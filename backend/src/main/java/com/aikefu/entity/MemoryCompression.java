package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_memory_compression")
public class MemoryCompression {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String compressionId;
    private String conversationId;
    private Integer originalMessageCount;
    private Integer compressedMessageCount;
    private String summary;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableLogic
    private Integer deleted;
}
