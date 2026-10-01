-- Purpose: create refresh_tokens table, the rotation chain of each session (UC-AUTH-01, 02; section 7.3)
-- Author: MinhTien | Created: 2026-09-26
CREATE TABLE refresh_tokens (
        id             uuid        NOT NULL DEFAULT gen_random_uuid()
    ,   session_id     uuid        NOT NULL
    ,   token_hash     char(64)    NOT NULL
    ,   expires_at     timestamptz NOT NULL
    ,   used_at        timestamptz
    ,   replaced_by_id uuid
    ,   created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_tokens 
        PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash 
        UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session 
        FOREIGN KEY (session_id) 
        REFERENCES user_sessions (id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by 
        FOREIGN KEY (replaced_by_id) 
        REFERENCES refresh_tokens (id)
        ON DELETE SET NULL,
    CONSTRAINT ck_refresh_tokens_hash_format 
        CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
