package com.asms.entity.admin;

/**
 * Action of an {@code admin_audit_logs} row (sheet Enum, admin_audit_action).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public enum AdminAuditAction {
    /** Account created (API-AUTH-12) */
    USER_CREATED,
    /** Activation email sent again (API-AUTH-13) */
    ACTIVATION_RESENT,
    /** User data edited (API-USER-09) */
    USER_UPDATED,
    /** Account locked (API-USER-11) */
    USER_LOCKED,
    /** Account unlocked (API-USER-12) */
    USER_UNLOCKED,
    /** User logged out of every device (API-USER-13) */
    USER_SESSIONS_REVOKED,
    SCHOOL_CREATED,
    /** Includes activating and deactivating */
    SCHOOL_UPDATED,
    SCHOOL_DELETED,
    /** Also once per holiday copied to another year (API-USER-31, metadata.copiedFromId) */
    HOLIDAY_CREATED,
    HOLIDAY_UPDATED,
    HOLIDAY_DELETED
}
