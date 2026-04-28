package com.aikefu.controller;

import com.aikefu.dto.ChatRequest;
import com.aikefu.entity.Conversation;
import com.aikefu.entity.ConversationMessage;
import com.aikefu.entity.User;
import com.aikefu.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatRequest request) {
        try {
            Map<String, Object> response = chatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);

        executor.execute(() -> {
            try {
                chatService.chatStream(request, emitter);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
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
