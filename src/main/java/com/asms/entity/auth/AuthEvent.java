package com.asms.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Append-only authentication event (FA-12, section 7.8). Never updated or deleted by application code, except the
 * retention job that removes events older than 180 days (NFR-AUTH-14).
 *
 * <p>{@code metadata} holds a JSON object serialized by the caller; it never contains passwords or tokens.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Entity
@Table(name = "auth_events")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthEvent {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuthEventType eventType;

    @Nullable
    @Column(name = "user_id")
    private UUID userId;

    @Nullable
    @Column(name = "actor_id")
    private UUID actorId;

    @Nullable
    @Column(length = 255)
    private String email;

    @Nullable
    @Column(name = "session_id")
    private UUID sessionId;

    @Nullable
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Nullable
    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuthEvent of(
            AuthEventType eventType,
            @Nullable UUID userId,
            @Nullable UUID actorId,
            @Nullable String email,
            @Nullable UUID sessionId,
            @Nullable String ipAddress,
            @Nullable String userAgent,
            String metadataJson) {
        AuthEvent event = new AuthEvent();
        event.eventType = eventType;
        event.userId = userId;
        event.actorId = actorId;
        event.email = email;
        event.sessionId = sessionId;
        event.ipAddress = ipAddress;
        event.userAgent = userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        event.metadata = metadataJson;
        return event;
    }
}
