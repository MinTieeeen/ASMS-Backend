package com.asms.entity.auth;

/**
 * Value of {@code metadata.reason} for {@link AuthEventType#LOGIN_FAILED} events.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum LoginFailedReason {
    USER_NOT_FOUND,
    INVALID_PASSWORD,
    NO_PASSWORD,
    ACCOUNT_LOCKED,
    TEMP_LOCKED,
    RATE_LIMITED
}
