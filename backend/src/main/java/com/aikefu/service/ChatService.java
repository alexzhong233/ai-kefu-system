package com.aikefu.service;

import com.aikefu.dto.ChatRequest;
import com.aikefu.dto.QueryRequest;
import com.aikefu.entity.*;
import com.aikefu.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final ChatModel chatModel;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final MemoryCompressionMapper compressionMapper;
    private final RagService ragService;
    
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int COMPRESSION_THRESHOLD = 30;
    private static final String SYSTEM_PROMPT = """
        You are a helpful AI customer service assistant. 
        Use the provided context to answer user questions accurately.
        If you don't know something, say so honestly.
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
        
        if (history.size() >= COMPRESSION_THRESHOLD) {
            compressMemory(conversationId, history);
        }
        
        history = messageMapper.getMessagesByConversationId(conversationId);
        
        List<Message> messages = buildMessages(context, history, message);

        // 先保存用户消息
        saveMessage(conversationId, "user", message);

        String assistantResponse;
        try {
            ChatResponse chatResponse = chatModel.call(new Prompt(messages));
            assistantResponse = chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Error calling chat API", e);
            assistantResponse = "抱歉，AI服务暂时不可用。错误: " + e.getMessage();
        }
        
        ConversationMessage assistantMessage = saveMessage(conversationId, "assistant", assistantResponse);
        
        updateConversationMessageCount(conversationId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("messageId", assistantMessage.getMessageId());
        response.put("conversationId", conversationId);
        response.put("role", "assistant");
        response.put("content", assistantResponse);
        response.put("createdAt", assistantMessage.getCreatedAt());
        
        return response;
    }

    public void chatStream(ChatRequest request, SseEmitter emitter) {
        String userId = request.getUserId();
        String convId = request.getConversationId();
        String message = request.getMessage();

        if (userId == null || userId.isEmpty()) {
            userId = "user1";
        }

        getOrCreateUser(userId);

        if (convId == null || convId.isEmpty()) {
            convId = createConversation(userId);
        }

        Conversation conversation = getConversation(convId);
        if (conversation == null) {
            convId = createConversation(userId);
        }

        final String conversationId = convId;

        // 先保存用户消息
        saveMessage(conversationId, "user", message);

        // 发送 conversationId 给前端
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of("conversationId", conversationId)));
        } catch (IOException e) {
            log.error("Error sending meta event", e);
            return;
        }

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuery(message);
        queryRequest.setTopK(5);
        List<Map<String, Object>> ragResults = ragService.queryRag(queryRequest);

        String context = buildContext(ragResults);

        List<ConversationMessage> history = messageMapper.getMessagesByConversationId(conversationId);

        if (history.size() >= COMPRESSION_THRESHOLD) {
            compressMemory(conversationId, history);
        }

        history = messageMapper.getMessagesByConversationId(conversationId);

        List<Message> messages = buildMessages(context, history, message);

        StringBuilder fullResponse = new StringBuilder();
        // 使用原子标志防止重复完成
        final boolean[] isCompleted = {false};
        try {
            chatModel.stream(new Prompt(messages))
                .doOnNext(chatResponse -> {
                    if (isCompleted[0]) return;
                    try {
                        String token = chatResponse.getResult().getOutput().getText();
                        if (token != null && !token.isEmpty()) {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event().name("token").data(token));
                        }
                    } catch (IOException e) {
                        log.error("Error sending token event", e);
                        isCompleted[0] = true;
                        emitter.completeWithError(e);
                    }
                })
                .doOnComplete(() -> {
                    if (isCompleted[0]) return;
                    isCompleted[0] = true;
                    ConversationMessage assistantMessage = saveMessage(conversationId, "assistant", fullResponse.toString());
                    updateConversationMessageCount(conversationId);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(Map.of(
                            "messageId", assistantMessage.getMessageId(),
                            "conversationId", conversationId
                        )));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Error sending done event", e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    if (isCompleted[0]) return;
                    isCompleted[0] = true;
                    log.error("Error in stream", e);
                    String errorMsg = "抱歉，AI服务暂时不可用。错误: " + e.getMessage();
                    ConversationMessage assistantMessage = saveMessage(conversationId, "assistant", errorMsg);
                    updateConversationMessageCount(conversationId);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(errorMsg));
                        emitter.complete();
                    } catch (IOException ex) {
                        log.error("Error sending error event", ex);
                        emitter.completeWithError(ex);
                    }
                })
                .subscribe();
        } catch (Exception e) {
            log.error("Error calling stream API", e);
            String errorMsg = "抱歉，AI服务暂时不可用。错误: " + e.getMessage();
            saveMessage(conversationId, "assistant", errorMsg);
            try {
                emitter.send(SseEmitter.event().name("error").data(errorMsg));
            } catch (IOException ignored) {}
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
        message.setMetadata(Map.of("model", "deepseek-v4-flash"));
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
    
    private List<Message> buildMessages(String context, List<ConversationMessage> history, String currentMessage) {
        List<Message> messages = new ArrayList<>();
        
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        
        if (context != null && !context.isEmpty()) {
            messages.add(new SystemMessage(context));
        }
        
        List<ConversationMessage> recentHistory = history.size() > MAX_HISTORY_MESSAGES
            ? history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size())
            : history;
        
        for (ConversationMessage msg : recentHistory) {
            switch (msg.getRole()) {
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        messages.add(new UserMessage(currentMessage));
        
        return messages;
    }
    
    private void compressMemory(String conversationId, List<ConversationMessage> history) {
        log.info("Compressing memory for conversation: {}", conversationId);
        
        String compressionId = UUID.randomUUID().toString();
        
        int originalCount = history.size();
        
        List<ConversationMessage> firstHalf = history.subList(0, history.size() / 2);
        List<ConversationMessage> secondHalf = history.subList(history.size() / 2, history.size());
        
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("Conversation summary (first ").append(firstHalf.size()).append(" messages):\n");
        
        for (ConversationMessage msg : firstHalf) {
            if ("user".equals(msg.getRole())) {
                String content = msg.getContent();
                summaryBuilder.append("User: ").append(content.substring(0, Math.min(100, content.length()))).append("\n");
            }
        }
        
        String summary = summaryBuilder.toString();
        
        for (ConversationMessage msg : firstHalf) {
            msg.setDeleted(1);
            messageMapper.updateById(msg);
        }
        
        ConversationMessage summaryMessage = new ConversationMessage();
        summaryMessage.setMessageId(UUID.randomUUID().toString());
        summaryMessage.setConversationId(conversationId);
        summaryMessage.setRole("system");
        summaryMessage.setContent(summary);
        summaryMessage.setMetadata(Map.of("type", "memory_compression"));
        messageMapper.insert(summaryMessage);
        
        MemoryCompression compression = new MemoryCompression();
        compression.setCompressionId(compressionId);
        compression.setConversationId(conversationId);
        compression.setOriginalMessageCount(originalCount);
        compression.setCompressedMessageCount(secondHalf.size() + 1);
        compression.setSummary(summary);
        compressionMapper.insert(compression);
        
        log.info("Memory compressed: {} -> {} messages", originalCount, secondHalf.size() + 1);
    }
    
    public List<User> getAllUsers() {
        return userMapper.selectList(null);
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
