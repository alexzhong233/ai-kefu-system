package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("t_conversation_message")
public class ConversationMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String messageId;
    private String conversationId;
    private String role;
    private String content;
    
    @TableField(typeHandler = com.aikefu.util.MapTypeHandler.class)
    private Map<String, Object> metadata;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
