package com.asms.repository.auth;

import com.asms.entity.auth.AuthEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /** Newest events of a user (API-USER-14); pass a page size of limit + 1 to know whether more follow */
    @Query("""
            select e from AuthEvent e
            where e.userId = :userId
            order by e.createdAt desc, e.id desc
            """)
    List<AuthEvent> findFirstPageByUser(@Param("userId") UUID userId, Pageable page);

    /** Events strictly after the cursor position (keyset pagination on {@code (created_at, id)}) */
    @Query("""
            select e from AuthEvent e
            where e.userId = :userId
              and (e.createdAt < :createdAt or (e.createdAt = :createdAt and e.id < :id))
            order by e.createdAt desc, e.id desc
            """)
    List<AuthEvent> findPageByUserAfter(
            @Param("userId") UUID userId, @Param("createdAt") Instant createdAt, @Param("id") long id, Pageable page);

    /** Retention: removes events older than the configured period (NFR-AUTH-14). */
    @Modifying
    @Query("delete from AuthEvent e where e.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
