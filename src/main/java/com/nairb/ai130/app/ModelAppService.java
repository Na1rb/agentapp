package com.nairb.ai130.app;

import com.nairb.ai130.domain.entity.AiModelConfig;
import com.nairb.ai130.domain.repository.AiModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型列表服务。
 * <p>
 * 从 {@code ai_model_config} 数据库表中读取可用模型，暴露给前端模型选择器。
 */
@Service
public class ModelAppService {

    private static final Logger log = LoggerFactory.getLogger(ModelAppService.class);

    private final AiModelConfigRepository repository;

    public ModelAppService(AiModelConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取所有启用的对话模型列表。
     * <p>
     * 每个模型包含 {@code code}（程序标识）和 {@code name}（显示名称）。
     */
    public List<Map<String, String>> listModels() {
        List<AiModelConfig> configs = repository.findAllEnabled();
        List<Map<String, String>> models = new ArrayList<>(configs.size());
        for (AiModelConfig c : configs) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", c.getModelCode());
            m.put("name", c.getModelName());
            models.add(m);
        }
        return models;
    }

    /**
     * 获取默认模型。
     * <p>
     * 从 {@code ai_model_config} 表中查找 {@code is_default = true} 的记录，
     * 如果找不到则回退到第一条启用的模型。
     */
    public Map<String, String> getDefaultModel() {
        AiModelConfig defaultConfig = repository.findDefault().orElse(null);
        if (defaultConfig == null) {
            // 兜底：取第一个启用的模型
            List<AiModelConfig> all = repository.findAllEnabled();
            if (all.isEmpty()) {
                log.warn("No enabled models found in database, returning fallback");
                Map<String, String> fallback = new LinkedHashMap<>();
                fallback.put("code", "qwen-plus");
                fallback.put("name", "通义千问 Plus");
                return fallback;
            }
            defaultConfig = all.get(0);
        }
        Map<String, String> model = new LinkedHashMap<>();
        model.put("code", defaultConfig.getModelCode());
        model.put("name", defaultConfig.getModelName());
        return model;
    }
}
