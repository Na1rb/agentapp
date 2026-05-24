-- AI 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id SERIAL PRIMARY KEY,
    model_code VARCHAR(50) UNIQUE NOT NULL,
    provider VARCHAR(50) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT false,
    sort_order INT DEFAULT 0,
    temperature DECIMAL(3, 2) DEFAULT 0.7,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始数据（请替换为真实 Key）
INSERT INTO ai_model_config (model_code, provider, base_url, api_key, model_name, enabled, is_default, sort_order, description)
VALUES
('qwen-plus', 'aliyun', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-your-key', 'qwen-plus', true, true, 1, '阿里云通义千问（默认）'),
('deepseek-chat', 'deepseek', 'https://api.deepseek.com', 'sk-your-key', 'deepseek-chat', true, false, 2, 'DeepSeek V3（备用）');
