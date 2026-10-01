package com.asms.service.auth;

/**
 * One unmet criterion of the password policy (BR-AUTH-01), returned in {@code violations} of
 * {@code AUTH_PASSWORD_POLICY} so the frontend can highlight the failed rules.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public enum PasswordViolation {
    /** Not between 8 and 64 characters. */
    LENGTH,
    /** No letter. */
    LETTER,
    /** No digit. */
    DIGIT,
    /** Contains the part of the email before "@" (when that part has at least 4 characters). */
    CONTAINS_EMAIL,
    /** Starts or ends with whitespace. */
    WHITESPACE
}
