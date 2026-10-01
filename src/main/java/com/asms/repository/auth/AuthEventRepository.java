package com.asms.repository.auth;

import com.asms.entity.auth.AuthEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for the append-only {@link AuthEvent} log.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    /** Retention: removes events older than the configured period (NFR-AUTH-14). */
    @Modifying
    @Query("delete from AuthEvent e where e.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
