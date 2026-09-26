package com.asms.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
 * One refresh token of a session's rotation chain (section 7.3). Only the SHA-256 hash is stored (NFR-AUTH-04).
 *
 * <p>At any time a session has exactly one token with {@code usedAt == null}; presenting a token whose
 * {@code usedAt} is set means it was reused. Append-only apart from the rotation fields, so it has no
 * {@code updated_at} and does not extend {@code BaseEntity}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private UserSession session;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Nullable
    @Column(name = "used_at")
    private Instant usedAt;

    @Nullable
    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Issues a token that expires together with the session's current sliding expiry. */
    public static RefreshToken issue(UserSession session, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.session = session;
        token.tokenHash = tokenHash;
        token.expiresAt = session.getExpiresAt();
        return token;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** Marks this token as rotated and links it to its replacement. */
    public void markRotated(UUID replacementId, Instant now) {
        usedAt = now;
        replacedById = replacementId;
    }
}
