-- Purpose: append-only log of every write an Admin makes (BR-USER-15, NFR-USER-08, NFR-USER-09)
-- Author: MinhTien | Created: 2026-10-04
CREATE TABLE admin_audit_logs (
        id          bigint       GENERATED ALWAYS AS IDENTITY
    ,   actor_id    uuid         NOT NULL
    ,   action      varchar(40)  NOT NULL
    ,   target_type varchar(30)  NOT NULL DEFAULT 'USER'
    ,   target_id   uuid         NOT NULL
    ,   changes     jsonb        NOT NULL DEFAULT '{}'::jsonb
    ,   reason      varchar(500)
    ,   metadata    jsonb        NOT NULL DEFAULT '{}'::jsonb
    ,   ip_address  varchar(45)
    ,   user_agent  varchar(512)
    ,   created_at  timestamptz  NOT NULL DEFAULT now(),

    -- Constraint --
    CONSTRAINT pk_admin_audit_logs
        PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_logs_actor
        FOREIGN KEY (actor_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_admin_audit_logs_action
        CHECK (action IN (
              'USER_CREATED'
            , 'ACTIVATION_RESENT'
            , 'USER_UPDATED'
            , 'USER_LOCKED'
            , 'USER_UNLOCKED'
            , 'USER_SESSIONS_REVOKED'
            , 'SCHOOL_CREATED'
            , 'SCHOOL_UPDATED'
            , 'SCHOOL_DELETED'
            , 'HOLIDAY_CREATED'
            , 'HOLIDAY_UPDATED'
            , 'HOLIDAY_DELETED'
        )),
    CONSTRAINT ck_admin_audit_logs_target_type
        CHECK (target_type IN ('USER', 'SCHOOL', 'HOLIDAY')),
    CONSTRAINT ck_admin_audit_logs_changes
        CHECK (jsonb_typeof(changes) = 'object'),
    CONSTRAINT ck_admin_audit_logs_lock_reason
        CHECK (action <> 'USER_LOCKED' OR reason IS NOT NULL)
);

-- Index --
CREATE INDEX ix_admin_audit_logs_created ON admin_audit_logs (created_at DESC, id DESC);
CREATE INDEX ix_admin_audit_logs_target ON admin_audit_logs (target_type, target_id, created_at DESC);
CREATE INDEX ix_admin_audit_logs_actor ON admin_audit_logs (actor_id, created_at DESC);
CREATE INDEX ix_admin_audit_logs_action ON admin_audit_logs (action, created_at DESC);

-- Append only. The application connects as the table owner, so REVOKE alone would not stop it; this trigger does.
-- Only the retention job may delete, after SET LOCAL asms.audit_purge = 'on' in its own transaction.
CREATE FUNCTION fn_admin_audit_logs_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('asms.audit_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'admin_audit_logs is append-only (%)', TG_OP;
END;
$$;

CREATE TRIGGER trg_admin_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON admin_audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_admin_audit_logs_append_only();
