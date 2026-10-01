package com.asms.entity.auth;

/**
 * Why a session was revoked ({@code user_sessions.revoke_reason}).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum SessionRevokeReason {
    /** The user logged out on this device (UC-AUTH-03). */
    LOGOUT,
    /** The user logged out of every device (UC-AUTH-03). */
    LOGOUT_ALL,
    /** The user logged this device out from another device (UC-AUTH-09). */
    USER_REVOKED,
    /** Other sessions revoked after a password change (UC-AUTH-06). */
    PASSWORD_CHANGED,
    /** Every session revoked after a password reset (UC-AUTH-04). */
    PASSWORD_RESET,
    /** A rotated refresh token was reused (section 7.3). */
    TOKEN_REUSED,
    /** Oldest session evicted when the user exceeds the session limit (BR-AUTH-08). */
    SESSION_LIMIT,
    /** Account locked by an Admin, or temporarily locked while changing the password (UC-AUTH-06). */
    ACCOUNT_LOCKED
}
