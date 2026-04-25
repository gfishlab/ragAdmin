CREATE TABLE ai_prompt_template (
    id              BIGSERIAL PRIMARY KEY,
    template_code   VARCHAR(100) NOT NULL,
    template_name   VARCHAR(100) NOT NULL,
    capability_type VARCHAR(64),
    prompt_content  TEXT NOT NULL,
    version_no      INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    description     VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_code_version UNIQUE (template_code, version_no)
);
