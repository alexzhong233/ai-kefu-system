# Spring AI Alibaba 集成说明

## 📦 依赖配置

项目已配置使用Spring AI和Spring AI Alibaba：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-core</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-alibaba</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

## ⚙️ 配置类

创建了 `SpringAiAlibabaConfig.java` 配置类：

```java
@Configuration
public class SpringAiAlibabaConfig {
    
    // Chat Model Bean
    @Bean
    public AlibabaChatModel alibabaChatModel() {
        return AlibabaChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(chatModel)
                .build();
    }
    
    // ChatClient Bean
    @Bean
    public ChatClient chatClient(AlibabaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
    
    // Embedding Model Bean
    @Bean
    public AlibabaEmbeddingModel alibabaEmbeddingModel() {
        return AlibabaEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(embeddingModel)
                .build();
    }
    
    @Bean
    public EmbeddingModel embeddingModel(AlibabaEmbeddingModel alibabaEmbeddingModel) {
        return alibabaEmbeddingModel;
    }
}
```

## 📝 application.yml 配置

```yaml
spring:
  ai:
    alibaba:
      api-key: ${DEEPSEEK_API_KEY:sk-your-api-key-here}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      chat:
        model: deepseek-chat
        temperature: 0.7
        max-tokens: 2048
      embedding:
        model: text-embedding-v3
```

## 🤖 使用示例

### ChatService - 使用 ChatClient

```java
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final ChatClient chatClient;
    
    public String chat(String message) {
        // 使用 ChatClient 发送消息
        String response = chatClient.prompt()
                .user(message)
                .call()
                .content();
        
        return response;
    }
}
```

### RagService - 使用 EmbeddingModel

```java
@Service
@RequiredArgsConstructor
public class RagService {
    
    private final EmbeddingModel embeddingModel;
    
    public float[] generateEmbedding(String text) {
        // 使用 EmbeddingModel 生成向量
        EmbeddingRequest request = new EmbeddingRequest(text, embeddingModel);
        EmbeddingResponse response = embeddingModel.embed(request);
        
        return response.getResult().getEmbedding();
    }
}
```

## 🔄 API 端点

### Chat API

```java
// ChatService.java
public Map<String, Object> chat(ChatRequest request) {
    // 1. 获取用户和对话
    User user = getOrCreateUser(request.getUserId());
    Conversation conversation = getOrCreateConversation(request.getUserId(), request.getConversationId());
    
    // 2. 查询 RAG 知识库
    List<Map<String, Object>> ragResults = ragService.queryRag(request.getMessage());
    
    // 3. 构建上下文
    String context = buildContext(ragResults);
    
    // 4. 使用 ChatClient 发送消息
    String response = chatClient.prompt()
            .user("Context: " + context + "\n\nUser: " + request.getMessage())
            .call()
            .content();
    
    // 5. 保存消息并返回
    saveMessage(conversationId, "user", request.getMessage());
    saveMessage(conversationId, "assistant", response);
    
    return response;
}
```

### Embedding API

```java
// RagService.java
public float[] generateEmbedding(String text) {
    EmbeddingRequest request = new EmbeddingRequest(text, embeddingModel);
    EmbeddingResponse response = embeddingModel.embed(request);
    return response.getResult().getEmbedding();
}
```

## 📊 支持的模型

### Chat Models
- `deepseek-chat` - DeepSeek Chat 模型

### Embedding Models
- `text-embedding-v3` - 1536 维向量

## 🔧 自定义配置

### 修改模型参数

在 `application.yml` 中修改：

```yaml
spring:
  ai:
    alibaba:
      chat:
        model: deepseek-chat
        temperature: 0.8  # 调整创造性 (0.0-1.0)
        max-tokens: 2048  # 调整最大token数
```

### 添加系统提示

在 `ChatService.java` 中修改：

```java
private static final String SYSTEM_PROMPT = """
    You are a helpful AI customer service assistant.
    Use the provided context to answer user questions accurately.
    If you don't know something, say so honestly.
    """;
```

## 🐛 故障排除

### 1. ChatClient 注入失败

**问题**: `No qualifying bean of type 'ChatClient'`

**解决方案**: 检查 `SpringAiAlibabaConfig` 配置类是否被扫描到

```java
@SpringBootApplication
@MapperScan("com.aikefu.mapper")
public class AiKefuApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiKefuApplication.class, args);
    }
}
```

### 2. EmbeddingModel 注入失败

**问题**: `No qualifying bean of type 'EmbeddingModel'`

**解决方案**: 确保 `SpringAiAlibabaConfig` 中同时定义了 `AlibabaEmbeddingModel` 和 `EmbeddingModel` Bean

### 3. API Key 无效

**问题**: 返回 401 或认证错误

**解决方案**: 
- 检查 `application.yml` 中的 `spring.ai.alibaba.api-key`
- 确认 API Key 有效
- 检查网络连接

## 📚 更多资源

- [Spring AI 官方文档](https://spring.io/projects/spring-ai)
- [Spring AI Alibaba](https://github.com/spring-projects/spring-ai)
- [DeepSeek API 文档](https://platform.deepseek.com/)

## 🎯 最佳实践

1. **错误处理**: 始终捕获异常并返回友好错误消息
2. **日志记录**: 使用 `@Slf4j` 记录关键操作
3. **资源管理**: Spring AI 自动管理连接池
4. **配置分离**: 使用 `.env` 文件管理敏感信息
5. **测试**: 先在本地测试 API 调用

## 🔄 版本兼容性

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.2.0 |
| Spring AI | 1.0.0-M4 |
| Java | 17+ |
| PostgreSQL | 15+ |
| PGVector | latest |

## ✅ 验证清单

- [x] Spring AI Alibaba 依赖已添加
- [x] 配置类已创建
- [x] ChatClient Bean 已定义
- [x] EmbeddingModel Bean 已定义
- [x] application.yml 已配置
- [x] ChatService 使用 ChatClient
- [x] RagService 使用 EmbeddingModel
- [x] 代码结构验证通过
