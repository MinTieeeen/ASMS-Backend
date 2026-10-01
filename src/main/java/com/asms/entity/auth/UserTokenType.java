package com.asms.entity.auth;

/**
 * Kind of one-time email token (BR-AUTH-11): activation (72 hours) or password reset (30 minutes).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum UserTokenType {
    ACTIVATION,
    PASSWORD_RESET
}
