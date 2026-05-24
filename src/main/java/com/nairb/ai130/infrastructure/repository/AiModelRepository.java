package com.nairb.ai130.infrastructure.repository;

import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.mapper.AiModelConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiModelRepository {

    private final AiModelConfigMapper aiModelConfigMapper;

    public AiModelRepository(AiModelConfigMapper aiModelConfigMapper) {
        this.aiModelConfigMapper = aiModelConfigMapper;
    }

    public List<AiModelConfig> findEnabled() {
        return aiModelConfigMapper.findEnabled();
    }

    public AiModelConfig findDefault() {
        return aiModelConfigMapper.findDefault();
    }

    public AiModelConfig findByCode(String modelCode) {
        return aiModelConfigMapper.findByCode(modelCode);
    }
}
