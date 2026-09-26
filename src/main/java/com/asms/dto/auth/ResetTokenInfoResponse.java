package com.asms.dto.auth;

import java.time.Instant;

/**
 * Response of API-AUTH-07: the reset link is valid; shows whose account it is without revealing the full email.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record ResetTokenInfoResponse(String maskedEmail, Instant expiresAt) {}
