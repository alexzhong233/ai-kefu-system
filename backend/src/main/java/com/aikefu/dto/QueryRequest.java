package com.aikefu.dto;

import lombok.Data;

@Data
public class QueryRequest {
    private String query;
    private Integer topK;
    private Double threshold;
}
