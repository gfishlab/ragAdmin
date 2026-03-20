CREATE TABLE IF NOT EXISTS chat_session_memory_summary (
    id                          BIGSERIAL PRIMARY KEY,
    session_id                  BIGINT NOT NULL,
    conversation_id             VARCHAR(128) NOT NULL,
    summary_text                TEXT NOT NULL,
    summary_version             INTEGER NOT NULL DEFAULT 1,
    compressed_message_count    INTEGER NOT NULL DEFAULT 0,
    compressed_until_message_id BIGINT,
    last_source_message_id      BIGINT,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_memory_summary_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_memory_summary_session_id
    ON chat_session_memory_summary (session_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_memory_summary_conversation_id
    ON chat_session_memory_summary (conversation_id);
