CREATE TABLE IF NOT EXISTS chat_web_search_source (
    id                  BIGSERIAL PRIMARY KEY,
    message_id          BIGINT NOT NULL,
    title               VARCHAR(500),
    source_url          VARCHAR(1000),
    published_at        TIMESTAMP,
    snippet             TEXT,
    rank_no             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_web_search_source_message FOREIGN KEY (message_id) REFERENCES chat_message (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_web_search_source_message_id
    ON chat_web_search_source (message_id);

CREATE INDEX IF NOT EXISTS idx_chat_web_search_source_message_rank
    ON chat_web_search_source (message_id, rank_no);
