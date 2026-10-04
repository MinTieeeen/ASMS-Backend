package com.asms.repository.admin;

import com.asms.entity.admin.AdminAuditAction;
import com.asms.entity.admin.AdminAuditLog;
import com.asms.entity.admin.AuditTargetType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Data access for {@link AdminAuditLog}; only inserts and reads (BR-USER-15).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public interface AdminAuditLogRepository
        extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {

    long countByTargetTypeAndTargetIdAndAction(AuditTargetType targetType, UUID targetId, AdminAuditAction action);
}
