package com.aikefu.service;

import com.aikefu.entity.AiProvider;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;


import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DynamicModelProvider {

    private final AiProviderService providerService;
    private final ConcurrentHashMap<String, Object> modelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> fingerprintCache = new ConcurrentHashMap<>();

    public DynamicModelProvider(AiProviderService providerService) {
        this.providerService = providerService;
    }

    public ChatModel getChatModel() {
        AiProvider provider = providerService.getActiveByModelType("chat");
        if (provider == null) {
            throw new IllegalStateException("未配置聊天模型，请在系统设置中添加并激活一个聊天模型配置");
        }
        String cacheKey = "chat_" + provider.getProviderId();
        String fingerprint = provider.getProviderId();

        if (!fingerprint.equals(fingerprintCache.get("chat"))) {
            synchronized (this) {
                if (!fingerprint.equals(fingerprintCache.get("chat"))) {
                    ChatModel model = createChatModel(provider);
                    modelCache.put(cacheKey, model);
                    fingerprintCache.put("chat", fingerprint);
                    log.info("重建聊天模型: name={}, type={}, model={}",
                            provider.getName(), provider.getProviderType(), provider.getModelName());
                }
            }
        }
        return (ChatModel) modelCache.get(cacheKey);
    }

    public EmbeddingModel getEmbeddingModel() {
        AiProvider provider = providerService.getActiveByModelType("embedding");
        if (provider == null) {
            throw new IllegalStateException("未配置 Embedding 模型，请在系统设置中添加并激活一个 Embedding 模型配置");
        }
        String cacheKey = "embedding_" + provider.getProviderId();
        String fingerprint = provider.getProviderId();

        if (!fingerprint.equals(fingerprintCache.get("embedding"))) {
            synchronized (this) {
                if (!fingerprint.equals(fingerprintCache.get("embedding"))) {
                    EmbeddingModel model = createEmbeddingModel(provider);
                    modelCache.put(cacheKey, model);
                    fingerprintCache.put("embedding", fingerprint);
                    log.info("重建 Embedding 模型: name={}, type={}, model={}",
                            provider.getName(), provider.getProviderType(), provider.getModelName());
                }
            }
        }
        return (EmbeddingModel) modelCache.get(cacheKey);
    }

    public AiProvider getActiveEmbeddingProvider() {
        return providerService.getActiveByModelType("embedding");
    }

    public AiProvider getActiveRerankProvider() {
        return providerService.getActiveByModelType("rerank");
    }

    public void invalidate() {
        synchronized (this) {
            fingerprintCache.clear();
            modelCache.clear();
            log.info("模型缓存已清除");
        }
    }

    public void invalidateByType(String modelType) {
        synchronized (this) {
            fingerprintCache.remove(modelType);
            modelCache.entrySet().removeIf(e -> e.getKey().startsWith(modelType + "_"));
            log.info("模型缓存已清除: type={}", modelType);
        }
    }

    public ChatResponse testChatModel(String providerType, String apiKey, String baseUrl, String modelName) {
        AiProvider temp = new AiProvider();
        temp.setProviderType(providerType);
        temp.setApiKey(apiKey);
        temp.setBaseUrl(baseUrl);
        temp.setModelName(modelName);
        ChatModel model = createChatModel(temp);
        return model.call(new Prompt(List.of(new UserMessage("Say 'connection successful' in one sentence."))));
    }

    public EmbeddingResponse testEmbeddingModel(String providerType, String apiKey, String baseUrl, String modelName) {
        AiProvider temp = new AiProvider();
        temp.setProviderType(providerType);
        temp.setApiKey(apiKey);
        temp.setBaseUrl(baseUrl);
        temp.setModelName(modelName);
        EmbeddingModel model = createEmbeddingModel(temp);
        return model.embedForResponse(List.of("test connection"));
    }

    // ======================== 模型创建 ========================

    private ChatModel createChatModel(AiProvider provider) {
        return switch (provider.getProviderType()) {
            case "dashscope" -> createDashScopeChatModel(provider);
            case "openai" -> createOpenAiChatModel(provider);
            default -> throw new IllegalArgumentException("不支持的聊天模型供应商: " + provider.getProviderType());
        };
    }

    private EmbeddingModel createEmbeddingModel(AiProvider provider) {
        return switch (provider.getProviderType()) {
            case "dashscope" -> createDashScopeEmbeddingModel(provider);
            case "openai" -> createOpenAiEmbeddingModel(provider);
            default -> throw new IllegalArgumentException("不支持的 Embedding 供应商: " + provider.getProviderType());
        };
    }

    private ChatModel createDashScopeChatModel(AiProvider provider) {
        double temperature = providerService.getExtraConfigFloat(provider, "temperature", 0.7f);

        DashScopeApi api = DashScopeApi.builder()
                .apiKey(provider.getApiKey())
                .build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(provider.getModelName())
                .withTemperature(temperature)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel createOpenAiChatModel(AiProvider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        double temperature = providerService.getExtraConfigFloat(provider, "temperature", 0.7f);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(provider.getModelName())
                .temperature(temperature)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private EmbeddingModel createDashScopeEmbeddingModel(AiProvider provider) {
        int dimension = providerService.getExtraConfigInt(provider, "dimension", 1024);
        String textType = providerService.getExtraConfigValue(provider, "textType", "document");

        DashScopeApi api = DashScopeApi.builder()
                .apiKey(provider.getApiKey())
                .build();

        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel(provider.getModelName())
                .withDimensions(dimension)
                .withTextType(textType)
                .build();

        return new DashScopeEmbeddingModel(api, MetadataMode.NONE, options);
    }

    private EmbeddingModel createOpenAiEmbeddingModel(AiProvider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(provider.getModelName())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }
}
