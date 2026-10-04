package com.asms.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One write made by an Admin (BR-USER-15). Append only: the entity is {@link Immutable} and a database trigger rejects
 * UPDATE and DELETE (NFR-USER-09). {@code changes} and {@code metadata} are JSON objects written by
 * {@code AdminAuditService}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Getter
@Entity
@Immutable
@Table(name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** {"field": {"from": ..., "to": ...}} */
    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String changes;

    @Nullable
    @Column(length = 500)
    private String reason;

    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata;

    @Nullable
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Nullable
    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AdminAuditLog of(
            UUID actorId,
            AdminAuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String changesJson,
            @Nullable String reason,
            String metadataJson,
            @Nullable String ipAddress,
            @Nullable String userAgent,
            Instant createdAt) {
        AdminAuditLog log = new AdminAuditLog();
        log.actorId = actorId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.changes = changesJson;
        log.reason = reason;
        log.metadata = metadataJson;
        log.ipAddress = ipAddress;
        log.userAgent = userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        log.createdAt = createdAt;
        return log;
    }
}
