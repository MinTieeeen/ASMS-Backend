package com.asms.dto.auth;

import java.time.Instant;

/**
 * Response of API-AUTH-09: the activation link is valid; data shown on the activation form (SCR-AUTH-04). The
 * {@code userCode} pre-fills the login form once the account is activated.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record ActivationTokenInfoResponse(String email, String userCode, String fullName, Instant expiresAt) {}
