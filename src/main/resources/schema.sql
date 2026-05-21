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
