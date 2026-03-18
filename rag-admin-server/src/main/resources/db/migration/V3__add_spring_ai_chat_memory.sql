-- 按 Spring AI 官方 JDBC Chat Memory PostgreSQL schema 落地，
-- 统一交由 Flyway 管理，避免与应用启动自动建表产生职责冲突。

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(128) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id_timestamp
    ON spring_ai_chat_memory (conversation_id, "timestamp");
