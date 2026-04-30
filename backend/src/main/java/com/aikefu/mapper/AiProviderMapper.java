package com.aikefu.mapper;

import com.aikefu.entity.AiProvider;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {

    @Update("UPDATE t_ai_provider SET is_active = FALSE, updated_at = NOW() " +
            "WHERE model_type = #{modelType} AND deleted = 0")
    int deactivateAllByModelType(@Param("modelType") String modelType);
}
