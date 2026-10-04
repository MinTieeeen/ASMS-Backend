-- Purpose: Module 2 (Người dùng) changes to users: profile columns, accent-free search column, who locked the account;
--          avatar_url is replaced by avatar_key (User_Mo_ta_bang_du_lieu.xlsx, sheets users and Migration)
-- Author: MinhTien | Created: 2026-10-04
-- pg_trgm and unaccent are installed by V1

ALTER TABLE users
    ADD COLUMN full_name_search varchar(100),
    ADD COLUMN bio              varchar(300),
    ADD COLUMN avatar_key       varchar(255),
    ADD COLUMN locked_by        uuid;

-- Same normalization as SearchNormalizer in Java: no accents (đ → d), lowercase, single spaces
UPDATE users
SET full_name_search = btrim(regexp_replace(lower(unaccent(full_name)), '\s+', ' ', 'g'));

ALTER TABLE users
    ALTER COLUMN full_name_search SET NOT NULL,
    DROP COLUMN avatar_url,
    ADD CONSTRAINT ck_users_bio_length
        CHECK (char_length(bio) <= 300),
    ADD CONSTRAINT ck_users_locked_reason_length
        CHECK (locked_reason IS NULL OR char_length(locked_reason) BETWEEN 10 AND 500),
    ADD CONSTRAINT fk_users_locked_by
        FOREIGN KEY (locked_by)
        REFERENCES users (id)
        ON DELETE SET NULL;

-- A locked account must say when and why (BR-USER-11)
ALTER TABLE users DROP CONSTRAINT ck_users_locked_has_time;
ALTER TABLE users ADD CONSTRAINT ck_users_locked_has_time
    CHECK (status <> 'LOCKED' OR (locked_at IS NOT NULL AND locked_reason IS NOT NULL));

-- Index --
CREATE INDEX ix_users_full_name_search_trgm ON users USING gin (full_name_search gin_trgm_ops);
CREATE INDEX ix_users_email_prefix ON users (email varchar_pattern_ops);
CREATE INDEX ix_users_role_status ON users (system_role, status);
CREATE INDEX ix_users_last_login_at ON users (last_login_at DESC NULLS LAST);

-- An Admin logs a user out of every device (UC-USER-09)
ALTER TABLE user_sessions DROP CONSTRAINT ck_user_sessions_revoke_reason;
ALTER TABLE user_sessions ADD CONSTRAINT ck_user_sessions_revoke_reason
    CHECK (revoke_reason IN (
          'LOGOUT'
        , 'LOGOUT_ALL'
        , 'USER_REVOKED'
        , 'PASSWORD_CHANGED'
        , 'PASSWORD_RESET'
        , 'TOKEN_REUSED'
        , 'SESSION_LIMIT'
        , 'ACCOUNT_LOCKED'
        , 'ADMIN_REVOKED'
    ));
