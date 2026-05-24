package com.nairb.ai130.infrastructure.mapper;

import com.nairb.ai130.domain.model.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiModelConfigMapper {

    @Select("SELECT * FROM ai_model_config WHERE enabled = true ORDER BY sort_order")
    List<AiModelConfig> findEnabled();

    @Select("SELECT * FROM ai_model_config WHERE enabled = true AND is_default = true LIMIT 1")
    AiModelConfig findDefault();

    @Select("SELECT * FROM ai_model_config WHERE model_code = #{modelCode} AND enabled = true LIMIT 1")
    AiModelConfig findByCode(String modelCode);
}
