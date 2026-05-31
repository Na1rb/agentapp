package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.McpToolConfig;
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
 * MCP 工具配置数据访问层。
 * <p>
 * 基于 {@link JdbcTemplate}，不使用 JPA，与项目现有风格保持一致。
 */
@Repository
public class McpToolConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(McpToolConfigRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public McpToolConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== RowMapper ====================

    private static final RowMapper<McpToolConfig> ROW_MAPPER = new McpToolConfigRowMapper();

    private static class McpToolConfigRowMapper implements RowMapper<McpToolConfig> {
        @Override
        public McpToolConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpToolConfig config = new McpToolConfig();
            config.setId(rs.getLong("id"));
            config.setToolName(rs.getString("tool_name"));
            config.setDisplayName(rs.getString("display_name"));
            config.setDescription(rs.getString("description"));
            config.setToolType(rs.getString("tool_type"));
            config.setEndpointUrl(rs.getString("endpoint_url"));
            config.setHttpMethod(rs.getString("http_method"));
            config.setRequestTemplate(rs.getString("request_template"));
            config.setResponseParser(rs.getString("response_parser"));
            config.setHeadersConfig(rs.getString("headers_config"));
            config.setAuthConfig(rs.getString("auth_config"));
            config.setParamSchema(rs.getString("param_schema"));
            config.setIsEnabled(rs.getBoolean("is_enabled"));
            config.setCreatedAt(rs.getTimestamp("created_at") != null
                    ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            config.setUpdatedAt(rs.getTimestamp("updated_at") != null
                    ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
            return config;
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 查询所有启用的工具配置。
     */
    public List<McpToolConfig> findAllEnabled() {
        String sql = "SELECT * FROM mcp_tool_config WHERE is_enabled = true ORDER BY id ASC";
        try {
            return jdbcTemplate.query(sql, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Failed to query enabled MCP tool configs", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询所有工具配置（含禁用的）。
     */
    public List<McpToolConfig> findAll() {
        String sql = "SELECT * FROM mcp_tool_config ORDER BY id ASC";
        try {
            return jdbcTemplate.query(sql, ROW_MAPPER);
        } catch (Exception e) {
            log.error("Failed to query all MCP tool configs", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据工具名称查询配置。
     */
    public Optional<McpToolConfig> findByToolName(String toolName) {
        String sql = "SELECT * FROM mcp_tool_config WHERE tool_name = ?";
        try {
            McpToolConfig config = jdbcTemplate.queryForObject(sql, ROW_MAPPER, toolName);
            return Optional.ofNullable(config);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query MCP tool config by name: {}", toolName, e);
            return Optional.empty();
        }
    }

    /**
     * 根据工具名称列表批量查询启用的工具配置。
     * <p>
     * 用于 Chat 请求时根据前端传入的 toolIds 动态加载工具。
     * 已禁用的工具会被自动过滤。
     *
     * @param toolNames 工具名称列表
     * @return 启用的工具配置列表（保持传入顺序）
     */
    public List<McpToolConfig> findByToolNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptyList();
        }
        // 动态生成 IN 子句占位符
        String placeholders = String.join(",", Collections.nCopies(toolNames.size(), "?"));
        String sql = "SELECT * FROM mcp_tool_config WHERE tool_name IN (" + placeholders
                + ") AND is_enabled = true ORDER BY id ASC";
        try {
            return jdbcTemplate.query(sql, ROW_MAPPER, toolNames.toArray());
        } catch (Exception e) {
            log.error("Failed to query MCP tool configs by names: {}", toolNames, e);
            return Collections.emptyList();
        }
    }
}
