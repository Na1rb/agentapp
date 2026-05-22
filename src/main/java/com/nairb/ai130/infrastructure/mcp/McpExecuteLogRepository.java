package com.nairb.ai130.infrastructure.mcp;

import com.nairb.ai130.domain.mcp.McpExecuteLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class McpExecuteLogRepository {

    private static final Logger log = LoggerFactory.getLogger(McpExecuteLogRepository.class);
    private final JdbcTemplate jdbc;

    public McpExecuteLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(McpExecuteLog logEntity) {
        String sql = "INSERT INTO ai_mcp_execute_log (session_id, server_name, tool_name, arguments, duration_ms, status, error_message, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbc.update(sql,
                    logEntity.getSessionId(),
                    logEntity.getServerName(),
                    logEntity.getToolName(),
                    logEntity.getArguments(),
                    logEntity.getDurationMs(),
                    logEntity.getStatus(),
                    logEntity.getErrorMessage(),
                    logEntity.getCreateTime() != null ? logEntity.getCreateTime() : LocalDateTime.now());
        } catch (Exception e) {
            log.error("保存 MCP 执行日志失败: {}", e.getMessage(), e);
        }
    }
}
