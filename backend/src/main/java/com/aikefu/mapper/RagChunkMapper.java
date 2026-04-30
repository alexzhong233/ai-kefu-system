package com.aikefu.mapper;

import com.aikefu.entity.RagChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    List<RagChunk> searchByVector(@Param("embedding") float[] embedding, @Param("topK") int topK,
                                  @Param("embeddingProviderId") String embeddingProviderId);

    List<Map<String, Object>> searchByVectorWithThreshold(
        @Param("embedding") float[] embedding,
        @Param("topK") int topK,
        @Param("threshold") double threshold,
        @Param("embeddingProviderId") String embeddingProviderId
    );

    /**
     * 根据 chunk_id 查询 chunk 内容（用于 Parent-Child 扩展上下文）
     */
    Map<String, Object> findByChunkId(@Param("chunkId") String chunkId);
}
