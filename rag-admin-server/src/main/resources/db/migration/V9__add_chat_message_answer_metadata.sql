ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS answer_confidence VARCHAR(16),
    ADD COLUMN IF NOT EXISTS has_knowledge_base_evidence BOOLEAN,
    ADD COLUMN IF NOT EXISTS need_follow_up BOOLEAN;
