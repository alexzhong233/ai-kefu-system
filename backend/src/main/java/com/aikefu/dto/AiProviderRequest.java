package com.aikefu.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiProviderRequest {
    private String name;
    private String providerType;
    private String modelType;
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private Map<String, Object> extraConfig;
}
