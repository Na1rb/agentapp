package com.nairb.ai130.infrastructure.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.types.vo.McpToolConfigVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具配置 Repository —— 基于 JdbcTemplate 操作 ai_client_tool_mcp 表。
 */
@Repository
public class McpToolRepository {

    private static final Logger log = LoggerFactory.getLogger(McpToolRepository.class);
    private static final String TABLE = "ai_client_tool_mcp";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpToolRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 增删改 ====================

    public boolean insert(McpToolConfigVO vo) {
        String sql = "INSERT INTO " + TABLE +
                " (mcp_id, mcp_name, transport_type, transport_config, request_timeout, status, create_time, update_time)" +
                " VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        int rows = jdbc.update(sql,
                vo.getMcpId(), vo.getMcpName(), vo.getTransportType(),
                vo.getTransportConfig() != null ? vo.getTransportConfig() : "{}",
                vo.getRequestTimeout() != null ? vo.getRequestTimeout() : 5,
                vo.getStatus() != null ? vo.getStatus() : 1,
                now, now);
        return rows > 0;
    }

    public boolean updateByMcpId(McpToolConfigVO vo) {
        StringBuilder sql = new StringBuilder("UPDATE " + TABLE + " SET update_time = ?");
        // 动态拼接，只更新非 null 字段
        if (vo.getMcpName() != null)          sql.append(", mcp_name = ?");
        if (vo.getTransportType() != null)    sql.append(", transport_type = ?");
        if (vo.getTransportConfig() != null)  sql.append(", transport_config = ?::jsonb");
        if (vo.getRequestTimeout() != null)   sql.append(", request_timeout = ?");
        if (vo.getStatus() != null)           sql.append(", status = ?");
        sql.append(" WHERE mcp_id = ?");

        // 构建参数列表
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(LocalDateTime.now());
        if (vo.getMcpName() != null)          params.add(vo.getMcpName());
        if (vo.getTransportType() != null)    params.add(vo.getTransportType());
        if (vo.getTransportConfig() != null)  params.add(vo.getTransportConfig());
        if (vo.getRequestTimeout() != null)   params.add(vo.getRequestTimeout());
        if (vo.getStatus() != null)           params.add(vo.getStatus());
        params.add(vo.getMcpId());

        return jdbc.update(sql.toString(), params.toArray()) > 0;
    }

    public boolean deleteByMcpId(String mcpId) {
        return jdbc.update("DELETE FROM " + TABLE + " WHERE mcp_id = ?", mcpId) > 0;
    }

    // ==================== 查询 ====================

    public McpToolConfigVO findByMcpId(String mcpId) {
        List<McpToolConfigVO> list = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE mcp_id = ?",
                new McpToolRowMapper(), mcpId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<McpToolConfigVO> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY create_time DESC", new McpToolRowMapper());
    }

    /** 查询所有启用（status=1）的 MCP 配置 */
    public List<McpToolConfigVO> findEnabled() {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE status = 1 ORDER BY create_time DESC",
                new McpToolRowMapper());
    }

    public boolean existsByMcpId(String mcpId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE mcp_id = ?", Integer.class, mcpId);
        return count != null && count > 0;
    }

    // ==================== RowMapper ====================

    private class McpToolRowMapper implements RowMapper<McpToolConfigVO> {
        @Override
        public McpToolConfigVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpToolConfigVO vo = new McpToolConfigVO();
            vo.setMcpId(rs.getString("mcp_id"));
            vo.setMcpName(rs.getString("mcp_name"));
            vo.setTransportType(rs.getString("transport_type"));
            vo.setTransportConfig(rs.getString("transport_config"));
            vo.setRequestTimeout(rs.getInt("request_timeout"));
            vo.setStatus(rs.getInt("status"));

            // 时间字段
            Timestamp ct = rs.getTimestamp("create_time");
            if (ct != null) vo.setCreateTime(ct.toLocalDateTime());
            Timestamp ut = rs.getTimestamp("update_time");
            if (ut != null) vo.setUpdateTime(ut.toLocalDateTime());

            // 解析 transport_config JSON → 结构化对象
            String raw = rs.getString("transport_config");
            if (raw != null && !raw.isBlank()) {
                try {
                    String type = rs.getString("transport_type");
                    if ("sse".equals(type)) {
                        vo.setTransportConfigSse(objectMapper.readValue(raw, McpToolConfigVO.TransportConfigSse.class));
                    } else if ("stdio".equals(type)) {
                        Map<String, McpToolConfigVO.TransportConfigStdio.Stdio> stdioMap = objectMapper.readValue(
                                raw, new TypeReference<Map<String, McpToolConfigVO.TransportConfigStdio.Stdio>>() {});
                        McpToolConfigVO.TransportConfigStdio stdio = new McpToolConfigVO.TransportConfigStdio();
                        stdio.setStdio(stdioMap);
                        vo.setTransportConfigStdio(stdio);
                    }
                } catch (Exception e) {
                    log.warn("解析 transport_config JSON 失败: mcpId={}", vo.getMcpId(), e);
                }
            }

            return vo;
        }
    }
}
