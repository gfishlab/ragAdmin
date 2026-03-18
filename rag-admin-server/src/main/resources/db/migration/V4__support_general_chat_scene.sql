-- 聊天会话拆分为首页通用会话和知识库内会话两种场景。

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS scene_type VARCHAR(32);

UPDATE chat_session
SET scene_type = 'KNOWLEDGE_BASE'
WHERE scene_type IS NULL;

ALTER TABLE chat_session
    ALTER COLUMN scene_type SET DEFAULT 'KNOWLEDGE_BASE';

ALTER TABLE chat_session
    ALTER COLUMN scene_type SET NOT NULL;

ALTER TABLE chat_session
    ALTER COLUMN kb_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_session_user_scene
    ON chat_session (user_id, scene_type);

-- 首页通用会话当前按“每用户一个默认会话”收敛，避免会话上下文和 chatId 规则冲突。
CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_general_user
    ON chat_session (user_id, scene_type)
    WHERE scene_type = 'GENERAL';
