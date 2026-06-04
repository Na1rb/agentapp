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
('qwen-plus',       '通义千问 Plus',      'openai',    TRUE,  TRUE, 1),
('qwen3.5-flash',   'Qwen3.5 Flash',     'openai',    FALSE, TRUE, 2),
('qwen3.5-plus',    'Qwen3.5 Plus',      'openai',    FALSE, TRUE, 3)
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
    max_round    INT          NOT NULL DEFAULT 5,
    max_pace     INT          NOT NULL DEFAULT 10,
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
    retry_limit    INT          NOT NULL DEFAULT 2,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_step UNIQUE (agent_id, step_id)
);

ALTER TABLE agent_config ADD COLUMN IF NOT EXISTS max_round INT NOT NULL DEFAULT 5;
ALTER TABLE agent_config ADD COLUMN IF NOT EXISTS max_pace INT NOT NULL DEFAULT 10;

ALTER TABLE agent_flow_step ADD COLUMN IF NOT EXISTS retry_limit INT NOT NULL DEFAULT 2;

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

-- Agent strategy node outputs. Each Loop/ReAct role invocation creates one row.
CREATE TABLE IF NOT EXISTS agent_node_result (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    log_id         BIGINT       NOT NULL REFERENCES agent_execution_log(id) ON DELETE CASCADE,
    round          INT          NOT NULL DEFAULT 1,
    role           VARCHAR(32)  NOT NULL,
    prompt         TEXT,
    output         TEXT,
    duration_ms    INT,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_node_result_log ON agent_node_result(log_id, round, id);

-- 种子数据：Loop 策略 Agent（多轮迭代优化）
INSERT INTO agent_config (agent_id, agent_name, description, channel, strategy, status, max_round, max_pace)
VALUES ('loop-writer', '多轮写作助手',
        '适合需要多轮迭代优化的写作任务，如深度分析报告、方案策划等',
        'agent', 'loop', 1, 5, 10)
ON CONFLICT (agent_id) DO NOTHING;

-- 种子数据：ReAct 策略 Agent（实时感知决策）
INSERT INTO agent_config (agent_id, agent_name, description, channel, strategy, status, max_round, max_pace)
VALUES ('react-researcher', '实时调研助手',
        '适合需要实时感知外部信息的调研任务，如竞品动态监控、实时数据采集等',
        'agent', 'react', 1, 5, 10)
ON CONFLICT (agent_id) DO NOTHING;

-- 种子数据：示例 Agent
INSERT INTO agent_config (agent_id, agent_name, description, channel, strategy)
VALUES ('demo-research', '信息调研员', '联网搜索 → 分析 → 汇总', 'chat', 'normal')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_flow_step (agent_id, step_id, sequence, client_type, client_name, client_id, step_prompt, output_key, on_error)
VALUES
('demo-research', 'search', 1, 'MCP_TOOL', '联网搜索', 'web_search',
 '根据用户问题搜索互联网。用户输入: {{user_input}}', 'search_result', 'retry'),
('demo-research', 'analyze', 2, 'LLM', '分析引擎', 'qwen3.5-plus',
 '基于以下搜索结果进行分析：\n{{search.output}}\n\n请提炼关键信息并给出结论。', 'analysis', 'abort')
ON CONFLICT (agent_id, step_id) DO NOTHING;

-- ============================================================
-- 工作流引擎 — 流程模板（带反馈环的有向图）
-- 说明：流程由 wf_node（节点）+ wf_edge（边 + 条件）组成，
--       支持并行分支、条件回退、人工确认等高级编排。
-- ============================================================

-- 流程模板定义
CREATE TABLE IF NOT EXISTS wf_definition (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    version         INT          NOT NULL DEFAULT 1,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 流程节点
CREATE TABLE IF NOT EXISTS wf_node (
    id              VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64)  NOT NULL REFERENCES wf_definition(id) ON DELETE CASCADE,
    node_type       VARCHAR(32)  NOT NULL,
    -- ANALYSIS | DECOMPOSE | EXECUTE | CHECK | SYNTHESIZE
    -- | PARALLEL_SPLIT | PARALLEL_MERGE | CONDITION | HUMAN_CONFIRM
    name            VARCHAR(128),
    config_json     TEXT,         -- 节点配置（检查标准、工具列表、子流程引用等）
    retry_limit     INT          NOT NULL DEFAULT 3,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_node_workflow ON wf_node(workflow_id, sort_order);

-- 流程边（转移条件）
CREATE TABLE IF NOT EXISTS wf_edge (
    id               VARCHAR(64) PRIMARY KEY,
    workflow_id      VARCHAR(64)  NOT NULL REFERENCES wf_definition(id) ON DELETE CASCADE,
    source_node_id   VARCHAR(64)  NOT NULL REFERENCES wf_node(id) ON DELETE CASCADE,
    target_node_id   VARCHAR(64)  NOT NULL REFERENCES wf_node(id) ON DELETE CASCADE,
    condition_expr   VARCHAR(256),  -- "check.passed == true" / "check.passed == false" / NULL=always
    sort_order       INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wf_edge_workflow ON wf_edge(workflow_id);

-- 工作流执行记录（持久化，支持断点续跑）
CREATE TABLE IF NOT EXISTS wf_execution (
    id               VARCHAR(64) PRIMARY KEY,
    workflow_id      VARCHAR(64)  REFERENCES wf_definition(id),
    session_id       VARCHAR(64)  NOT NULL,
    prompt           TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'running',
    -- running | paused | completing | completed | failed
    current_node_id  VARCHAR(64),
    retry_count      INT          NOT NULL DEFAULT 0,
    error_message    TEXT,
    started_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    finished_at      TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_exec_session ON wf_execution(session_id);

-- 节点执行结果（每个节点一次执行一条记录）
CREATE TABLE IF NOT EXISTS wf_node_result (
    id               VARCHAR(64) PRIMARY KEY,
    execution_id     VARCHAR(64)  NOT NULL REFERENCES wf_execution(id) ON DELETE CASCADE,
    node_id          VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'pending',
    -- pending | running | completed | failed | skipped
    input_text       TEXT,
    output_text      TEXT,
    error_message    TEXT,
    check_result     TEXT,         -- 结构化 JSON: {"passed":true/false, "reasons":[...], "score":0.85}
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_node_result_exec ON wf_node_result(execution_id);

-- ============================================================
-- 默认工作流模板种子数据
-- 实现：ANALYSIS → DECOMPOSE → PARALLEL_SPLIT → EXECUTE
--       → PARALLEL_MERGE → CHECK → SYNTHESIZE
-- 带检查失败回退到 ANALYSIS 的反馈环
-- ============================================================
INSERT INTO wf_definition (id, name, description)
VALUES ('default-step-check', '分步编排（默认）', '分析→拆解→并行执行→检查→汇总，检查不通过自动回退重试')
ON CONFLICT (id) DO NOTHING;

-- 节点
INSERT INTO wf_node (id, workflow_id, node_type, name, retry_limit, sort_order) VALUES
('n1', 'default-step-check', 'ANALYSIS',       '需求分析',   3, 1),
('n2', 'default-step-check', 'DECOMPOSE',      '任务拆解',   3, 2),
('n3', 'default-step-check', 'PARALLEL_SPLIT', '并行执行',   3, 3),
('n4', 'default-step-check', 'EXECUTE',        '子任务执行', 3, 4),
('n5', 'default-step-check', 'PARALLEL_MERGE', '结果归集',   3, 5),
('n6', 'default-step-check', 'CHECK',          '质量检查',   3, 6),
('n7', 'default-step-check', 'SYNTHESIZE',     '汇总回答',   3, 7)
ON CONFLICT (id) DO NOTHING;

-- 边（含条件分支）
-- 正常路径
INSERT INTO wf_edge (id, workflow_id, source_node_id, target_node_id, condition_expr, sort_order) VALUES
('e0', 'default-step-check', 'n1', 'n7', 'analysis.requiresWorkflow == false', 1),
('e1', 'default-step-check', 'n1', 'n2', 'analysis.requiresWorkflow == true', 2),
('e2', 'default-step-check', 'n2', 'n3', NULL, 2),
('e3', 'default-step-check', 'n3', 'n4', NULL, 3),
('e4', 'default-step-check', 'n4', 'n5', NULL, 4),
('e5', 'default-step-check', 'n5', 'n6', NULL, 5),
-- 检查通过 → 汇总
('e6', 'default-step-check', 'n6', 'n7', 'check.passed == true', 6),
-- 检查不通过且未超限 → 回退到分析（带错误上下文）
('e7', 'default-step-check', 'n6', 'n1', 'check.passed == false && retryCount < 3', 7),
-- 检查不通过且超限 → 强制汇总（给出不完整但有价值的回答）
('e8', 'default-step-check', 'n6', 'n7', 'check.passed == false && retryCount >= 3', 8)
ON CONFLICT (id) DO NOTHING;

UPDATE wf_edge
SET condition_expr = 'analysis.requiresWorkflow == true', sort_order = 2
WHERE id = 'e1' AND workflow_id = 'default-step-check';
