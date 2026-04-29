package com.aikefu.service;

import com.aikefu.dto.DocumentUploadResponse;
import com.aikefu.dto.QueryRequest;
import com.aikefu.entity.RagChunk;
import com.aikefu.entity.RagDocument;
import com.aikefu.mapper.RagChunkMapper;
import com.aikefu.mapper.RagDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.Tika;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
    
    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final EmbeddingModel embeddingModel;
    
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";
    
    /** jtokkit 编码器（用于 token 计数，近似 cl100k_base） */
    private final Encoding tokenEncoder = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    
    /** jieba 分词器（延迟初始化，词典加载较慢） */
    private volatile JiebaSegmenter jiebaSegmenter;
    
    // ======================== 配置项 ========================
    
    @org.springframework.beans.factory.annotation.Value("${rag.chunk-mode:semantic}")
    private String chunkMode;
    
    @org.springframework.beans.factory.annotation.Value("${rag.chunk-size:512}")
    private int chunkSize;
    
    @org.springframework.beans.factory.annotation.Value("${rag.chunk-overlap:64}")
    private int chunkOverlap;
    
    @org.springframework.beans.factory.annotation.Value("${rag.min-chunk-size:50}")
    private int minChunkSize;
    
    @org.springframework.beans.factory.annotation.Value("${rag.similarity-threshold:0.5}")
    private float similarityThreshold;
    
    @org.springframework.beans.factory.annotation.Value("${rag.embedding-dimension:1024}")
    private int embeddingDimension;
    
    @org.springframework.beans.factory.annotation.Value("${rag.semantic.similarity-threshold:0.5}")
    private float semanticSimilarityThreshold;
    
    @org.springframework.beans.factory.annotation.Value("${rag.semantic.sentence-window:1}")
    private int semanticSentenceWindow;
    
    @org.springframework.beans.factory.annotation.Value("${rag.parent-child.enabled:true}")
    private boolean parentChildEnabled;
    
    @org.springframework.beans.factory.annotation.Value("${rag.parent-child.parent-max-heading-level:2}")
    private int parentMaxHeadingLevel;
    
    @org.springframework.beans.factory.annotation.Value("${rag.rerank.candidate-multiplier:3}")
    private int rerankCandidateMultiplier;
    
    @org.springframework.beans.factory.annotation.Value("${rag.rerank.keyword-weight:0.2}")
    private float rerankKeywordWeight;
    
    @org.springframework.beans.factory.annotation.Value("${rag.rerank.cross-encoder.enabled:true}")
    private boolean crossEncoderEnabled;
    
    @org.springframework.beans.factory.annotation.Value("${rag.rerank.cross-encoder.model:gte-rerank}")
    private String crossEncoderModel;
    
    @org.springframework.beans.factory.annotation.Value("${spring.ai.dashscope.api-key}")
    private String dashscopeApiKey;
    
    // ======================== 内部类 ========================
    
    /** 切分过程中的元数据追踪对象 */
    private static class ChunkMeta {
        String heading = "";
        int headingLevel = 0;
        String documentTitle = "";
        boolean isParentChunk = false;
        
        Map<String, Object> toMap(String fileName, int tokenCount) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fileName", fileName);
            map.put("tokenCount", tokenCount);
            if (!heading.isEmpty()) {
                map.put("heading", heading);
            }
            if (headingLevel > 0) {
                map.put("headingLevel", headingLevel);
            }
            if (!documentTitle.isEmpty()) {
                map.put("documentTitle", documentTitle);
            }
            if (isParentChunk) {
                map.put("chunkType", "parent");
            }
            return map;
        }
    }
    
    /** 带元数据的文本块（内部使用，包含预分配 ID 和父级引用） */
    private static class TextChunk {
        String text;
        ChunkMeta meta;
        String chunkId;          // 预分配的 chunk ID
        String parentChunkId;    // 父级 chunk 的 ID（仅子 chunk 有）
        
        TextChunk(String text, ChunkMeta meta) {
            this.text = text;
            this.meta = meta;
            this.chunkId = UUID.randomUUID().toString();
        }
    }
    
    // ======================== Token 计数 ========================
    
    /** 计算文本的 token 数量（使用 jtokkit，近似 cl100k_base 编码） */
    private int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return tokenEncoder.countTokens(text);
    }
    
    // ======================== jieba 分词（延迟初始化） ========================
    
    private JiebaSegmenter getJiebaSegmenter() {
        if (jiebaSegmenter == null) {
            synchronized (this) {
                if (jiebaSegmenter == null) {
                    log.info("初始化 jieba 分词器...");
                    jiebaSegmenter = new JiebaSegmenter();
                    log.info("jieba 分词器初始化完成");
                }
            }
        }
        return jiebaSegmenter;
    }
    
    // ======================== 文档上传 ========================
    
    public DocumentUploadResponse uploadDocument(MultipartFile file) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                log.error("Failed to create upload directory: {}", UPLOAD_DIR);
                throw new IOException("Failed to create upload directory");
            }
        }
        
        String filePath = UPLOAD_DIR + documentId + "_" + fileName;
        file.transferTo(new File(filePath));
        
        RagDocument document = new RagDocument();
        document.setDocumentId(documentId);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setFilePath(filePath);
        document.setStatus("processing");
        documentMapper.insert(document);
        
        try {
            String content = extractText(filePath, fileType);
            document.setContentText(content);
            
            // 分块：根据配置选择 fixed 或 semantic
            List<TextChunk> chunks;
            if ("semantic".equals(chunkMode)) {
                chunks = chunkTextSemantic(content, fileName);
            } else {
                chunks = chunkTextFixed(content, fileName);
            }
            
            // 如果启用 Parent-Child，对 chunks 进行父子分层
            if (parentChildEnabled) {
                chunks = applyParentChildStrategy(chunks, fileName);
            }
            
            // 存储所有 chunks
            int chunkCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk tc = chunks.get(i);
                float[] embedding = generateEmbedding(tc.text);
                
                RagChunk chunk = new RagChunk();
                chunk.setChunkId(tc.chunkId);
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setContent(tc.text);
                chunk.setEmbedding(embedding);
                chunk.setParentChunkId(tc.parentChunkId);
                chunk.setMetadata(tc.meta.toMap(fileName, countTokens(tc.text)));
                chunkMapper.insert(chunk);
                chunkCount++;
            }
            
            document.setChunkCount(chunkCount);
            document.setStatus("completed");
            documentMapper.updateById(document);
            
            log.info("Document processed: id={}, chunks={}, mode={}, parent-child={}", 
                     documentId, chunkCount, chunkMode, parentChildEnabled);
            
            return new DocumentUploadResponse(documentId, fileName, "completed", chunkCount, "Document uploaded and processed successfully");
            
        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            document.setStatus("failed");
            documentMapper.updateById(document);
            
            try {
                File uploadedFile = new File(filePath);
                if (uploadedFile.exists()) {
                    uploadedFile.delete();
                }
            } catch (Exception ex) {
                log.warn("Failed to delete uploaded file: {}", filePath);
            }
            
            return new DocumentUploadResponse(documentId, fileName, "failed", 0, "Error: " + e.getMessage());
        }
    }
    
    // ======================== 查询检索 ========================
    
    public List<Map<String, Object>> queryRag(QueryRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        double threshold = request.getThreshold() != null ? request.getThreshold() : similarityThreshold;
        
        log.info("RAG Query: query={}, topK={}, threshold={}, crossEncoder={}", 
                 request.getQuery(), topK, threshold, crossEncoderEnabled);
        
        float[] queryEmbedding = generateEmbedding(request.getQuery());
        
        // 多召回候选，用于后续重排
        int candidateK = topK * rerankCandidateMultiplier;
        List<Map<String, Object>> candidates = chunkMapper.searchByVectorWithThreshold(
            queryEmbedding, candidateK, Math.max(threshold * 0.8, 0.3)
        );
        
        // 重排：优先使用 Cross-encoder，失败时回退到关键词+向量混合
        List<Map<String, Object>> reranked;
        if (crossEncoderEnabled) {
            reranked = rerankByCrossEncoder(request.getQuery(), candidates);
            if (reranked == null) {
                log.warn("Cross-encoder rerank failed, falling back to keyword+vector");
                reranked = rerankByKeywordAndVector(request.getQuery(), candidates);
            }
        } else {
            reranked = rerankByKeywordAndVector(request.getQuery(), candidates);
        }
        
        // 截取 topK
        List<Map<String, Object>> finalResults = reranked.stream().limit(topK).collect(Collectors.toList());
        
        // Parent-Child 上下文扩展
        if (parentChildEnabled) {
            finalResults = expandWithParentContext(finalResults);
        }
        
        log.info("RAG Query Results: candidates={}, after rerank={}", candidates.size(), finalResults.size());
        
        return finalResults.stream().map(row -> {
            Map<String, Object> result = new HashMap<>();
            result.put("chunkId", row.get("chunk_id"));
            result.put("documentId", row.get("document_id"));
            result.put("content", row.get("content"));
            result.put("chunkIndex", row.get("chunk_index"));
            result.put("metadata", row.get("metadata"));
            result.put("similarity", row.get("finalScore") != null ? row.get("finalScore") : row.get("similarity"));
            if (row.get("parentContext") != null) {
                result.put("parentContext", row.get("parentContext"));
            }
            return result;
        }).collect(Collectors.toList());
    }
    
    // ======================== P1: Cross-encoder 重排 ========================
    
    /**
     * 使用 DashScope Cross-encoder API 进行重排
     * 返回 null 表示调用失败，需要回退到其他重排方式
     */
    private List<Map<String, Object>> rerankByCrossEncoder(String query, List<Map<String, Object>> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        
        try {
            // 提取候选文档内容
            List<String> documents = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                documents.add((String) candidate.getOrDefault("content", ""));
            }
            
            // 构造 DashScope rerank 请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", crossEncoderModel);
            
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", documents);
            requestBody.put("input", input);
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("top_n", candidates.size());
            parameters.put("return_documents", false);
            requestBody.put("parameters", parameters);
            
            RestTemplate restTemplate = new RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dashscopeApiKey);
            
            org.springframework.http.HttpEntity<Map<String, Object>> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank",
                entity,
                Map.class
            );
            
            if (response == null) {
                log.warn("DashScope rerank API returned null");
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output == null) {
                log.warn("DashScope rerank API response missing 'output' field");
                return null;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
            if (results == null || results.isEmpty()) {
                log.warn("DashScope rerank API returned empty results");
                return null;
            }
            
            // 按 rerank 结果重新排序 candidates
            Map<Integer, Map<String, Object>> indexedCandidates = new HashMap<>();
            for (int i = 0; i < candidates.size(); i++) {
                indexedCandidates.put(i, candidates.get(i));
            }
            
            List<Map<String, Object>> reranked = new ArrayList<>();
            for (Map<String, Object> rerankResult : results) {
                int index = ((Number) rerankResult.get("index")).intValue();
                double relevanceScore = ((Number) rerankResult.get("relevance_score")).doubleValue();
                Map<String, Object> candidate = indexedCandidates.get(index);
                if (candidate != null) {
                    candidate.put("finalScore", relevanceScore);
                    candidate.put("rerankMethod", "cross-encoder");
                    reranked.add(candidate);
                }
            }
            
            log.info("Cross-encoder rerank completed: {} results", reranked.size());
            return reranked;
            
        } catch (Exception e) {
            log.error("DashScope rerank API call failed: {}", e.getMessage());
            return null;
        }
    }
    
    // ======================== 关键词+向量混合重排（回退方案） ========================
    
    /**
     * 关键词匹配 + 向量相似度混合重排
     * 使用 jieba 中文分词提升关键词提取精度
     */
    private List<Map<String, Object>> rerankByKeywordAndVector(String query, List<Map<String, Object>> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        
        Set<String> queryKeywords = extractKeywordsJieba(query);
        
        for (Map<String, Object> candidate : candidates) {
            double vectorScore = ((Number) candidate.getOrDefault("similarity", 0.0)).doubleValue();
            String content = (String) candidate.getOrDefault("content", "");
            
            double keywordScore = calculateKeywordScore(queryKeywords, content);
            
            double finalScore = (1.0 - rerankKeywordWeight) * vectorScore + rerankKeywordWeight * keywordScore;
            candidate.put("finalScore", finalScore);
            candidate.put("keywordScore", keywordScore);
            candidate.put("rerankMethod", "keyword+vector");
        }
        
        candidates.sort((a, b) -> {
            double scoreA = ((Number) a.get("finalScore")).doubleValue();
            double scoreB = ((Number) b.get("finalScore")).doubleValue();
            return Double.compare(scoreB, scoreA);
        });
        
        return candidates;
    }
    
    // ======================== P2: jieba 关键词提取 ========================
    
    /**
     * 使用 jieba 分词提取关键词
     * 中文用 jieba SEARCH 模式（对长词再次切分，适合搜索场景）
     * 英文按空格分词
     */
    private Set<String> extractKeywordsJieba(String text) {
        Set<String> keywords = new HashSet<>();
        
        Set<String> stopWords = Set.of(
            "的", "了", "是", "在", "和", "有", "不", "这", "个", "也",
            "着", "又", "之", "与", "而", "且", "其", "或", "那", "被", "从", "把", "对", "让", "给",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "need", "dare", "ought",
            "used", "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "as", "into", "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further", "then",
            "once", "and", "but", "or", "nor", "not", "so", "yet", "both", "either",
            "neither", "each", "every", "all", "any", "few", "more", "most", "other",
            "some", "such", "no", "only", "own", "same", "than", "too", "very",
            "just", "because", "if", "when", "where", "how", "what", "which", "who");
        
        try {
            List<SegToken> tokens = getJiebaSegmenter().process(text, JiebaSegmenter.SegMode.SEARCH);
            for (SegToken token : tokens) {
                String word = token.word.trim().toLowerCase();
                if (word.length() >= 2 && !stopWords.contains(word)) {
                    // 过滤纯标点和纯数字
                    if (word.matches(".*[\\u4e00-\\u9fffa-zA-Z].*")) {
                        keywords.add(word);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("jieba 分词失败，回退到简单分词: {}", e.getMessage());
            // 回退到简单分词
            String[] words = text.toLowerCase().split("[\\s,，.。！？!?;；:：、]+");
            for (String word : words) {
                if (word.length() >= 2 && !stopWords.contains(word)) {
                    keywords.add(word);
                }
            }
            // 中文 n-gram 回退
            java.util.regex.Pattern cnPattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{2,4}");
            java.util.regex.Matcher matcher = cnPattern.matcher(text);
            while (matcher.find()) {
                String group = matcher.group();
                if (!stopWords.contains(group)) {
                    keywords.add(group);
                }
            }
        }
        
        return keywords;
    }
    
    /**
     * 计算关键词匹配得分：命中的关键词比例
     */
    private double calculateKeywordScore(Set<String> queryKeywords, String content) {
        if (queryKeywords.isEmpty() || content == null || content.isEmpty()) {
            return 0.0;
        }
        String lowerContent = content.toLowerCase();
        int matched = 0;
        for (String keyword : queryKeywords) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                matched++;
            }
        }
        return (double) matched / queryKeywords.size();
    }
    
    // ======================== P2: Parent-Child 上下文扩展 ========================
    
    /**
     * 为子 chunk 扩展父级上下文
     * 检索命中子 chunk 时，自动附带父级 chunk 的完整内容作为上下文
     */
    private List<Map<String, Object>> expandWithParentContext(List<Map<String, Object>> results) {
        for (Map<String, Object> result : results) {
            Object parentChunkIdObj = result.get("parent_chunk_id");
            if (parentChunkIdObj != null && !parentChunkIdObj.toString().isEmpty()) {
                String parentChunkId = parentChunkIdObj.toString();
                try {
                    Map<String, Object> parentChunk = chunkMapper.findByChunkId(parentChunkId);
                    if (parentChunk != null) {
                        String parentContent = (String) parentChunk.get("content");
                        if (parentContent != null && !parentContent.equals(result.get("content"))) {
                            result.put("parentContext", parentContent);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch parent chunk {}: {}", parentChunkId, e.getMessage());
                }
            }
        }
        return results;
    }
    
    // ======================== P1: 固定大小分块（Token 级别） ========================
    
    /**
     * 固定大小分块，基于 Token 计数
     * 替代之前的字符计数，保证中英文混排时 chunk 大小更均匀
     */
    private List<TextChunk> chunkTextFixed(String text, String fileName) {
        List<TextChunk> result = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return result;
        }
        
        if (countTokens(text) <= chunkSize) {
            ChunkMeta meta = new ChunkMeta();
            meta.documentTitle = extractDocumentTitle(fileName);
            result.add(new TextChunk(text, meta));
            return result;
        }
        
        // Step 1: 按段落/标题拆分成语义单元
        List<Map.Entry<String, ChunkMeta>> segments = splitIntoSegmentsWithMeta(text, fileName);
        
        // Step 2: 将语义单元合并为 chunk（基于 token 计数）
        StringBuilder currentChunk = new StringBuilder();
        String overlapBuffer = "";
        ChunkMeta currentMeta = new ChunkMeta();
        currentMeta.documentTitle = extractDocumentTitle(fileName);
        
        for (Map.Entry<String, ChunkMeta> segEntry : segments) {
            String segment = segEntry.getKey();
            ChunkMeta segMeta = segEntry.getValue();
            
            // 更新当前 chunk 的标题上下文
            if (segMeta.headingLevel > 0) {
                currentMeta.heading = segMeta.heading;
                currentMeta.headingLevel = segMeta.headingLevel;
            }
            
            int segmentTokens = countTokens(segment);
            int currentTokens = countTokens(currentChunk.toString());
            
            if (segmentTokens > chunkSize) {
                // 超长段落需要进一步切分
                if (currentChunk.length() > 0) {
                    result.add(new TextChunk(currentChunk.toString().trim(), copyMeta(currentMeta)));
                    overlapBuffer = getOverlapTextByToken(currentChunk.toString(), chunkOverlap);
                    currentChunk = new StringBuilder(overlapBuffer);
                }
                List<String> subChunks = splitLongSegment(segment);
                for (String sub : subChunks) {
                    int subTokens = countTokens(sub);
                    int newTokens = countTokens(currentChunk.toString()) + subTokens;
                    if (newTokens > chunkSize && countTokens(currentChunk.toString()) > countTokens(overlapBuffer)) {
                        result.add(new TextChunk(currentChunk.toString().trim(), copyMeta(currentMeta)));
                        overlapBuffer = getOverlapTextByToken(currentChunk.toString(), chunkOverlap);
                        currentChunk = new StringBuilder(overlapBuffer);
                    }
                    currentChunk.append(sub).append("\n");
                }
            } else {
                int newTokens = currentTokens + segmentTokens;
                if (newTokens <= chunkSize) {
                    currentChunk.append(segment).append("\n");
                } else {
                    if (currentChunk.length() > 0) {
                        result.add(new TextChunk(currentChunk.toString().trim(), copyMeta(currentMeta)));
                        overlapBuffer = getOverlapTextByToken(currentChunk.toString(), chunkOverlap);
                        currentChunk = new StringBuilder(overlapBuffer);
                    }
                    currentChunk.append(segment).append("\n");
                }
            }
        }
        
        if (currentChunk.length() > 0) {
            String last = currentChunk.toString().trim();
            if (countTokens(last) >= minChunkSize || result.isEmpty()) {
                result.add(new TextChunk(last, copyMeta(currentMeta)));
            } else {
                if (!result.isEmpty()) {
                    TextChunk prev = result.get(result.size() - 1);
                    result.set(result.size() - 1, new TextChunk(prev.text + "\n" + last, prev.meta));
                }
            }
        }
        
        return result;
    }
    
    // ======================== P2: 语义分块 ========================
    
    /**
     * 语义分块：基于 embedding 相似度检测语义边界
     * 
     * 核心思路：
     * 1. 先按标题拆分成大段（标题处必然切分）
     * 2. 在每个标题段内部，将文本拆分为句子
     * 3. 批量计算句子的 embedding
     * 4. 计算相邻句子的余弦相似度
     * 5. 在相似度骤降处（低于阈值）切分
     * 6. 保证同一 chunk 内语义连贯
     */
    private List<TextChunk> chunkTextSemantic(String text, String fileName) {
        List<TextChunk> result = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return result;
        }
        
        if (countTokens(text) <= chunkSize) {
            ChunkMeta meta = new ChunkMeta();
            meta.documentTitle = extractDocumentTitle(fileName);
            result.add(new TextChunk(text, meta));
            return result;
        }
        
        // Step 1: 按标题拆分成大段
        List<Map.Entry<String, ChunkMeta>> headingSegments = splitByHeading(text, fileName);
        
        // Step 2: 对每个标题段内部进行语义分块
        for (Map.Entry<String, ChunkMeta> headingEntry : headingSegments) {
            String sectionText = headingEntry.getKey();
            ChunkMeta sectionMeta = headingEntry.getValue();
            
            if (countTokens(sectionText) <= chunkSize) {
                result.add(new TextChunk(sectionText, sectionMeta));
                continue;
            }
            
            // 将段落拆分为句子
            List<String> sentences = splitIntoSentences(sectionText);
            if (sentences.size() <= 1) {
                result.add(new TextChunk(sectionText, sectionMeta));
                continue;
            }
            
            // 批量计算句子 embedding
            List<float[]> sentenceEmbeddings = batchGenerateEmbeddings(sentences);
            
            if (sentenceEmbeddings == null || sentenceEmbeddings.size() != sentences.size()) {
                // embedding 计算失败，回退到固定大小分块
                log.warn("Semantic chunking embedding failed, falling back to fixed chunking for section");
                result.addAll(chunkTextFixed(sectionText, fileName));
                continue;
            }
            
            // 计算相邻句子的余弦相似度
            List<Double> similarities = new ArrayList<>();
            for (int i = 0; i < sentenceEmbeddings.size() - 1; i++) {
                double sim = cosineSimilarity(sentenceEmbeddings.get(i), sentenceEmbeddings.get(i + 1));
                similarities.add(sim);
            }
            
            // 根据相似度确定切分点：相似度低于阈值处切分
            List<Integer> splitPoints = new ArrayList<>();
            for (int i = 0; i < similarities.size(); i++) {
                if (similarities.get(i) < semanticSimilarityThreshold) {
                    splitPoints.add(i + 1);  // 在 i 和 i+1 之间切分
                }
            }
            
            // 按切分点合并句子为 chunk，同时遵守 token 上限
            int start = 0;
            ChunkMeta currentMeta = copyMeta(sectionMeta);
            
            for (int sp : splitPoints) {
                if (sp <= start) continue;
                
                StringBuilder chunkBuilder = new StringBuilder();
                for (int i = start; i < sp; i++) {
                    chunkBuilder.append(sentences.get(i));
                }
                
                String chunkText = chunkBuilder.toString().trim();
                
                if (countTokens(chunkText) > chunkSize) {
                    // chunk 超过 token 上限，进一步切分
                    result.addAll(chunkTextFixed(chunkText, fileName));
                } else if (countTokens(chunkText) >= minChunkSize) {
                    result.add(new TextChunk(chunkText, currentMeta));
                }
                
                start = sp;
            }
            
            // 处理最后一个 chunk
            if (start < sentences.size()) {
                StringBuilder lastBuilder = new StringBuilder();
                for (int i = start; i < sentences.size(); i++) {
                    lastBuilder.append(sentences.get(i));
                }
                String lastChunk = lastBuilder.toString().trim();
                
                if (countTokens(lastChunk) > chunkSize) {
                    result.addAll(chunkTextFixed(lastChunk, fileName));
                } else if (result.isEmpty() || countTokens(lastChunk) >= minChunkSize) {
                    result.add(new TextChunk(lastChunk, currentMeta));
                } else {
                    TextChunk prev = result.get(result.size() - 1);
                    result.set(result.size() - 1, new TextChunk(prev.text + "\n" + lastChunk, prev.meta));
                }
            }
        }
        
        log.info("Semantic chunking completed: {} chunks from {} heading sections", 
                 result.size(), headingSegments.size());
        return result;
    }
    
    /**
     * 按标题级别拆分文本（标题处必然切分）
     */
    private List<Map.Entry<String, ChunkMeta>> splitByHeading(String text, String fileName) {
        List<Map.Entry<String, ChunkMeta>> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentSection = new StringBuilder();
        ChunkMeta currentMeta = new ChunkMeta();
        currentMeta.documentTitle = extractDocumentTitle(fileName);
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                if (currentSection.length() > 0) {
                    sections.add(Map.entry(currentSection.toString().trim(), copyMeta(currentMeta)));
                    currentSection = new StringBuilder();
                }
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                currentMeta.headingLevel = level;
                currentMeta.heading = trimmed.replaceAll("^#{1,6}\\s+", "").trim();
                currentSection.append(trimmed).append("\n");
            } else {
                currentSection.append(trimmed).append("\n");
            }
        }
        
        if (currentSection.length() > 0) {
            sections.add(Map.entry(currentSection.toString().trim(), copyMeta(currentMeta)));
        }
        
        return sections;
    }
    
    /**
     * 将文本拆分为句子列表（支持中英文）
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 按中英文断句标点切分，保留标点
        String[] parts = text.split("(?<=[。！？；.!?;])\\s*");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }
    
    /**
     * 批量生成 embedding（DashScope 一次最多 25 条）
     */
    private List<float[]> batchGenerateEmbeddings(List<String> texts) {
        try {
            List<float[]> allEmbeddings = new ArrayList<>();
            int batchSize = 25;
            
            for (int i = 0; i < texts.size(); i += batchSize) {
                List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
                EmbeddingResponse response = embeddingModel.embedForResponse(batch);
                
                if (response != null && response.getResults() != null) {
                    for (int j = 0; j < batch.size(); j++) {
                        if (j < response.getResults().size()) {
                            float[] embedding = response.getResults().get(j).getOutput();
                            allEmbeddings.add(embedding != null ? embedding : new float[embeddingDimension]);
                        } else {
                            allEmbeddings.add(new float[embeddingDimension]);
                        }
                    }
                } else {
                    for (int j = 0; j < batch.size(); j++) {
                        allEmbeddings.add(new float[embeddingDimension]);
                    }
                }
            }
            
            return allEmbeddings;
        } catch (Exception e) {
            log.error("Batch embedding generation failed: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    // ======================== P2: Parent-Child 分块策略 ========================
    
    /**
     * 对已有 chunks 应用 Parent-Child 策略
     * 
     * 将标题级别 <= parentMaxHeadingLevel 的内容作为父级 chunk，
     * 其下的内容作为子级 chunk，通过 parentChunkId 关联。
     * 
     * 检索时命中子 chunk，可自动扩展父级上下文以提供更完整的信息。
     */
    private List<TextChunk> applyParentChildStrategy(List<TextChunk> chunks, String fileName) {
        List<TextChunk> result = new ArrayList<>();
        
        // 识别父级 chunk 的范围
        List<int[]> parentRanges = new ArrayList<>();
        int currentParentStart = -1;
        
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            int headingLevel = chunk.meta.headingLevel;
            
            if (headingLevel > 0 && headingLevel <= parentMaxHeadingLevel) {
                if (currentParentStart >= 0) {
                    parentRanges.add(new int[]{currentParentStart, i});
                }
                currentParentStart = i;
            }
        }
        if (currentParentStart >= 0) {
            parentRanges.add(new int[]{currentParentStart, chunks.size()});
        }
        
        // 没有合适的标题层级，不做 Parent-Child 处理
        if (parentRanges.isEmpty()) {
            log.info("No suitable heading levels for parent-child strategy, skipping");
            return chunks;
        }
        
        // 处理第一个父级范围之前的 chunks（文档开头无标题部分）
        if (parentRanges.get(0)[0] > 0) {
            for (int i = 0; i < parentRanges.get(0)[0]; i++) {
                result.add(chunks.get(i));
            }
        }
        
        // 为每个父级范围创建父级 chunk + 子级 chunks
        for (int[] range : parentRanges) {
            int start = range[0];
            int end = range[1];
            
            // 合并父级范围内的所有文本作为父级 chunk
            StringBuilder parentContent = new StringBuilder();
            for (int i = start; i < end; i++) {
                parentContent.append(chunks.get(i).text).append("\n\n");
            }
            String parentText = parentContent.toString().trim();
            
            // DashScope text-embedding-v3 最大 8192 tokens
            if (countTokens(parentText) > 8192) {
                // 父级 chunk 太大，不做 Parent-Child，仅保留子级 chunks
                log.debug("Parent chunk too large ({} tokens), skipping parent-child for range [{}, {})", 
                          countTokens(parentText), start, end);
                for (int i = start; i < end; i++) {
                    result.add(chunks.get(i));
                }
                continue;
            }
            
            // 创建父级 chunk（预分配 chunkId，子 chunks 引用此 ID）
            String parentChunkId = chunks.get(start).chunkId;  // 复用第一个子 chunk 的 ID 作为父级 ID
            ChunkMeta parentMeta = copyMeta(chunks.get(start).meta);
            parentMeta.isParentChunk = true;
            TextChunk parentChunk = new TextChunk(parentText, parentMeta);
            parentChunk.chunkId = parentChunkId;  // 使用预分配的 ID
            parentChunk.parentChunkId = null;       // 父级无上级
            result.add(parentChunk);
            
            // 创建子级 chunks，关联到父级
            for (int i = start; i < end; i++) {
                TextChunk childChunk = new TextChunk(chunks.get(i).text, chunks.get(i).meta);
                childChunk.parentChunkId = parentChunkId;
                result.add(childChunk);
            }
        }
        
        log.info("Parent-Child strategy applied: {} total chunks (from {} original)", result.size(), chunks.size());
        return result;
    }
    
    // ======================== 通用辅助方法 ========================
    
    public List<RagDocument> getAllDocuments() {
        return documentMapper.selectList(new QueryWrapper<RagDocument>().eq("deleted", 0));
    }
    
    public RagDocument getDocumentById(String documentId) {
        return documentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagDocument>()
                .eq("document_id", documentId)
        );
    }
    
    public List<RagChunk> getChunksByDocumentId(String documentId) {
        return chunkMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagChunk>()
                .eq("document_id", documentId)
                .orderByAsc("chunk_index")
        );
    }
    
    public boolean deleteDocument(String documentId) {
        try {
            RagDocument document = getDocumentById(documentId);
            if (document == null) {
                log.warn("Document not found for deletion: {}", documentId);
                return false;
            }

            int docResult = documentMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RagDocument>()
                            .eq("document_id", documentId)
                            .set("deleted", 1));
            if (docResult <= 0) {
                log.error("Failed to delete document: {}", documentId);
                return false;
            }
            
            int chunkResult = chunkMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RagChunk>()
                    .eq("document_id", documentId)
                    .set("deleted", 1)
            );
            log.info("Deleted document {} and {} associated chunks", documentId, chunkResult);
            
            String filePath = document.getFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        log.info("Deleted physical file: {}", filePath);
                    } else {
                        log.warn("Failed to delete physical file: {}", filePath);
                    }
                } else {
                    log.warn("Physical file not found: {}", filePath);
                }
            } else {
                log.warn("Document has no file path: {}", documentId);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error deleting document {}: {}", documentId, e.getMessage(), e);
            return false;
        }
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDocuments", documentMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagDocument>()
                .eq("deleted", 0)
        ));
        stats.put("totalChunks", chunkMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagChunk>()
                .eq("deleted", 0)
        ));
        stats.put("completedDocuments", documentMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagDocument>()
                .eq("deleted", 0)
                .eq("status", "completed")
        ));
        stats.put("failedDocuments", documentMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagDocument>()
                .eq("deleted", 0)
                .eq("status", "failed")
        ));
        return stats;
    }
    
    private float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            if (embeddingResponse != null && !embeddingResponse.getResults().isEmpty()) {
                float[] embedding = embeddingResponse.getResult().getOutput();
                if (embedding != null && embedding.length > 0) {
                    return embedding;
                }
            }
            return new float[embeddingDimension];
        } catch (Exception e) {
            log.error("Error generating embedding: {}", e.getMessage(), e);
            return new float[embeddingDimension];
        }
    }
    
    private final Tika tika = new Tika();

    private String extractText(String filePath, String fileType) throws IOException {
        String content;

        switch (fileType.toLowerCase()) {
            case "txt":
            case "md":
                content = Files.readString(Path.of(filePath));
                break;
            case "html":
            case "htm":
                content = Files.readString(Path.of(filePath));
                content = content.replaceAll("<h[1-6][^>]*>", "\n## ");
                content = content.replaceAll("</h[1-6]>", "\n");
                content = content.replaceAll("<br\\s*/?>", "\n");
                content = content.replaceAll("<p[^>]*>", "\n");
                content = content.replaceAll("</p>", "\n");
                content = content.replaceAll("<[^>]+>", " ");
                break;
            default:
                try (FileInputStream fis = new FileInputStream(filePath)) {
                    content = tika.parseToString(fis);
                } catch (TikaException e) {
                    throw new RuntimeException(e);
                }
        }

        content = content.replaceAll("[ \t]+", " ");
        content = content.replaceAll("\n{3,}", "\n\n");
        String[] lines = content.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                cleaned.append(trimmedLine).append("\n");
            } else {
                cleaned.append("\n");
            }
        }

        return cleaned.toString().trim();
    }

    private ChunkMeta copyMeta(ChunkMeta source) {
        ChunkMeta copy = new ChunkMeta();
        copy.heading = source.heading;
        copy.headingLevel = source.headingLevel;
        copy.documentTitle = source.documentTitle;
        copy.isParentChunk = source.isParentChunk;
        return copy;
    }
    
    private String extractDocumentTitle(String fileName) {
        if (fileName == null) return "";
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
    }

    /**
     * 按段落和 Markdown 标题拆分为语义单元，同时追踪标题信息
     */
    private List<Map.Entry<String, ChunkMeta>> splitIntoSegmentsWithMeta(String text, String fileName) {
        List<Map.Entry<String, ChunkMeta>> segments = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentSegment = new StringBuilder();
        ChunkMeta currentMeta = new ChunkMeta();
        currentMeta.documentTitle = extractDocumentTitle(fileName);

        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                if (currentSegment.length() > 0) {
                    segments.add(Map.entry(currentSegment.toString().trim(), copyMeta(currentMeta)));
                    currentSegment = new StringBuilder();
                }
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                currentMeta.headingLevel = level;
                currentMeta.heading = trimmed.replaceAll("^#{1,6}\\s+", "").trim();
                currentSegment.append(trimmed).append(" ");
            } else if (trimmed.isEmpty()) {
                if (currentSegment.length() > 0) {
                    segments.add(Map.entry(currentSegment.toString().trim(), copyMeta(currentMeta)));
                    currentSegment = new StringBuilder();
                }
            } else {
                currentSegment.append(trimmed).append(" ");
            }
        }

        if (currentSegment.length() > 0) {
            segments.add(Map.entry(currentSegment.toString().trim(), copyMeta(currentMeta)));
        }

        return segments;
    }

    /**
     * 对超长段落按句子切分（基于 token 计数）
     */
    private List<String> splitLongSegment(String segment) {
        List<String> result = new ArrayList<>();
        String[] sentences = segment.split("(?<=[。！？；：.!?;:，、,\")）】}])\\s*");

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            int currentTokens = countTokens(current.toString());
            int sentenceTokens = countTokens(sentence);
            
            if (currentTokens + sentenceTokens <= chunkSize) {
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                if (sentenceTokens > chunkSize) {
                    // 超长句子按 token 切分
                    result.addAll(splitByTokens(sentence));
                } else {
                    current.append(sentence);
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }
    
    /**
     * 按 token 数切分长文本（逐句子边界累加直到达到 token 上限）
     */
    private List<String> splitByTokens(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokenCount = 0;
        
        String[] parts = text.split("(?<=[。！？；：.!?;:，、,])");
        
        for (String part : parts) {
            int partTokens = countTokens(part);
            if (currentTokenCount + partTokens <= chunkSize) {
                current.append(part);
                currentTokenCount += partTokens;
            } else {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    String overlap = getOverlapTextByToken(current.toString(), chunkOverlap);
                    current = new StringBuilder(overlap);
                    currentTokenCount = countTokens(overlap);
                }
                if (partTokens > chunkSize) {
                    // 极端情况：单个片段超长，按字符硬切
                    int step = chunkSize * 2;
                    for (int i = 0; i < part.length(); i += step) {
                        int end = Math.min(i + chunkSize * 3, part.length());
                        String sub = part.substring(i, end);
                        if (end < part.length()) {
                            int lastComma = findLastPunctuation(sub);
                            if (lastComma > sub.length() * 0.6) {
                                sub = sub.substring(0, lastComma + 1);
                                i -= (end - (i + sub.length()));
                            }
                        }
                        result.add(sub);
                    }
                } else {
                    current.append(part);
                    currentTokenCount += partTokens;
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }
    
    /**
     * 在文本末尾附近查找最近的标点断句点
     */
    private int findLastPunctuation(String text) {
        int lastIdx = -1;
        char[] punctuations = {'，', '、', ',', '；', ';', '：', ':', '。', '！', '？', '.', '!', '?'};
        for (char p : punctuations) {
            int idx = text.lastIndexOf(p);
            if (idx > lastIdx) {
                lastIdx = idx;
            }
        }
        return lastIdx;
    }

    /**
     * 基于 token 计数获取文本末尾的重叠部分
     */
    private String getOverlapTextByToken(String text, int overlapTokens) {
        if (text == null || text.isEmpty() || countTokens(text) <= overlapTokens) {
            return text;
        }
        
        // 从后往前逐字符添加，直到达到目标 token 数
        StringBuilder overlap = new StringBuilder();
        int tokenCount = 0;
        for (int i = text.length() - 1; i >= 0 && tokenCount < overlapTokens; i--) {
            overlap.insert(0, text.charAt(i));
            // 每添加几个字符后检查 token 数（优化性能）
            if (overlap.length() % 4 == 0 || i == 0) {
                tokenCount = countTokens(overlap.toString());
            }
        }
        
        // 尝试在句子边界截断
        String overlapStr = overlap.toString();
        char[] boundaries = {'。', '！', '？', '；', '：', '.', '!', '?', ';', ':', '\n', '，', ',', '、'};
        for (char boundary : boundaries) {
            int idx = overlapStr.indexOf(boundary);
            if (idx >= 0 && idx < overlapStr.length() - 10) {
                return overlapStr.substring(idx + 1).trim();
            }
        }
        return overlapStr;
    }

    private String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
