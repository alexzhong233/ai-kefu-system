package com.aikefu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * - 异步请求超时（SSE 流式响应需要）
 * - 禁用 Tomcat 输出缓冲对 SSE 的影响
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // SSE 流式响应超时：5 分钟
        configurer.setDefaultTimeout(300000L);
    }
}
