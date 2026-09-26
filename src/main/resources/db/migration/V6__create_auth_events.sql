-- Purpose: create auth_events table, append-only log of authentication events (FA-12, section 7.8, NFR-AUTH-14)
-- Author: MinhTien | Created: 2026-09-26
CREATE TABLE auth_events (
        id             bigint           GENERATED ALWAYS AS IDENTITY
    ,   event_type     varchar(40)      NOT NULL
    ,   user_id        uuid
    ,   actor_id       uuid
    ,   email          varchar(255)
    ,   session_id     uuid,
    ,   ip_address     varchar(45),
    ,   user_agent     varchar(512),
    ,   metadata       jsonb            NOT NULL DEFAULT '{}'::jsonb,
    ,   created_at     timestamptz      NOT NULL DEFAULT now(),

    CONSTRAINT pk_auth_events 
        PRIMARY KEY (id),
    CONSTRAINT fk_auth_events_user 
        FOREIGN KEY (user_id) 
        REFERENCES users (id) 
        ON DELETE SET NULL,
    CONSTRAINT fk_auth_events_actor 
        FOREIGN KEY (actor_id) 
        REFERENCES users (id) 
        ON DELETE SET NULL,
    CONSTRAINT ck_auth_events_event_type 
        CHECK (event_type IN (
                'LOGIN_SUCCESS'
            ,   'LOGIN_FAILED'
            ,   'ACCOUNT_TEMP_LOCKED'
            ,   'LOGOUT'
            ,   'LOGOUT_ALL'
            ,   'SESSION_REVOKED'
            ,   'SESSION_EVICTED'
            ,   'REFRESH_TOKEN_REUSED'
            ,   'PASSWORD_RESET_REQUESTED'
            ,   'PASSWORD_RESET'
            ,   'PASSWORD_CHANGED'
            ,   'ACCOUNT_ACTIVATED'
            ,   'USER_CREATED'
            ,   'ACTIVATION_RESENT'))
);

CREATE INDEX ix_auth_events_user_time ON auth_events (user_id, created_at DESC);
CREATE INDEX ix_auth_events_type_time ON auth_events (event_type, created_at DESC);
CREATE INDEX ix_auth_events_created ON auth_events USING brin (created_at);
