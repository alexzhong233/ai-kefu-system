-- Create database (run this separately if needed)
-- CREATE DATABASE aikefu;

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- User table for managing different users
CREATE TABLE IF NOT EXISTS t_user (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL UNIQUE,
    user_name VARCHAR(200),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- RAG Document table
CREATE TABLE IF NOT EXISTS t_rag_document (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(100) NOT NULL UNIQUE,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(50),
    file_size BIGINT,
    file_path VARCHAR(1000),
    content_text TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    chunk_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- RAG Chunk table with vector embedding
CREATE TABLE IF NOT EXISTS t_rag_chunk (
    id BIGSERIAL PRIMARY KEY,
    chunk_id VARCHAR(100) NOT NULL UNIQUE,
    document_id VARCHAR(100) NOT NULL,
    parent_chunk_id VARCHAR(100),          -- 父级 chunk ID，用于 Parent-Child 分块策略
    content TEXT NOT NULL,
    chunk_index INTEGER,
    metadata JSONB,
    embedding VECTOR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (document_id) REFERENCES t_rag_document(document_id) ON DELETE CASCADE
);

-- Index for vector similarity search
CREATE INDEX IF NOT EXISTS idx_rag_chunk_embedding ON t_rag_chunk USING ivfflat (embedding vector_cosine_ops);

-- Conversation table
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    title VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    message_count INTEGER DEFAULT 0,
    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES t_user(user_id) ON DELETE CASCADE
);

-- Conversation Message table
CREATE TABLE IF NOT EXISTS t_conversation_message (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(100) NOT NULL UNIQUE,
    conversation_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (conversation_id) REFERENCES t_conversation(conversation_id) ON DELETE CASCADE
);

-- Memory compression history table
CREATE TABLE IF NOT EXISTS t_memory_compression (
    id BIGSERIAL PRIMARY KEY,
    compression_id VARCHAR(100) NOT NULL UNIQUE,
    conversation_id VARCHAR(100) NOT NULL,
    original_message_count INTEGER,
    compressed_message_count INTEGER,
    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (conversation_id) REFERENCES t_conversation(conversation_id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_user_user_id ON t_user(user_id);
CREATE INDEX IF NOT EXISTS idx_rag_document_doc_id ON t_rag_document(document_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_doc_id ON t_rag_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_parent_id ON t_rag_chunk(parent_chunk_id) WHERE parent_chunk_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_conversation_conv_id ON t_conversation(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON t_conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_message_conv_id ON t_conversation_message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_created_at ON t_conversation_message(created_at);

-- 为已有数据库添加 summary 列（新建表已包含，此语句兼容旧库）
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS last_summarized_message_count INTEGER DEFAULT 0;

-- 为已有数据库添加 parent_chunk_id 列（Parent-Child 分块策略）
ALTER TABLE t_rag_chunk ADD COLUMN IF NOT EXISTS parent_chunk_id VARCHAR(100);

-- Insert sample users
INSERT INTO t_user (user_id, user_name, description) VALUES 
('user1', 'User 1', 'First test user'),
('user2', 'User 2', 'Second test user')
ON CONFLICT (user_id) DO NOTHING;

-- Add column comments
COMMENT ON TABLE t_user IS 'User table';
COMMENT ON COLUMN t_user.user_id IS 'User identifier';
COMMENT ON COLUMN t_user.user_name IS 'User name';
COMMENT ON COLUMN t_user.description IS 'User description';

COMMENT ON TABLE t_rag_document IS 'RAG Document table';
COMMENT ON COLUMN t_rag_document.document_id IS 'Document identifier';
COMMENT ON COLUMN t_rag_document.file_name IS 'File name';
COMMENT ON COLUMN t_rag_document.file_type IS 'File type (txt, pdf, md, etc.)';
COMMENT ON COLUMN t_rag_document.file_size IS 'File size in bytes';
COMMENT ON COLUMN t_rag_document.file_path IS 'File storage path';
COMMENT ON COLUMN t_rag_document.content_text IS 'Extracted text content';
COMMENT ON COLUMN t_rag_document.status IS 'Status: pending, processing, completed, failed';
COMMENT ON COLUMN t_rag_document.chunk_count IS 'Number of chunks';

COMMENT ON TABLE t_rag_chunk IS 'RAG Chunk table';
COMMENT ON COLUMN t_rag_chunk.chunk_id IS 'Chunk identifier';
COMMENT ON COLUMN t_rag_chunk.document_id IS 'Reference to document';
COMMENT ON COLUMN t_rag_chunk.content IS 'Chunk content text';
COMMENT ON COLUMN t_rag_chunk.chunk_index IS 'Chunk sequence number';
COMMENT ON COLUMN t_rag_chunk.metadata IS 'Additional metadata';
COMMENT ON COLUMN t_rag_chunk.embedding IS 'Vector embedding (text-embedding-v3, 1024 dimensions)';

COMMENT ON TABLE t_conversation IS 'Conversation table';
COMMENT ON COLUMN t_conversation.conversation_id IS 'Conversation identifier';
COMMENT ON COLUMN t_conversation.user_id IS 'User identifier';
COMMENT ON COLUMN t_conversation.title IS 'Conversation title';
COMMENT ON COLUMN t_conversation.status IS 'Status: active, archived';
COMMENT ON COLUMN t_conversation.message_count IS 'Number of messages';

COMMENT ON TABLE t_conversation_message IS 'Conversation Message table';
COMMENT ON COLUMN t_conversation_message.message_id IS 'Message identifier';
COMMENT ON COLUMN t_conversation_message.conversation_id IS 'Conversation identifier';
COMMENT ON COLUMN t_conversation_message.role IS 'Role: user, assistant, system';
COMMENT ON COLUMN t_conversation_message.content IS 'Message content';
COMMENT ON COLUMN t_conversation_message.metadata IS 'Additional metadata (model, tokens, etc.)';

COMMENT ON TABLE t_memory_compression IS 'Memory compression history table';
COMMENT ON COLUMN t_memory_compression.compression_id IS 'Compression identifier';
COMMENT ON COLUMN t_memory_compression.conversation_id IS 'Conversation identifier';
COMMENT ON COLUMN t_memory_compression.original_message_count IS 'Original message count';
COMMENT ON COLUMN t_memory_compression.compressed_message_count IS 'Compressed message count';
COMMENT ON COLUMN t_memory_compression.summary IS 'Compression summary';
