ALTER TABLE kb_knowledge_base
    ADD CONSTRAINT chk_kb_embedding_model_required
    CHECK (embedding_model_id IS NOT NULL) NOT VALID;

COMMENT ON COLUMN kb_knowledge_base.embedding_model_id IS '知识库绑定的向量模型 ID，创建与更新时必须显式指定';
COMMENT ON COLUMN kb_knowledge_base.chat_model_id IS '已废弃：仅保留兼容历史数据，运行时不再作为知识库聊天模型来源';
