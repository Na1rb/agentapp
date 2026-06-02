package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AiModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型配置数据访问层。
 */
@Repository
public class AiModelConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigRepository.class);

    private final JdbcTemplate jdbc;

    public AiModelConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AiModelConfig> ROW_MAPPER = (rs, rowNum) -> {
        AiModelConfig config = new AiModelConfig();
        config.setId(rs.getLong("id"));
        config.setModelCode(rs.getString("model_code"));
        config.setModelName(rs.getString("model_name"));
        config.setProvider(rs.getString("provider"));
        config.setBaseUrl(rs.getString("base_url"));
        config.setApiKey(rs.getString("api_key"));
        config.setIsDefault(rs.getBoolean("is_default"));
        config.setIsEnabled(rs.getBoolean("is_enabled"));
        config.setSortOrder(rs.getInt("sort_order"));
        config.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        config.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return config;
    };

    /**
     * 查询所有启用的模型（按 sort_order 排序）。
     */
    public List<AiModelConfig> findAllEnabled() {
        String sql = "SELECT * FROM ai_model_config WHERE is_enabled = true ORDER BY sort_order ASC";
        try {
            return jdbc.query(sql, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Failed to query enabled models", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询所有模型（含禁用的）。
     */
    public List<AiModelConfig> findAll() {
        String sql = "SELECT * FROM ai_model_config ORDER BY sort_order ASC";
        try {
            return jdbc.query(sql, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Failed to query all models", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询默认模型。
     */
    public Optional<AiModelConfig> findDefault() {
        String sql = "SELECT * FROM ai_model_config WHERE is_default = true AND is_enabled = true LIMIT 1";
        try {
            AiModelConfig config = jdbc.queryForObject(sql, ROW_MAPPER);
            return Optional.ofNullable(config);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query default model", e);
            return Optional.empty();
        }
    }

    /**
     * 根据 model_code 查询。
     */
    public Optional<AiModelConfig> findByCode(String modelCode) {
        String sql = "SELECT * FROM ai_model_config WHERE model_code = ?";
        try {
            AiModelConfig config = jdbc.queryForObject(sql, ROW_MAPPER, modelCode);
            return Optional.ofNullable(config);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query model by code: {}", modelCode, e);
            return Optional.empty();
        }
    }
}
