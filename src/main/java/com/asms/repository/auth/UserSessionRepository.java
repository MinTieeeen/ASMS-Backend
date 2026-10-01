package com.asms.repository.auth;

import com.asms.entity.auth.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link UserSession}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Active sessions of a user, most recently used first (UC-AUTH-09, BR-AUTH-08). */
    @Query("""
            select s from UserSession s
            where s.user.id = :userId
              and s.revokedAt is null
              and s.expiresAt > :now
              and s.absoluteExpiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    // "User_Id" (not "UserId"): UserSession#getUserId() would otherwise be read as a non-existent attribute
    Optional<UserSession> findByIdAndUser_Id(UUID id, UUID userId);

    /**
     * Deletes sessions that ended before {@code cutoff}: absolutely or slidingly expired, or revoked (NFR-AUTH-09).
     * Their refresh tokens are removed by the {@code ON DELETE CASCADE} foreign key.
     */
    @Modifying
    @Query("""
            delete from UserSession s
            where s.absoluteExpiresAt < :cutoff
               or s.expiresAt < :cutoff
               or s.revokedAt < :cutoff
            """)
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
