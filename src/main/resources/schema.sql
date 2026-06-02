-- ============================================================
-- 注意：vector_store 向量表由 Spring AI PgVectorStore 自动管理
-- （AiConfig.vectorStore() 中 initializeSchema=true），无需手动建表
-- ============================================================

-- 创建 vector 扩展（PgVectorStore 依赖）
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- MCP 工具配置表（Phase 1：工具配置存储与暴露）
-- ============================================================
CREATE TABLE IF NOT EXISTS mcp_tool_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tool_name        VARCHAR(64)  NOT NULL,
    display_name     VARCHAR(128) NOT NULL,
    description      VARCHAR(512),
    tool_type        VARCHAR(32)  NOT NULL DEFAULT 'HTTP_API',
    endpoint_url     VARCHAR(256),
    http_method      VARCHAR(8)   NOT NULL DEFAULT 'GET',
    request_template TEXT,
    response_parser  VARCHAR(32)  DEFAULT 'JSON_PATH',
    headers_config   TEXT,
    auth_config      TEXT,
    param_schema     TEXT,
    is_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mcp_tool_config_tool_name UNIQUE (tool_name)
);

-- 种子数据：天气查询工具
INSERT INTO mcp_tool_config (tool_name, display_name, description, tool_type, endpoint_url, http_method, param_schema)
VALUES
('weather_query', '天气查询', '查询指定城市当天的天气信息', 'HTTP_API',
 'https://api.openweathermap.org/data/2.5/weather', 'GET',
 '{"type":"object","properties":{"city":{"type":"string","description":"城市名称"}},"required":["city"]}')
ON CONFLICT (tool_name) DO NOTHING;

-- 种子数据：联网搜索工具
INSERT INTO mcp_tool_config (tool_name, display_name, description, tool_type, endpoint_url, http_method, param_schema)
VALUES
('web_search', '联网搜索', '通过搜索引擎检索互联网公开信息', 'HTTP_API',
 'https://api.duckduckgo.com/', 'GET',
 '{"type":"object","properties":{"q":{"type":"string","description":"搜索关键词"}},"required":["q"]}')
ON CONFLICT (tool_name) DO NOTHING;

-- ============================================================
-- AI 模型配置表（Phase 5: 数据库驱动的模型选择）
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_model_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_code       VARCHAR(64)  NOT NULL,
    model_name       VARCHAR(128) NOT NULL,
    provider         VARCHAR(32)  NOT NULL DEFAULT 'openai',
    base_url         VARCHAR(256),
    api_key          VARCHAR(256),
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_model_config_code UNIQUE (model_code)
);

-- 种子数据：对话模型
INSERT INTO ai_model_config (model_code, model_name, provider, is_default, is_enabled, sort_order)
VALUES
('qwen-plus',     '通义千问 Plus',    'openai',    TRUE,  TRUE, 1),
('deepseek-chat', 'DeepSeek Chat',    'openai',    FALSE, TRUE, 2),
('qwen-turbo',    '通义千问 Turbo',   'openai',    FALSE, TRUE, 3)
ON CONFLICT (model_code) DO NOTHING;

-- ============================================================
-- 用户表（简单的顺序分配 ID）
-- ============================================================
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 用户-会话关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS user_session (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id),
    chat_id     VARCHAR(64)  NOT NULL,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_session_chat_id UNIQUE (chat_id)
);

CREATE INDEX IF NOT EXISTS idx_user_session_user_id ON user_session(user_id);

-- ============================================================
-- Agent 配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_config (
    agent_id     VARCHAR(64) PRIMARY KEY,
    agent_name   VARCHAR(128) NOT NULL,
    description  TEXT,
    channel      VARCHAR(32)  NOT NULL DEFAULT 'chat',
    strategy     VARCHAR(32)  NOT NULL DEFAULT 'normal',
    status       INT          NOT NULL DEFAULT 1,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- Agent 流程步骤表（管道 Pipeline 模式）
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_flow_step (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id       VARCHAR(64)  NOT NULL REFERENCES agent_config(agent_id) ON DELETE CASCADE,
    step_id        VARCHAR(64)  NOT NULL,
    sequence       INT          NOT NULL DEFAULT 0,
    client_type    VARCHAR(32)  NOT NULL,
    client_name    VARCHAR(128),
    client_id      VARCHAR(128),
    step_prompt    TEXT,
    input_mapping  TEXT,                    -- JSON: {"q":"{{user_input}}"}
    output_key     VARCHAR(64),
    on_error       VARCHAR(32)  DEFAULT 'abort',
    fallback_step  VARCHAR(64),
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_step UNIQUE (agent_id, step_id)
);

-- ============================================================
-- Agent 执行日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_execution_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id       VARCHAR(64)  NOT NULL,
    session_id     VARCHAR(64)  NOT NULL,
    user_id        BIGINT,
    status         VARCHAR(16)  DEFAULT 'running',
    step_results   TEXT,                    -- JSON 数组
    total_tokens   INT,
    error_msg      TEXT,
    started_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    finished_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_exec_session ON agent_execution_log(session_id);
CREATE INDEX IF NOT EXISTS idx_agent_flow_step_agent ON agent_flow_step(agent_id, sequence);

-- 种子数据：示例 Agent
INSERT INTO agent_config (agent_id, agent_name, description, channel, strategy)
VALUES ('demo-research', '信息调研员', '联网搜索 → 分析 → 汇总', 'chat', 'normal')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_flow_step (agent_id, step_id, sequence, client_type, client_name, client_id, step_prompt, output_key, on_error)
VALUES
('demo-research', 'search', 1, 'MCP_TOOL', '联网搜索', 'web_search',
 '根据用户问题搜索互联网。用户输入: {{user_input}}', 'search_result', 'retry'),
('demo-research', 'analyze', 2, 'LLM', '分析引擎', 'qwen-plus',
 '基于以下搜索结果进行分析：\n{{search.output}}\n\n请提炼关键信息并给出结论。', 'analysis', 'abort')
ON CONFLICT (agent_id, step_id) DO NOTHING;
