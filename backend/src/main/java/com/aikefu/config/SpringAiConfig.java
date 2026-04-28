package com.aikefu.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Alibaba 配置
 * 
 * ChatModel 和 EmbeddingModel 由 spring-ai-alibaba-starter-dashscope 自动配置，
 * 无需手动创建 Bean。
 * 
 * 配置项在 application.yml 的 spring.ai.dashscope 下设置：
 * - Chat 模型: deepseek-v4-flash
 * - Embedding 模型: text-embedding-v3
 */
@Configuration
public class SpringAiConfig {
}
