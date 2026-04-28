package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String conversationId;
    private String userId;
    private String title;
    private String status;
    private Integer messageCount;
    private String summary;
    private Integer lastSummarizedMessageCount;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
