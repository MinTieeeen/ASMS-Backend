package com.asms.dto.admin;

import java.time.Instant;

/**
 * Response of API-AUTH-13: a new activation email was queued; the new link is valid until
 * {@code activationExpiresAt}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public record ActivationEmailResponse(Instant activationExpiresAt) {}
