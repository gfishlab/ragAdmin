-- 放开前台 APP 端首页通用会话的一人一会话限制，保留管理端 GENERAL 会话的单会话约束。

DROP INDEX IF EXISTS uk_chat_session_general_user_terminal;

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_admin_general_user_terminal
    ON chat_session (user_id, terminal_type, scene_type)
    WHERE scene_type = 'GENERAL' AND terminal_type = 'ADMIN';
