CREATE TABLE ai_mcp_execute_log (
    id BIGSERIAL PRIMARY KEY, -- PostgreSQL syntax, adjust to AUTO_INCREMENT if using MySQL
    session_id VARCHAR(64),
    server_name VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments TEXT,
    duration_ms BIGINT,
    status SMALLINT NOT NULL, -- 1-success, 0-failed
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_mcp_execute_log IS 'MCP工具调用审计日志表';
COMMENT ON COLUMN ai_mcp_execute_log.id IS '主键';
COMMENT ON COLUMN ai_mcp_execute_log.session_id IS '会话ID';
COMMENT ON COLUMN ai_mcp_execute_log.server_name IS 'MCP服务名称';
COMMENT ON COLUMN ai_mcp_execute_log.tool_name IS '工具名称';
COMMENT ON COLUMN ai_mcp_execute_log.arguments IS '请求参数(JSON)';
COMMENT ON COLUMN ai_mcp_execute_log.duration_ms IS '执行耗时(毫秒)';
COMMENT ON COLUMN ai_mcp_execute_log.status IS '状态: 1-成功, 0-失败';
COMMENT ON COLUMN ai_mcp_execute_log.error_message IS '失败时的错误信息';
COMMENT ON COLUMN ai_mcp_execute_log.create_time IS '调用时间';
