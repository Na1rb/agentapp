-- 1. 创建 AI 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id SERIAL PRIMARY KEY,
    model_code VARCHAR(50) UNIQUE NOT NULL, -- 前端传的标识，如 'qwen-max', 'deepseek-v3'
    provider VARCHAR(50) NOT NULL,          -- 厂商，如 'aliyun', 'deepseek', 'openai'
    base_url VARCHAR(255) NOT NULL,         -- API 地址
    api_key VARCHAR(255) NOT NULL,          -- 密钥
    model_name VARCHAR(100) NOT NULL,       -- 实际发给大模型的名称参数
    enabled BOOLEAN DEFAULT true,           -- 是否启用
    is_default BOOLEAN DEFAULT false,       -- 是否为系统默认模型
    sort_order INT DEFAULT 0,               -- 排序字段
    temperature DECIMAL(3, 2) DEFAULT 0.7,  -- 温度参数
    description VARCHAR(255),               -- 描述，比如 "阿里云 Qwen Max (最强)"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 清空旧数据以防止唯一键冲突
TRUNCATE TABLE ai_model_config RESTART IDENTITY CASCADE;

-- 2. 插入丰富的模型初始化数据 (请务必在运行后，将表里的 sk-xxx 替换为你真实的 API KEY)
INSERT INTO ai_model_config 
(model_code, provider, base_url, api_key, model_name, enabled, is_default, sort_order, temperature, description)
VALUES 
-- 阿里云 (主推系列)
('qwen-max', 'aliyun', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-your-qwen-key-here', 'qwen-max', true, true, 10, 0.7, '通义千问 Max (综合能力最强，推荐)'),
('qwen-plus', 'aliyun', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-your-qwen-key-here', 'qwen-plus', true, false, 20, 0.7, '通义千问 Plus (性能与速度均衡)'),
('qwen-turbo', 'aliyun', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-your-qwen-key-here', 'qwen-turbo', true, false, 30, 0.7, '通义千问 Turbo (极速响应)'),

-- DeepSeek (性价比/编程极客)
('deepseek-chat', 'deepseek', 'https://api.deepseek.com', 'sk-your-deepseek-key-here', 'deepseek-chat', true, false, 40, 0.7, 'DeepSeek V3 (国内超高性价比，逻辑极佳)'),
('deepseek-reasoner', 'deepseek', 'https://api.deepseek.com', 'sk-your-deepseek-key-here', 'deepseek-reasoner', true, false, 50, 0.3, 'DeepSeek R1 (深度思考模型，擅长数理逻辑)'),

-- Kimi (月之暗面)
('moonshot-v1-8k', 'moonshot', 'https://api.moonshot.cn/v1', 'sk-your-kimi-key-here', 'moonshot-v1-8k', true, false, 60, 0.7, 'Kimi 8K (擅长长文本总结)'),
('moonshot-v1-32k', 'moonshot', 'https://api.moonshot.cn/v1', 'sk-your-kimi-key-here', 'moonshot-v1-32k', true, false, 70, 0.7, 'Kimi 32K (超长上下文支持)'),

-- 智谱 AI
('glm-4', 'zhipu', 'https://open.bigmodel.cn/api/paas/v4', 'sk-your-zhipu-key-here', 'glm-4', false, false, 80, 0.7, '智谱 GLM-4 (当前未启用)'),

-- OpenAI (作为兜底或海外线路参考)
('gpt-4o', 'openai', 'https://api.openai.com/v1', 'sk-your-openai-key-here', 'gpt-4o', false, false, 90, 0.7, 'GPT-4o (海外旗舰，当前未启用)');
