package com.asms.entity.auth;

import com.asms.entity.base.BaseEntity;
import com.asms.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One login on one device or browser. The id is the {@code sid} claim of every access token issued for it.
 *
 * <p>A session is active while it is not revoked and neither its sliding expiry ({@code expiresAt}, BR-AUTH-05) nor its
 * absolute expiry ({@code absoluteExpiresAt}, BR-AUTH-06) has passed.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Entity
@Table(name = "user_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession extends BaseEntity {

    private static final int USER_AGENT_MAX_LENGTH = 512;
    private static final int DEVICE_LABEL_MAX_LENGTH = 100;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Nullable
    @Column(name = "device_label", length = DEVICE_LABEL_MAX_LENGTH)
    private String deviceLabel;

    @Nullable
    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "last_ip_address", nullable = false, length = 45)
    private String lastIpAddress;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Nullable
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private SessionRevokeReason revokeReason;

    /** Opens a session at login time (UC-AUTH-01). */
    public static UserSession open(
            User user,
            boolean rememberMe,
            @Nullable String deviceLabel,
            @Nullable String userAgent,
            String ipAddress,
            Instant now,
            Duration slidingTtl,
            Duration absoluteTtl) {
        UserSession session = new UserSession();
        session.user = user;
        session.rememberMe = rememberMe;
        session.deviceLabel = truncate(deviceLabel, DEVICE_LABEL_MAX_LENGTH);
        session.userAgent = truncate(userAgent, USER_AGENT_MAX_LENGTH);
        session.ipAddress = ipAddress;
        session.lastIpAddress = ipAddress;
        session.lastUsedAt = now;
        session.absoluteExpiresAt = now.plus(absoluteTtl);
        session.expiresAt = cappedExpiry(now, slidingTtl, session.absoluteExpiresAt);
        return session;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && expiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }

    /** Records a refresh: moves the sliding expiry forward without passing the absolute expiry (BR-AUTH-05, 06). */
    public void touch(String ipAddress, Instant now, Duration slidingTtl) {
        lastIpAddress = ipAddress;
        lastUsedAt = now;
        expiresAt = cappedExpiry(now, slidingTtl, absoluteExpiresAt);
    }

    /** Revokes the session. Revoking an already revoked session keeps the first reason. */
    public void revoke(SessionRevokeReason reason, Instant now) {
        if (isRevoked()) {
            return;
        }
        revokedAt = now;
        revokeReason = reason;
    }

    private static Instant cappedExpiry(Instant now, Duration ttl, Instant absoluteExpiry) {
        Instant sliding = now.plus(ttl);
        return sliding.isAfter(absoluteExpiry) ? absoluteExpiry : sliding;
    }

    @Nullable
    private static String truncate(@Nullable String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
