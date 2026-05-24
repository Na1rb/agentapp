-- AI 智能体配置表
CREATE TABLE IF NOT EXISTS ai_agent (
    id SERIAL PRIMARY KEY,
    agent_id VARCHAR(64) UNIQUE NOT NULL,
    agent_name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    channel VARCHAR(32) DEFAULT 'agent',
    strategy VARCHAR(64) NOT NULL DEFAULT 'fixedAgentExecuteStrategy',
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_agent IS 'AI智能体配置表';
COMMENT ON COLUMN ai_agent.agent_id IS '智能体ID（唯一标识）';
COMMENT ON COLUMN ai_agent.strategy IS '执行策略(autoAgentExecuteStrategy|flowAgentExecuteStrategy|fixedAgentExecuteStrategy)';
COMMENT ON COLUMN ai_agent.status IS '状态(0:禁用,1:启用)';

-- 智能体-客户端流程关联表
CREATE TABLE IF NOT EXISTS ai_agent_flow_config (
    id SERIAL PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    client_name VARCHAR(100),
    client_type VARCHAR(32),
    sequence INT DEFAULT 0,
    step_prompt TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_agent_flow_config IS '智能体-客户端流程关联表';
COMMENT ON COLUMN ai_agent_flow_config.agent_id IS '智能体ID';
COMMENT ON COLUMN ai_agent_flow_config.client_id IS '客户端ID（引用ai_client_config外部系统）';
COMMENT ON COLUMN ai_agent_flow_config.sequence IS '执行顺序';
COMMENT ON COLUMN ai_agent_flow_config.step_prompt IS '执行步骤提示词';

CREATE INDEX IF NOT EXISTS idx_ai_agent_flow_config_agent_id ON ai_agent_flow_config(agent_id);

-- 初始数据：3 种策略各一个 Agent
INSERT INTO ai_agent (agent_id, agent_name, description, channel, strategy, status) VALUES
('agent001', '简单对话体（Fixed）', '固定客户端链式执行，适用于有明确步骤的任务', 'agent', 'fixedAgentExecuteStrategy', 1),
('agent002', '智能分析体（Auto）', '自动分析→执行→检查→总结，全自动完成复杂任务', 'agent', 'autoAgentExecuteStrategy', 1),
('agent003', '流程编排体（Flow）', '按步骤分析→规划→解析→执行，适合多工具协作', 'agent', 'flowAgentExecuteStrategy', 1);

-- Fixed 策略的流程配置（agent001：2 步链）
INSERT INTO ai_agent_flow_config (agent_id, client_id, client_name, client_type, sequence, step_prompt) VALUES
('agent001', 'default', '默认AI助手', 'chat', 0, '你是一个友好的AI助手，直接回答用户问题。'),
('agent001', 'refiner', '结果优化器', 'chat', 1, '你是一个专业的文本优化专家。对上面生成的内容进行润色和优化，使其更专业、更易读。');
