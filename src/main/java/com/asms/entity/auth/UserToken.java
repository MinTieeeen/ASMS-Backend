package com.asms.entity.auth;

import com.asms.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One-time token sent by email to activate an account or reset a password (BR-AUTH-11). Only the SHA-256 hash is
 * stored (NFR-AUTH-04).
 *
 * <p>A token is valid while it is neither used nor invalidated by a newer token of the same type, and has not
 * expired.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Entity
@Table(name = "user_tokens")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserTokenType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Nullable
    @Column(name = "used_at")
    private Instant usedAt;

    @Nullable
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Nullable
    @Column(name = "created_by")
    private UUID createdBy;

    @Nullable
    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserToken issue(
            User user,
            UserTokenType type,
            String tokenHash,
            Instant now,
            Duration ttl,
            @Nullable UUID createdBy,
            @Nullable String requestIp) {
        UserToken token = new UserToken();
        token.user = user;
        token.type = type;
        token.tokenHash = tokenHash;
        token.expiresAt = now.plus(ttl);
        token.createdBy = createdBy;
        token.requestIp = requestIp;
        return token;
    }

    /** True once the token was used or replaced by a newer one; the API reports both as {@code USED}. */
    public boolean isConsumed() {
        return usedAt != null || invalidatedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }
}
