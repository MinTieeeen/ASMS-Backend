package com.asms.repository.auth;

import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link UserToken}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Invalidates every unused token of this type for the user before a new one is issued (BR-AUTH-11). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update UserToken t set t.invalidatedAt = :now
            where t.user.id = :userId
              and t.type = :type
              and t.usedAt is null
              and t.invalidatedAt is null
            """)
    int invalidateActive(@Param("userId") UUID userId, @Param("type") UserTokenType type, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
