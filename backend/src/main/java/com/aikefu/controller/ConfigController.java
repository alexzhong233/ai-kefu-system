package com.aikefu.controller;

import com.aikefu.dto.AiProviderRequest;
import com.aikefu.entity.AiProvider;
import com.aikefu.service.AiProviderService;
import com.aikefu.service.DynamicModelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AiProviderService providerService;
    private final DynamicModelProvider modelProvider;

    @GetMapping("/providers")
    public ResponseEntity<List<AiProvider>> listProviders(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return ResponseEntity.ok(providerService.listByModelType(type));
        }
        return ResponseEntity.ok(providerService.listAll());
    }

    @PostMapping("/providers")
    public ResponseEntity<Map<String, Object>> createProvider(@RequestBody AiProviderRequest request) {
        try {
            AiProvider provider = new AiProvider();
            provider.setName(request.getName());
            provider.setProviderType(request.getProviderType());
            provider.setModelType(request.getModelType());
            provider.setApiKey(request.getApiKey());
            provider.setBaseUrl(request.getBaseUrl());
            provider.setModelName(request.getModelName());
            provider.setExtraConfig(request.getExtraConfig());

            AiProvider created = providerService.create(provider);
            return ResponseEntity.ok(Map.of("success", true, "provider", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> updateProvider(@PathVariable Long id, @RequestBody AiProviderRequest request) {
        try {
            AiProvider provider = new AiProvider();
            provider.setName(request.getName());
            provider.setProviderType(request.getProviderType());
            provider.setApiKey(request.getApiKey());
            provider.setBaseUrl(request.getBaseUrl());
            provider.setModelName(request.getModelName());
            provider.setExtraConfig(request.getExtraConfig());

            AiProvider updated = providerService.update(id, provider);
            return ResponseEntity.ok(Map.of("success", true, "provider", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> deleteProvider(@PathVariable Long id) {
        try {
            boolean deleted = providerService.delete(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("success", true));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Provider not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/providers/{id}/activate")
    public ResponseEntity<Map<String, Object>> activateProvider(@PathVariable Long id) {
        try {
            AiProvider activated = providerService.activate(id);
            // Invalidate the model cache for this type
            modelProvider.invalidateByType(activated.getModelType());
            return ResponseEntity.ok(Map.of("success", true, "provider", activated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testProvider(@RequestBody Map<String, Object> request) {
        try {
            String providerType = (String) request.get("providerType");
            String modelType = (String) request.get("modelType");
            String apiKey = (String) request.get("apiKey");
            String baseUrl = (String) request.get("baseUrl");
            String modelName = (String) request.get("modelName");

            if ("chat".equals(modelType)) {
                ChatResponse response = modelProvider.testChatModel(providerType, apiKey, baseUrl, modelName);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "response", response.getResult().getOutput().getText()
                ));
            } else if ("embedding".equals(modelType)) {
                EmbeddingResponse response = modelProvider.testEmbeddingModel(providerType, apiKey, baseUrl, modelName);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "dimension", response.getResult().getOutput().length
                ));
            } else if ("rerank".equals(modelType)) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Rerank 配置已保存"
                ));
            }

            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Unknown model type: " + modelType));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
