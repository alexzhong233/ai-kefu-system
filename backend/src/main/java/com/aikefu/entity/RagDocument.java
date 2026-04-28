package com.aikefu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_rag_document")
public class RagDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String documentId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String contentText;
    private String status;
    private Integer chunkCount;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;
}
