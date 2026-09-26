-- Purpose: create user_sessions table, one row per login on a device; id is the "sid" claim (UC-AUTH-01, 02, 03, 09)
-- Author: MinhTien | Created: 2026-09-26
CREATE TABLE user_sessions (
        id                  uuid         NOT NULL DEFAULT gen_random_uuid()
    ,   user_id             uuid         NOT NULL
    ,   remember_me         boolean      NOT NULL DEFAULT false
    ,   device_label        varchar(100)
    ,   user_agent          varchar(512)
    ,   ip_address          varchar(45)  NOT NULL
    ,   last_ip_address     varchar(45)  NOT NULL
    ,   created_at          timestamptz  NOT NULL DEFAULT now()
    ,   last_used_at        timestamptz  NOT NULL DEFAULT now()
    ,   expires_at          timestamptz  NOT NULL
    ,   absolute_expires_at timestamptz  NOT NULL
    ,   revoked_at          timestamptz
    ,   revoke_reason       varchar(30)
    ,   updated_at          timestamptz  NOT NULL DEFAULT now()

    -- Constraint --
    CONSTRAINT pk_user_sessions 
        PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user 
        FOREIGN KEY (user_id) 
        REFERENCES users (id) 
        ON DELETE CASCADE,
    CONSTRAINT ck_user_sessions_expiry 
        CHECK (expires_at <= absolute_expires_at),
    CONSTRAINT ck_user_sessions_absolute_after_created 
        CHECK (absolute_expires_at > created_at),
    CONSTRAINT ck_user_sessions_revoke_pair 
        CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT ck_user_sessions_revoke_reason 
        CHECK (revoke_reason IN (
              'LOGOUT'
            , 'LOGOUT_ALL'
            , 'USER_REVOKED'
            , 'PASSWORD_CHANGED'
            , 'PASSWORD_RESET'
            ,'TOKEN_REUSED'
            , 'SESSION_LIMIT'
            , 'ACCOUNT_LOCKED'
        ))
);

-- Index --
CREATE INDEX ix_user_sessions_user_active ON user_sessions (user_id, last_used_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_sessions_cleanup ON user_sessions (absolute_expires_at);
