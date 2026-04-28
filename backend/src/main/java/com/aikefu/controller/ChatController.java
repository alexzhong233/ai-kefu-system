package com.aikefu.controller;

import com.aikefu.dto.ChatRequest;
import com.aikefu.entity.Conversation;
import com.aikefu.entity.ConversationMessage;
import com.aikefu.entity.User;
import com.aikefu.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatService chatService;
    
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatRequest request) {
        try {
            Map<String, Object> response = chatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 流式聊天 — 直接返回 Flux<ServerSentEvent>
     * Spring MVC 会自动管理 SSE 流的 flush，不需要手动 SseEmitter
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamMessage(@RequestBody ChatRequest request) {
        return chatService.chatStreamFlux(request);
    }
    
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        try {
            List<User> users = chatService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/conversations")
    public ResponseEntity<Map<String, String>> createConversation(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            if (userId == null || userId.isEmpty()) {
                userId = "user1";
            }
            String conversationId = chatService.createConversation(userId);
            return ResponseEntity.ok(Map.of("conversationId", conversationId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> getConversations(@RequestParam String userId) {
        try {
            List<Conversation> conversations = chatService.getConversationsByUserId(userId);
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ConversationMessage>> getMessages(@PathVariable String conversationId) {
        try {
            List<ConversationMessage> messages = chatService.getMessagesByConversationId(conversationId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String conversationId) {
        try {
            boolean success = chatService.deleteConversation(conversationId);
            return ResponseEntity.ok(Map.of("success", success, "conversationId", conversationId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
