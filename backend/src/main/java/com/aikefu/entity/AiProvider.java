package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.aikefu.util.MapTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "t_ai_provider", autoResultMap = true)
public class AiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerId;

    private String name;

    private String providerType;

    private String modelType;

    private String apiKey;

    private String baseUrl;

    private String modelName;

    @TableField(typeHandler = MapTypeHandler.class)
    private Map<String, Object> extraConfig;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
