package com.asms.service.admin;

import com.asms.dto.common.ClientInfo;
import com.asms.entity.admin.AdminAuditAction;
import com.asms.entity.admin.AdminAuditLog;
import com.asms.entity.admin.AuditTargetType;
import com.asms.repository.admin.AdminAuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the admin audit log (BR-USER-15, FR-USER-23).
 *
 * <p>Unlike {@code auth_events}, which are written asynchronously, an audit row is written in the same transaction as
 * the change it describes: if the change rolls back, so does the row, and the other way round (NFR-USER-08). Calling it
 * outside a transaction is a bug, hence {@link Propagation#MANDATORY}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry, ClientInfo client) {
        auditLogRepository.save(AdminAuditLog.of(
                entry.actorId(),
                entry.action(),
                entry.targetType(),
                entry.targetId(),
                objectMapper.writeValueAsString(entry.changes().asMap()),
                entry.reason(),
                objectMapper.writeValueAsString(entry.metadata()),
                client.ipAddress(),
                client.userAgent(),
                Instant.now(clock)));
    }

    /**
     * What an Admin did to which object.
     *
     * @param reason required for {@link AdminAuditAction#USER_LOCKED}
     * @param metadata extra facts, e.g. {"revokedCount": 3}
     */
    public record AuditEntry(
            UUID actorId,
            AdminAuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            AuditChanges changes,
            @Nullable String reason,
            Map<String, Object> metadata) {

        public static AuditEntry onUser(UUID actorId, AdminAuditAction action, UUID userId, AuditChanges changes) {
            return new AuditEntry(actorId, action, AuditTargetType.USER, userId, changes, null, Map.of());
        }

        public AuditEntry withReason(String newReason) {
            return new AuditEntry(actorId, action, targetType, targetId, changes, newReason, metadata);
        }

        public AuditEntry withMetadata(Map<String, Object> newMetadata) {
            return new AuditEntry(actorId, action, targetType, targetId, changes, reason, newMetadata);
        }
    }
}
