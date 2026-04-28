package com.aikefu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aikefu.mapper")
public class AiKefuApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AiKefuApplication.class, args);
    }
}
