package com.asms.dto.auth;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One signed-in device in the device list (API-AUTH-14, FR-AUTH-27).
 *
 * @param ipAddress IP seen at the latest refresh
 * @param createdAt login time
 * @param current true for the session of the access token making the request
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record SessionResponse(
        UUID id,
        @Nullable String deviceLabel,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current) {}
