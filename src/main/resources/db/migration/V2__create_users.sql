-- Purpose: create users table with authentication columns (M01 Auth, M01_Auth_Tables_Desc sheet "users")
-- Author: MinhTien | Created: 2026-09-26
CREATE TABLE users (
        id                   uuid         NOT NULL DEFAULT gen_random_uuid()
    ,   password_hash        varchar(255)
    ,   email                varchar(255) NOT NULL
    ,   full_name            varchar(100) NOT NULL
    ,   student_code         varchar(20)
    ,   system_role          varchar(20)  NOT NULL DEFAULT 'USER'
    ,   status               varchar(30)  NOT NULL DEFAULT 'PENDING_ACTIVATION'
    ,   failed_login_count   smallint     NOT NULL DEFAULT 0
    ,   locked_until         timestamptz
    ,   last_failed_login_at timestamptz
    ,   locked_at            timestamptz
    ,   locked_reason        varchar(500)
    ,   activated_at         timestamptz
    ,   email_verified_at    timestamptz
    ,   password_changed_at  timestamptz
    ,   last_login_at        timestamptz
    ,   avatar_url           varchar(500)
    ,   created_by           uuid
    ,   created_at           timestamptz  NOT NULL DEFAULT now()
    ,   updated_at           timestamptz  NOT NULL DEFAULT now()
    ,   version              integer      NOT NULL DEFAULT 0,

    -- Constraint --
    CONSTRAINT pk_users 
        PRIMARY KEY (id),
    CONSTRAINT uq_users_email 
        UNIQUE (email),
    CONSTRAINT fk_users_created_by 
        FOREIGN KEY (created_by) 
        REFERENCES users (id) 
        ON DELETE SET NULL,
    CONSTRAINT ck_users_email_normalized 
        CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_full_name_length 
        CHECK (char_length(full_name) BETWEEN 2 AND 100),
    CONSTRAINT ck_users_student_code_format 
        CHECK (student_code ~ '^[A-Za-z0-9]{1,20}$'),
    CONSTRAINT ck_users_system_role 
        CHECK (system_role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status 
        CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED')),
    CONSTRAINT ck_users_failed_login_count 
        CHECK (failed_login_count >= 0),
    CONSTRAINT ck_users_active_has_password 
        CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL),
    CONSTRAINT ck_users_locked_has_time 
        CHECK (status <> 'LOCKED' OR locked_at IS NOT NULL)
);
-- Index --
CREATE UNIQUE INDEX uq_users_student_code ON users (student_code) WHERE student_code IS NOT NULL;
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_users_created_at ON users (created_at DESC);
