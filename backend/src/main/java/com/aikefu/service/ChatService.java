package com.aikefu.service;

import com.aikefu.dto.ChatRequest;
import com.aikefu.dto.QueryRequest;
import com.aikefu.entity.*;
import com.aikefu.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final DynamicModelProvider modelProvider;
    private final AiProviderService aiProviderService;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final String SYSTEM_PROMPT = """
        You are a helpful AI customer service assistant. 
        Use the provided context to answer user questions accurately.
        If you don't know something, say so honestly.
        """;

    private static final int SUMMARY_ROUND_INTERVAL = 10;

    private static final String SUMMARY_PROMPT = """
        请总结以下对话内容。要求：
        1. 生成一个简短的标题（不超过30个字符），概括对话主题
        2. 生成一段摘要（不超过200字），包含对话的关键信息、用户需求和已给出的回答要点
        
        对话内容：
        %s
        
        请严格按照以下JSON格式返回，不要包含markdown标记或其他内容：
        {"title":"简短标题","summary":"详细摘要"}
        """;
    
    public Map<String, Object> chat(ChatRequest request) {
        String userId = request.getUserId();
        String conversationId = request.getConversationId();
        String message = request.getMessage();
        
        if (userId == null || userId.isEmpty()) {
            userId = "user1";
        }
        
        User user = getOrCreateUser(userId);
        
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = createConversation(userId);
        }
        
        Conversation conversation = getConversation(conversationId);
        if (conversation == null) {
            conversationId = createConversation(userId);
            conversation = getConversation(conversationId);
        }
        
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuery(message);
        queryRequest.setTopK(5);
        List<Map<String, Object>> ragResults = ragService.queryRag(queryRequest);
        
        String context = buildContext(ragResults);
        
        List<ConversationMessage> history = messageMapper.getMessagesByConversationId(conversationId);

        String conversationSummary = conversation.getSummary();
        Integer lastSummarizedMessageCount = conversation.getLastSummarizedMessageCount();
        List<Message> messages = buildMessages(context, conversationSummary, lastSummarizedMessageCount, history, message);

        // 先保存用户消息
        saveMessage(conversationId, "user", message);

        String assistantResponse;
        try {
            ChatResponse chatResponse = modelProvider.getChatModel().call(new Prompt(messages));
            assistantResponse = chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Error calling chat API", e);
            assistantResponse = "抱歉，AI服务暂时不可用。错误: " + e.getMessage();
        }
        
        ConversationMessage assistantMessage = saveMessage(conversationId, "assistant", assistantResponse);
        
        updateConversationMessageCount(conversationId);

        // 记忆总结：第一轮和以后每10轮
        int round = getConversationRound(conversationId);
        if (round == 1 || (round > 1 && round % SUMMARY_ROUND_INTERVAL == 0)) {
            summarizeConversation(conversationId);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("messageId", assistantMessage.getMessageId());
        response.put("conversationId", conversationId);
        response.put("role", "assistant");
        response.put("content", assistantResponse);
        response.put("createdAt", assistantMessage.getCreatedAt());
        
        return response;
    }

    /**
     * 流式聊天 — 返回 Flux<ServerSentEvent>
     * Spring MVC 自动管理 SSE 流的 flush，每个元素立即推送到客户端
     */
    public Flux<ServerSentEvent<String>> chatStreamFlux(ChatRequest request) {
        String userId = request.getUserId();
        String convId = request.getConversationId();
        String message = request.getMessage();

        if (userId == null || userId.isEmpty()) {
            userId = "user1";
        }

        getOrCreateUser(userId);

        boolean isNewConversation = (convId == null || convId.isEmpty());
        if (isNewConversation) {
            convId = createConversation(userId);
        }

        Conversation conversation = getConversation(convId);
        if (conversation == null) {
            convId = createConversation(userId);
            isNewConversation = true;
        }

        final String conversationId = convId;
        final boolean newConversation = isNewConversation;
        final String conversationSummary = conversation.getSummary();
        final Integer lastSummarizedMessageCount = conversation.getLastSummarizedMessageCount();

        // 先保存用户消息
        saveMessage(conversationId, "user", message);

        // RAG 检索
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuery(message);
        queryRequest.setTopK(5);
        List<Map<String, Object>> ragResults = ragService.queryRag(queryRequest);

        String context = buildContext(ragResults);

        List<ConversationMessage> history = messageMapper.getMessagesByConversationId(conversationId);

        List<Message> messages = buildMessages(context, conversationSummary, lastSummarizedMessageCount, history, message);

        // 累积完整响应
        AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());

        // 构建事件流：meta → tokens → done
        Flux<ServerSentEvent<String>> metaFlux = Flux.just(
            ServerSentEvent.<String>builder()
                .event("meta")
                .data(toJson(Map.of("conversationId", conversationId, "isNew", newConversation)))
                .build()
        );

        Flux<ServerSentEvent<String>> tokenFlux = modelProvider.getChatModel().stream(new Prompt(messages))
            .flatMap(chatResponse -> {
                String token = chatResponse.getResult().getOutput().getText();
                if (token != null && !token.isEmpty()) {
                    fullResponseRef.get().append(token);
                    return Flux.just(ServerSentEvent.<String>builder()
                        .event("token")
                        .data(token)
                        .build());
                }
                return Flux.empty();
            });

        Flux<ServerSentEvent<String>> doneFlux = Flux.defer(() -> {
            String responseText = fullResponseRef.get().toString();
            if (responseText.isEmpty()) {
                responseText = "（AI 未返回内容）";
            }
            ConversationMessage assistantMessage = saveMessage(conversationId, "assistant", responseText);
            updateConversationMessageCount(conversationId);

            // 记忆总结：第一轮和以后每10轮
            int round = getConversationRound(conversationId);
            if (round == 1 || (round > 1 && round % SUMMARY_ROUND_INTERVAL == 0)) {
                summarizeConversation(conversationId);
            }

            return Flux.just(
                ServerSentEvent.<String>builder()
                    .event("done")
                    .data(toJson(Map.of(
                        "messageId", assistantMessage.getMessageId(),
                        "conversationId", conversationId,
                        "content", responseText
                    )))
                    .build()
            );
        });

        return Flux.concat(metaFlux, tokenFlux, doneFlux)
            .onErrorResume(e -> {
                log.error("Error in stream", e);
                String errorMsg = "抱歉，AI服务暂时不可用。错误: " + e.getMessage();
                String currentText = fullResponseRef.get().toString();
                if (currentText.isEmpty()) {
                    saveMessage(conversationId, "assistant", errorMsg);
                    updateConversationMessageCount(conversationId);
                }
                return Flux.just(
                    ServerSentEvent.<String>builder()
                        .event("error")
                        .data(errorMsg)
                        .build()
                );
            });
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error serializing to JSON", e);
            return "{}";
        }
    }
    
    private User getOrCreateUser(String userId) {
        User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("user_id", userId)
        );
        
        if (user == null) {
            user = new User();
            user.setUserId(userId);
            user.setUserName("User " + userId);
            userMapper.insert(user);
        }
        
        return user;
    }
    
    public String createConversation(String userId) {
        String conversationId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle("New Conversation");
        conversation.setStatus("active");
        conversation.setMessageCount(0);
        conversationMapper.insert(conversation);
        return conversationId;
    }
    
    public Conversation getConversation(String conversationId) {
        return conversationMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Conversation>()
                .eq("conversation_id", conversationId)
        );
    }
    
    public List<Conversation> getConversationsByUserId(String userId) {
        return conversationMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Conversation>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
        );
    }
    
    public List<ConversationMessage> getMessagesByConversationId(String conversationId) {
        return messageMapper.getMessagesByConversationId(conversationId);
    }
    
    private ConversationMessage saveMessage(String conversationId, String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        String modelName = "unknown";
        try {
            AiProvider chatProvider = aiProviderService.getActiveByModelType("chat");
            if (chatProvider != null) modelName = chatProvider.getModelName();
        } catch (Exception ignored) {}
        message.setMetadata(Map.of("model", modelName));
        messageMapper.insert(message);
        return message;
    }
    
    private void updateConversationMessageCount(String conversationId) {
        Long count = messageMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMessage>()
                .eq("conversation_id", conversationId)
        );
        
        conversationMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Conversation>()
                .eq("conversation_id", conversationId)
                .set("message_count", count.intValue())
                .set("updated_at", LocalDateTime.now())
        );
    }
    
    private String buildContext(List<Map<String, Object>> ragResults) {
        if (ragResults.isEmpty()) {
            return "";
        }
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Relevant information from knowledge base:\n");
        for (Map<String, Object> result : ragResults) {
            contextBuilder.append("- ").append(result.get("content")).append("\n\n");
        }
        return contextBuilder.toString();
    }
    
    private List<Message> buildMessages(String ragContext, String conversationSummary, Integer lastSummarizedMessageCount, List<ConversationMessage> history, String currentMessage) {
        List<Message> messages = new ArrayList<>();
        
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        
        // 注入对话历史摘要，替代已被总结的旧消息
        if (conversationSummary != null && !conversationSummary.isEmpty()) {
            messages.add(new SystemMessage("对话历史摘要: " + conversationSummary));
        }
        
        if (ragContext != null && !ragContext.isEmpty()) {
            messages.add(new SystemMessage(ragContext));
        }
        
        // 只保留 user + assistant 消息，与 lastSummarizedMessageCount 的统计口径一致
        List<ConversationMessage> effectiveHistory = history.stream()
            .filter(msg -> "user".equals(msg.getRole()) || "assistant".equals(msg.getRole()))
            .toList();
        
        // 有摘要时，只取未被总结的新消息；无摘要时取全部
        List<ConversationMessage> recentHistory;
        if (conversationSummary != null && !conversationSummary.isEmpty() 
                && lastSummarizedMessageCount != null && lastSummarizedMessageCount > 0) {
            // 跳过已被总结的消息（全部是 user+assistant，索引一一对应）
            int skipCount = Math.min(lastSummarizedMessageCount, effectiveHistory.size());
            recentHistory = effectiveHistory.subList(skipCount, effectiveHistory.size());
        } else {
            // 无摘要，取最近的消息
            recentHistory = effectiveHistory.size() > MAX_HISTORY_MESSAGES
                ? effectiveHistory.subList(effectiveHistory.size() - MAX_HISTORY_MESSAGES, effectiveHistory.size())
                : effectiveHistory;
        }
        
        for (ConversationMessage msg : recentHistory) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        messages.add(new UserMessage(currentMessage));
        
        return messages;
    }
    
    public List<User> getAllUsers() {
        return userMapper.selectList(null);
    }
    
    /**
     * 获取对话当前轮次（用户消息数量）
     */
    private int getConversationRound(String conversationId) {
        Long userMessageCount = messageMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMessage>()
                .eq("conversation_id", conversationId)
                .eq("role", "user")
        );
        return userMessageCount.intValue();
    }

    /**
     * AI 记忆总结：生成对话摘要并更新标题
     * 在第一轮和以后每10轮自动触发
     */
    private void summarizeConversation(String conversationId) {
        try {
            Conversation conversation = getConversation(conversationId);
            if (conversation == null) return;

            List<ConversationMessage> messages = messageMapper.getMessagesByConversationId(conversationId);

            // 只统计 user + assistant 消息数，与 buildMessages 中的实际使用量一致
            long effectiveMessageCount = messages.stream()
                .filter(msg -> !"system".equals(msg.getRole()))
                .count();

            StringBuilder conversationText = new StringBuilder();
            for (ConversationMessage msg : messages) {
                if ("system".equals(msg.getRole())) continue;
                String content = msg.getContent();
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                String roleLabel = "user".equals(msg.getRole()) ? "用户" : "助手";
                conversationText.append(roleLabel).append(": ").append(content).append("\n\n");
            }

            String prompt = String.format(SUMMARY_PROMPT, conversationText.toString());

            ChatResponse response = modelProvider.getChatModel().call(new Prompt(List.of(new UserMessage(prompt))));
            String result = response.getResult().getOutput().getText();

            // 清理 markdown 代码块标记
            String cleaned = result.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            @SuppressWarnings("unchecked")
            Map<String, String> parsed = objectMapper.readValue(cleaned, Map.class);

            String title = parsed.getOrDefault("title", conversation.getTitle());
            String summary = parsed.getOrDefault("summary", "");

            conversationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Conversation>()
                    .eq("conversation_id", conversationId)
                    .set("title", title)
                    .set("summary", summary)
                    .set("last_summarized_message_count", effectiveMessageCount)
                    .set("updated_at", LocalDateTime.now())
            );

            log.info("Conversation {} summarized: title={}", conversationId, title);
        } catch (Exception e) {
            log.warn("Failed to summarize conversation {}: {}", conversationId, e.getMessage());
        }
    }

    public boolean deleteConversation(String conversationId) {
        Conversation conversation = getConversation(conversationId);
        if (conversation != null) {
            conversationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Conversation>()
                    .eq("conversation_id", conversationId)
                    .set("deleted", 1)
            );

            messageMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ConversationMessage>()
                    .eq("conversation_id", conversationId)
                    .set("deleted", 1)
            );

            return true;
        }
        return false;
    }
}
