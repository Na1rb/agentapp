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
