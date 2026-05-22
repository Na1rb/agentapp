package com.nairb.ai130.infrastructure.repository;

import com.nairb.ai130.domain.model.AiModelConfig;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiModelRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiModelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiModelConfig> findAllEnabledModels() {
        String sql = "SELECT * FROM ai_model_config WHERE enabled = true ORDER BY sort_order ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AiModelConfig.class));
    }

    public AiModelConfig findDefaultModel() {
        String sql = "SELECT * FROM ai_model_config WHERE enabled = true AND is_default = true LIMIT 1";
        List<AiModelConfig> results = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AiModelConfig.class));
        return results.isEmpty() ? null : results.get(0);
    }

    public AiModelConfig findByModelCode(String modelCode) {
        String sql = "SELECT * FROM ai_model_config WHERE model_code = ? AND enabled = true LIMIT 1";
        List<AiModelConfig> results = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AiModelConfig.class), modelCode);
        return results.isEmpty() ? null : results.get(0);
    }
}
