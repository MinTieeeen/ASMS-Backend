package com.asms.dto.admin;

import com.asms.entity.auth.AuthEventType;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One sign-in event of a user (schema AuthEventItem, API-USER-14).
 *
 * @param deviceLabel browser and OS read from the user agent, e.g. "Chrome 128 · Windows 11"
 * @param metadata extra facts, e.g. {"reason": "INVALID_PASSWORD"}
 * @param actor who acted when it was not the account owner (an Admin)
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AuthEventItemResponse(
        long id,
        AuthEventType eventType,
        @Nullable String ipAddress,
        @Nullable String deviceLabel,
        Map<String, Object> metadata,
        @Nullable UserRefResponse actor,
        Instant createdAt) {}
