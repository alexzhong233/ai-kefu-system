package com.aikefu.mapper;

import com.aikefu.entity.RagChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {
    
    @Select("SELECT * FROM t_rag_chunk " +
            "WHERE deleted = 0 " +
            "ORDER BY embedding <=> #{embedding}::vector " +
            "LIMIT #{topK}")
    List<RagChunk> searchByVector(@Param("embedding") float[] embedding, @Param("topK") int topK);
    
    @Select("SELECT chunk_id, document_id, content, chunk_index, metadata, " +
            "1 - (embedding <=> #{embedding}::vector) AS similarity " +
            "FROM t_rag_chunk " +
            "WHERE deleted = 0 " +
            "AND 1 - (embedding <=> #{embedding}::vector) >= #{threshold} " +
            "ORDER BY embedding <=> #{embedding}::vector " +
            "LIMIT #{topK}")
    List<Map<String, Object>> searchByVectorWithThreshold(
        @Param("embedding") float[] embedding, 
        @Param("topK") int topK,
        @Param("threshold") double threshold
    );
}
