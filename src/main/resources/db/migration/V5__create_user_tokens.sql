-- Purpose: create user_tokens table, one-time email tokens for activation and password reset (BR-AUTH-11)
-- Author: MinhTien | Created: 2026-09-26
CREATE TABLE user_tokens (
        id             uuid        NOT NULL DEFAULT gen_random_uuid()
    ,   user_id        uuid        NOT NULL
    ,   type           varchar(20) NOT NULL
    ,   token_hash     char(64)    NOT NULL
    ,   expires_at     timestamptz NOT NULL
    ,   used_at        timestamptz
    ,   invalidated_at timestamptz
    ,   created_by     uuid
    ,   request_ip     varchar(45)
    ,   created_at     timestamptz NOT NULL DEFAULT now()

    CONSTRAINT pk_user_tokens 
        PRIMARY KEY (id),
    CONSTRAINT uq_user_tokens_hash 
        UNIQUE (token_hash),
    CONSTRAINT fk_user_tokens_user 
        FOREIGN KEY (user_id) 
        REFERENCES users (id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_user_tokens_created_by 
        FOREIGN KEY (created_by) 
        REFERENCES users (id) 
        ON DELETE SET NULL,
    CONSTRAINT ck_user_tokens_type 
        CHECK (type IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_user_tokens_hash_format 
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_tokens_expiry 
        CHECK (expires_at > created_at),
    CONSTRAINT ck_user_tokens_single_end 
        CHECK (NOT (used_at IS NOT NULL AND invalidated_at IS NOT NULL))
);

CREATE INDEX ix_user_tokens_active ON user_tokens (user_id, type) WHERE used_at IS NULL AND invalidated_at IS NULL;
CREATE INDEX ix_user_tokens_expires ON user_tokens (expires_at);
