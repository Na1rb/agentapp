CREATE TABLE IF NOT EXISTS sys_ai_model (
    id VARCHAR(50) PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    description VARCHAR(255)
);

-- 插入默认数据 (注意：请根据实际情况替换 api_key)
INSERT INTO sys_ai_model (id, model_name, provider, base_url, api_key, is_active, description)
VALUES 
('qwen-plus-01', 'qwen-plus', 'alibaba', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'YOUR_QWEN_API_KEY_HERE', true, '通义千问 Plus'),
('deepseek-chat-01', 'deepseek-chat', 'deepseek', 'https://api.deepseek.com', 'YOUR_DEEPSEEK_API_KEY_HERE', true, '深度求索 Chat')
ON CONFLICT (id) DO NOTHING;
