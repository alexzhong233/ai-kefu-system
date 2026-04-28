package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("t_rag_chunk")
public class RagChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String chunkId;
    private String documentId;
    private String content;
    private Integer chunkIndex;
    
    @TableField(typeHandler = com.aikefu.util.MapTypeHandler.class)
    private Map<String, Object> metadata;
    
    @TableField(typeHandler = com.aikefu.util.VectorTypeHandler.class)
    private float[] embedding;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
