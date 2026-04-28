package com.aikefu.service;

import com.aikefu.dto.DocumentUploadResponse;
import com.aikefu.dto.QueryRequest;
import com.aikefu.entity.RagChunk;
import com.aikefu.entity.RagDocument;
import com.aikefu.mapper.RagChunkMapper;
import com.aikefu.mapper.RagDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.lang3.StringUtils;

import org.apache.tika.Tika;

import javax.print.Doc;
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
    private static final int CHUNK_SIZE = 800;            // 目标 chunk 字符数（约 400-500 tokens）
    private static final int CHUNK_OVERLAP = 200;         // 相邻 chunk 重叠字符数，保持上下文连贯
    private static final int MIN_CHUNK_SIZE = 100;        // 最小 chunk 大小，避免过度碎片化
    private static final float SIMILARITY_THRESHOLD = 0.5f;
    private static final int EMBEDDING_DIMENSION = 1024;
    
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
            
            List<String> chunks = chunkText(content);
            int chunkCount = 0;
            
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = generateEmbedding(chunkText);
                
                RagChunk chunk = new RagChunk();
                chunk.setChunkId(UUID.randomUUID().toString());
                chunk.setDocumentId(documentId);
                chunk.setContent(chunkText);
                chunk.setChunkIndex(i);
                chunk.setEmbedding(embedding);
                chunk.setMetadata(Map.of(
                    "fileName", fileName,
                    "chunkSize", chunkText.length()
                ));
                chunkMapper.insert(chunk);
                chunkCount++;
            }
            
            document.setChunkCount(chunkCount);
            document.setStatus("completed");
            documentMapper.updateById(document);
            
            return new DocumentUploadResponse(documentId, fileName, "completed", chunkCount, "Document uploaded and processed successfully");
            
        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            document.setStatus("failed");
            documentMapper.updateById(document);
            
            // 尝试删除已上传的文件
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
    
    public List<Map<String, Object>> queryRag(QueryRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        double threshold = request.getThreshold() != null ? request.getThreshold() : SIMILARITY_THRESHOLD;
        
        log.info("RAG Query: query={}, topK={}, threshold={}", request.getQuery(), topK, threshold);
        
        float[] queryEmbedding = generateEmbedding(request.getQuery());
        List<Map<String, Object>> rows = chunkMapper.searchByVectorWithThreshold(queryEmbedding, topK, threshold);

        log.info("RAG Query Results: found {} chunks", rows.size());
        
        return rows.stream().map(row -> {
            Map<String, Object> result = new HashMap<>();
            result.put("chunkId", row.get("chunk_id"));
            result.put("documentId", row.get("document_id"));
            result.put("content", row.get("content"));
            result.put("chunkIndex", row.get("chunk_index"));
            result.put("metadata", row.get("metadata"));
            result.put("similarity", row.get("similarity"));
            return result;
        }).collect(Collectors.toList());
    }
    
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
            
            // 逻辑删除相关的 chunks
            int chunkResult = chunkMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RagChunk>()
                    .eq("document_id", documentId)
                    .set("deleted", 1)
            );
            log.info("Deleted document {} and {} associated chunks", documentId, chunkResult);
            
            // 删除物理文件
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
        
        // 只统计未删除的记录
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
            return new float[EMBEDDING_DIMENSION];
        } catch (Exception e) {
            log.error("Error generating embedding: {}", e.getMessage(), e);
            return new float[EMBEDDING_DIMENSION];
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
                // 保留标题文本，去除其他标签
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

        // 保留换行符作为段落分隔，只压缩连续空格
        content = content.replaceAll("[ \t]+", " ");
        content = content.replaceAll("\n{3,}", "\n\n");
        content = StringUtils.normalizeSpace(content);

        return content.trim();
    }

    /**
     * 优化的文档分块策略：
     * 1. 按段落/换行优先切割，保持语义完整性
     * 2. 支持中英文断句（。！？；\n 等）
     * 3. 相邻 chunk 保留重叠（overlap），避免边界信息丢失
     * 4. Markdown 标题作为分块优先边界
     * 5. 中文长句无空格时按固定长度切割
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }

        // Step 1: 按段落/标题拆分成语义单元
        List<String> segments = splitIntoSegments(text);

        // Step 2: 将语义单元合并为 chunk，带重叠
        StringBuilder currentChunk = new StringBuilder();
        String overlapBuffer = "";

        for (String segment : segments) {
            // 如果单个 segment 超过 CHUNK_SIZE，需要进一步切分
            if (segment.length() > CHUNK_SIZE) {
                // 先保存当前累积的 chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    // 记录最后 overlap 文本
                    overlapBuffer = getOverlapText(currentChunk.toString(), CHUNK_OVERLAP);
                    currentChunk = new StringBuilder(overlapBuffer);
                }
                // 按句子切分长段落
                List<String> subChunks = splitLongSegment(segment);
                for (String sub : subChunks) {
                    if (currentChunk.length() + sub.length() > CHUNK_SIZE && currentChunk.length() > overlapBuffer.length()) {
                        chunks.add(currentChunk.toString().trim());
                        overlapBuffer = getOverlapText(currentChunk.toString(), CHUNK_OVERLAP);
                        currentChunk = new StringBuilder(overlapBuffer);
                    }
                    currentChunk.append(sub).append("\n");
                }
            } else {
                // 如果加入这个 segment 不会超过 CHUNK_SIZE
                if (currentChunk.length() + segment.length() + 1 <= CHUNK_SIZE) {
                    currentChunk.append(segment).append("\n");
                } else {
                    // 保存当前 chunk
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        overlapBuffer = getOverlapText(currentChunk.toString(), CHUNK_OVERLAP);
                        currentChunk = new StringBuilder(overlapBuffer);
                    }
                    currentChunk.append(segment).append("\n");
                }
            }
        }

        if (currentChunk.length() > 0) {
            String last = currentChunk.toString().trim();
            if (last.length() >= MIN_CHUNK_SIZE || chunks.isEmpty()) {
                chunks.add(last);
            } else {
                // 太短的尾部合并到上一个 chunk
                if (!chunks.isEmpty()) {
                    chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + "\n" + last);
                }
            }
        }

        return chunks;
    }

    /**
     * 按段落和 Markdown 标题拆分为语义单元
     */
    private List<String> splitIntoSegments(String text) {
        List<String> segments = new ArrayList<>();
        // 按双换行（段落）或 Markdown 标题行分割
        String[] lines = text.split("\n");
        StringBuilder currentSegment = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            // Markdown 标题行作为新 segment 的起点
            if (trimmed.matches("^#{1,6}\\s+.+") && currentSegment.length() > 0) {
                segments.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder();
            }
            // 空行表示段落结束
            if (trimmed.isEmpty()) {
                if (currentSegment.length() > 0) {
                    segments.add(currentSegment.toString().trim());
                    currentSegment = new StringBuilder();
                }
            } else {
                currentSegment.append(trimmed).append(" ");
            }
        }

        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString().trim());
        }

        return segments;
    }

    /**
     * 对超长段落按句子切分，支持中英文
     */
    private List<String> splitLongSegment(String segment) {
        List<String> result = new ArrayList<>();
        // 中英文句号、感叹号、问号、分号、冒号
        String[] sentences = segment.split("(?<=[。！？；：.!?;:])\\s*");

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() + sentence.length() <= CHUNK_SIZE) {
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                // 中文长句没有空格，按固定长度硬切
                if (sentence.length() > CHUNK_SIZE) {
                    for (int i = 0; i < sentence.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                        int end = Math.min(i + CHUNK_SIZE, sentence.length());
                        result.add(sentence.substring(i, end));
                    }
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
     * 获取文本末尾的重叠部分，尝试在句子边界截断
     */
    private String getOverlapText(String text, int overlapSize) {
        if (text.length() <= overlapSize) {
            return text;
        }
        String tail = text.substring(text.length() - overlapSize);
        // 尝试从第一个句子边界开始，避免截断句子
        int sentenceStart = tail.indexOf('。');
        if (sentenceStart < 0) sentenceStart = tail.indexOf('！');
        if (sentenceStart < 0) sentenceStart = tail.indexOf('？');
        if (sentenceStart < 0) sentenceStart = tail.indexOf(". ");
        if (sentenceStart < 0) sentenceStart = tail.indexOf('\n');
        if (sentenceStart >= 0 && sentenceStart < tail.length() - 10) {
            return tail.substring(sentenceStart + 1).trim();
        }
        return tail;
    }
    

    private String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
