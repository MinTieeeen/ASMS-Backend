package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.dto.auth.AuthTokenResponse;
import com.asms.dto.auth.IssuedSession;
import com.asms.dto.auth.SessionResponse;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.RefreshToken;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserSession;
import com.asms.entity.user.User;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.auth.SessionMapper;
import com.asms.mapper.user.UserMapper;
import com.asms.repository.auth.RefreshTokenRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.security.JwtTokenService;
import com.asms.security.RevokedSessionStore;
import com.asms.util.RandomCodeGenerator;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates, refreshes and revokes login sessions (UC-AUTH-02, UC-AUTH-03, UC-AUTH-09).
 *
 * <p>Every revocation also puts the session id on the Redis blocklist so that its access tokens stop working at once
 * (section 7.4). {@link #revokeAllForUser} is the shared entry point for other modules, such as the admin module when
 * it locks an account.
 *
 * <p>Methods that revoke something and then report an error use {@code noRollbackFor} so the revocation is kept.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final RevokedSessionStore revokedSessionStore;
    private final DeviceLabelParser deviceLabelParser;
    private final AuthEventPublisher events;
    private final UserMapper userMapper;
    private final SessionMapper sessionMapper;
    private final AppProperties props;
    private final Clock clock;

    /**
     * Opens a session after a successful login and issues its first tokens (UC-AUTH-01 step 7). Evicts the least
     * recently used sessions when the user already has the maximum number (BR-AUTH-08).
     */
    @Transactional
    public IssuedSession openSession(User user, boolean rememberMe, ClientInfo client) {
        Instant now = Instant.now(clock);
        evictSessionsOverLimit(user.getId(), client, now);
        UserSession session = sessionRepository.save(UserSession.open(
                user,
                rememberMe,
                deviceLabelParser.parse(client.userAgent()),
                client.userAgent(),
                client.ipAddress(),
                now,
                slidingTtl(rememberMe),
                props.auth().sessionAbsoluteTtl()));
        String refreshToken = issueRefreshToken(session);
        return toIssuedSession(user, session, refreshToken, now);
    }

    /**
     * Rotates the refresh token and issues a new access token (section 7.3, FR-AUTH-09).
     *
     * @throws BusinessException {@code AUTH_REFRESH_INVALID}, {@code AUTH_REFRESH_RACE}, {@code AUTH_REFRESH_REUSED}
     *     or {@code AUTH_ACCOUNT_LOCKED}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedSession refresh(@Nullable String refreshToken, ClientInfo client) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHasher.sha256Hex(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_INVALID));
        UserSession session = current.getSession();
        if (current.isUsed()) {
            rejectReusedToken(current, session, client, now);
        }
        if (!session.isActive(now) || !current.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        User user = session.getUser();
        if (!user.isActive()) {
            revoke(session, SessionRevokeReason.ACCOUNT_LOCKED, now);
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        session.touch(client.ipAddress(), now, slidingTtl(session.isRememberMe()));
        String newRefreshToken = RandomCodeGenerator.urlSafeToken(REFRESH_TOKEN_BYTES);
        RefreshToken replacement =
                refreshTokenRepository.save(RefreshToken.issue(session, TokenHasher.sha256Hex(newRefreshToken)));
        current.markRotated(replacement.getId(), now);
        return toIssuedSession(user, session, newRefreshToken, now);
    }

    /** Logs out the session owning the refresh token; a missing or unknown token is ignored (UC-AUTH-03). */
    @Transactional
    public void logout(@Nullable String refreshToken, ClientInfo client) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(refreshToken))
                .map(RefreshToken::getSession)
                .filter(session -> !session.isRevoked())
                .ifPresent(session -> {
                    revoke(session, SessionRevokeReason.LOGOUT, Instant.now(clock));
                    events.publish(AuthEventOccurred.of(AuthEventType.LOGOUT, client)
                            .withUser(session.getUserId())
                            .withSession(session.getId()));
                });
    }

    /** Logs out every device of the user, including the current one (UC-AUTH-03, FR-AUTH-11). */
    @Transactional
    public void logoutAll(UUID userId, UUID currentSessionId, ClientInfo client) {
        int revoked = revokeAllForUser(userId, SessionRevokeReason.LOGOUT_ALL, null);
        events.publish(AuthEventOccurred.of(AuthEventType.LOGOUT_ALL, client)
                .withUser(userId)
                .withSession(currentSessionId)
                .withMetadata("revokedSessions", revoked));
    }

    /**
     * Revokes every active session of a user except {@code exceptSessionId} (password change, password reset, account
     * locked by an Admin).
     *
     * @return the number of revoked sessions
     */
    @Transactional
    public int revokeAllForUser(UUID userId, SessionRevokeReason reason, @Nullable UUID exceptSessionId) {
        Instant now = Instant.now(clock);
        List<UserSession> sessions = sessionRepository.findActiveByUserId(userId, now).stream()
                .filter(session -> !session.getId().equals(exceptSessionId))
                .toList();
        sessions.forEach(session -> revoke(session, reason, now));
        return sessions.size();
    }

    /** Revokes one session, for example the current one when the account gets temporarily locked (UC-AUTH-06). */
    @Transactional
    public void revokeSession(UUID sessionId, SessionRevokeReason reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> revoke(session, reason, Instant.now(clock)));
    }

    /** Active sessions of the user, the current one first (API-AUTH-14, FR-AUTH-27). */
    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
        return sessionRepository.findActiveByUserId(userId, Instant.now(clock)).stream()
                .map(session -> sessionMapper.toResponse(session, currentSessionId))
                .sorted(Comparator.comparing(SessionResponse::current).reversed())
                .toList();
    }

    /**
     * Logs out another device of the user (API-AUTH-15, FR-AUTH-28).
     *
     * @throws BusinessException {@code AUTH_SESSION_IS_CURRENT} or {@code AUTH_SESSION_NOT_FOUND}
     */
    @Transactional
    public void revokeOtherSession(UUID userId, UUID currentSessionId, UUID targetSessionId, ClientInfo client) {
        if (targetSessionId.equals(currentSessionId)) {
            throw new BusinessException(ErrorCode.AUTH_SESSION_IS_CURRENT);
        }
        Instant now = Instant.now(clock);
        UserSession target = sessionRepository
                .findByIdAndUser_Id(targetSessionId, userId)
                .filter(session -> session.isActive(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_SESSION_NOT_FOUND));
        revoke(target, SessionRevokeReason.USER_REVOKED, now);
        events.publish(AuthEventOccurred.of(AuthEventType.SESSION_REVOKED, client)
                .withUser(userId)
                .withSession(currentSessionId)
                .withMetadata("revokedSessionId", targetSessionId.toString()));
    }

    /**
     * A rotated token came back. Within the race window it is most likely two tabs refreshing at once; after that it
     * is treated as theft and the whole session is revoked (section 7.3).
     */
    private void rejectReusedToken(RefreshToken token, UserSession session, ClientInfo client, Instant now) {
        Instant usedAt = token.getUsedAt();
        if (usedAt != null && !usedAt.plus(props.auth().refreshRaceWindow()).isBefore(now)) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_RACE);
        }
        revoke(session, SessionRevokeReason.TOKEN_REUSED, now);
        log.warn("Refresh token reuse detected for session {}", session.getId());
        events.publish(AuthEventOccurred.of(AuthEventType.REFRESH_TOKEN_REUSED, client)
                .withUser(session.getUserId())
                .withSession(session.getId()));
        throw new BusinessException(ErrorCode.AUTH_REFRESH_REUSED);
    }

    private void evictSessionsOverLimit(UUID userId, ClientInfo client, Instant now) {
        List<UserSession> active = sessionRepository.findActiveByUserId(userId, now);
        int maxSessions = props.auth().maxSessionsPerUser();
        // Sorted by last use descending: the tail holds the least recently used sessions
        for (int i = maxSessions - 1; i < active.size(); i++) {
            UserSession evicted = active.get(i);
            revoke(evicted, SessionRevokeReason.SESSION_LIMIT, now);
            events.publish(AuthEventOccurred.of(AuthEventType.SESSION_EVICTED, client)
                    .withUser(userId)
                    .withSession(evicted.getId()));
        }
    }

    private void revoke(UserSession session, SessionRevokeReason reason, Instant now) {
        session.revoke(reason, now);
        revokedSessionStore.markRevoked(session.getId());
    }

    private String issueRefreshToken(UserSession session) {
        String refreshToken = RandomCodeGenerator.urlSafeToken(REFRESH_TOKEN_BYTES);
        refreshTokenRepository.save(RefreshToken.issue(session, TokenHasher.sha256Hex(refreshToken)));
        return refreshToken;
    }

    private IssuedSession toIssuedSession(User user, UserSession session, String refreshToken, Instant now) {
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(
                user.getId(), session.getId(), user.getSystemRole().name());
        AuthTokenResponse body = new AuthTokenResponse(
                accessToken.value(), accessToken.expiresInSeconds(), userMapper.toCurrentUser(user));
        // BR-AUTH-05: persistent cookie only with "remember me"; otherwise it dies with the browser
        Duration cookieMaxAge = session.isRememberMe() ? Duration.between(now, session.getExpiresAt()) : null;
        return new IssuedSession(body, refreshToken, cookieMaxAge);
    }

    private Duration slidingTtl(boolean rememberMe) {
        return rememberMe ? props.auth().refreshTtlRemember() : props.auth().refreshTtlDefault();
    }
}
