package com.nairb.ai130.infrastructure.repository;

import com.nairb.ai130.domain.model.AiModelConfig;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiModelRepository {

    private final JdbcTemplate jdbc;

    public AiModelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AiModelConfig> findEnabled() {
        return jdbc.query(
                "SELECT * FROM ai_model_config WHERE enabled = true ORDER BY sort_order",
                new BeanPropertyRowMapper<>(AiModelConfig.class));
    }

    public AiModelConfig findDefault() {
        List<AiModelConfig> list = jdbc.query(
                "SELECT * FROM ai_model_config WHERE enabled = true AND is_default = true LIMIT 1",
                new BeanPropertyRowMapper<>(AiModelConfig.class));
        return list.isEmpty() ? null : list.get(0);
    }

    public AiModelConfig findByCode(String modelCode) {
        List<AiModelConfig> list = jdbc.query(
                "SELECT * FROM ai_model_config WHERE model_code = ? AND enabled = true LIMIT 1",
                new BeanPropertyRowMapper<>(AiModelConfig.class), modelCode);
        return list.isEmpty() ? null : list.get(0);
    }
}
