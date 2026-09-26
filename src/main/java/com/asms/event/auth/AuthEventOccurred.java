package com.asms.event.auth;

import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Application event describing one authentication event (section 7.8). Published by Auth services and persisted
 * asynchronously by {@code AuthEventListener}, so that logging never slows down or breaks the API.
 *
 * <p>{@code metadata} must never contain passwords or tokens (NFR-AUTH-03).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record AuthEventOccurred(
        AuthEventType type,
        @Nullable UUID userId,
        @Nullable UUID actorId,
        @Nullable String email,
        @Nullable UUID sessionId,
        @Nullable String ipAddress,
        @Nullable String userAgent,
        Map<String, Object> metadata) {

    public AuthEventOccurred {
        metadata = Map.copyOf(metadata);
    }

    public static AuthEventOccurred of(AuthEventType type, ClientInfo client) {
        return new AuthEventOccurred(type, null, null, null, null, client.ipAddress(), client.userAgent(), Map.of());
    }

    public AuthEventOccurred withUser(@Nullable UUID newUserId) {
        return new AuthEventOccurred(type, newUserId, actorId, email, sessionId, ipAddress, userAgent, metadata);
    }

    public AuthEventOccurred withActor(@Nullable UUID newActorId) {
        return new AuthEventOccurred(type, userId, newActorId, email, sessionId, ipAddress, userAgent, metadata);
    }

    public AuthEventOccurred withEmail(@Nullable String newEmail) {
        return new AuthEventOccurred(type, userId, actorId, newEmail, sessionId, ipAddress, userAgent, metadata);
    }

    public AuthEventOccurred withSession(@Nullable UUID newSessionId) {
        return new AuthEventOccurred(type, userId, actorId, email, newSessionId, ipAddress, userAgent, metadata);
    }

    public AuthEventOccurred withMetadata(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(metadata);
        merged.put(key, value);
        return new AuthEventOccurred(type, userId, actorId, email, sessionId, ipAddress, userAgent, merged);
    }
}
