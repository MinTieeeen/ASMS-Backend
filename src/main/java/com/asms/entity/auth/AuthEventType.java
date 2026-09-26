package com.asms.entity.auth;

/**
 * Type of an authentication event written to {@code auth_events} (section 7.8).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum AuthEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_TEMP_LOCKED,
    LOGOUT,
    LOGOUT_ALL,
    SESSION_REVOKED,
    SESSION_EVICTED,
    REFRESH_TOKEN_REUSED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    ACCOUNT_ACTIVATED,
    USER_CREATED,
    ACTIVATION_RESENT
}
