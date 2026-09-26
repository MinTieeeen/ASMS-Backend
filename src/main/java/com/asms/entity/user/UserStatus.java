package com.asms.entity.user;

/**
 * Account status. Only {@link #ACTIVE} accounts can log in and refresh sessions (BR-AUTH-03); {@link #LOCKED} is set by the admin module (M12).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    LOCKED
}
