package com.aikefu.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {
    private String userId;
    private String conversationId;
    private String message;
    private List<Map<String, String>> history;
}
