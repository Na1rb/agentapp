-- 创建 vector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建文档嵌入表（阿里云 text-embedding-v3 维度为 1024）
CREATE TABLE IF NOT EXISTS document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    embedding vector(1024),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建 HNSW 索引用于向量搜索
CREATE INDEX IF NOT EXISTS idx_document_embeddings_embedding
ON document_embeddings USING hnsw (embedding vector_cosine_ops);

-- 创建元数据索引用于快速查询
CREATE INDEX IF NOT EXISTS idx_document_embeddings_metadata
ON document_embeddings USING gin (metadata);

-- ============================================================
-- MCP 工具配置表（Phase 1：工具配置存储与暴露）
-- ============================================================
CREATE TABLE IF NOT EXISTS mcp_tool_config (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
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
