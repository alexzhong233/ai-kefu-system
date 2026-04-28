package com.aikefu.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatResponse {
    private String messageId;
    private String conversationId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
