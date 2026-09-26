package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.dto.auth.IssuedSession;
import com.asms.dto.auth.SessionResponse;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.RefreshToken;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserSession;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.auth.SessionMapperImpl;
import com.asms.mapper.user.UserMapperImpl;
import com.asms.repository.auth.RefreshTokenRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.security.JwtTokenService;
import com.asms.security.RevokedSessionStore;
import com.asms.support.TestProperties;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.9", "UA");
    private static final String RAW_TOKEN = "raw-refresh-token";

    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RevokedSessionStore revokedSessionStore = mock(RevokedSessionStore.class);
    private final DeviceLabelParser deviceLabelParser = mock(DeviceLabelParser.class);
    private final AuthEventPublisher events = mock(AuthEventPublisher.class);

    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                sessionRepository,
                refreshTokenRepository,
                jwtTokenService,
                revokedSessionStore,
                deviceLabelParser,
                events,
                new UserMapperImpl(),
                new SessionMapperImpl(),
                TestProperties.appProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.createBootstrapAdmin("a@gmail.com", "Nguyen Van A", "hash", NOW.minus(Duration.ofDays(2)));
        user.setId(UUID.randomUUID());
        when(jwtTokenService.issue(any(), any(), anyString()))
                .thenReturn(new JwtTokenService.AccessToken("access", 900));
        when(sessionRepository.save(any())).thenAnswer(inv -> withId(inv.<UserSession>getArgument(0)));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
            return token;
        });
    }

    @Test
    @DisplayName("BR-AUTH-05: remember me gives a 30-day persistent cookie")
    void openSession_shouldIssuePersistentCookie_whenRememberMe() {
        IssuedSession issued = sessionService.openSession(user, true, CLIENT);

        assertThat(issued.cookieMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(issued.body().user().email()).isEqualTo("a@gmail.com");
        assertThat(issued.refreshToken()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("BR-AUTH-05: without remember me the cookie is a browser-session cookie")
    void openSession_shouldIssueSessionCookie_whenNotRememberMe() {
        IssuedSession issued = sessionService.openSession(user, false, CLIENT);

        assertThat(issued.cookieMaxAge()).isNull();
    }

    @Test
    @DisplayName("BR-AUTH-08: the least recently used session is evicted at the limit")
    void openSession_shouldEvictLeastRecentlyUsed_whenLimitReached() {
        List<UserSession> active = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            active.add(session(NOW.minusSeconds(i)));
        }
        when(sessionRepository.findActiveByUserId(user.getId(), NOW)).thenReturn(active);

        sessionService.openSession(user, false, CLIENT);

        UserSession oldest = active.get(9);
        assertThat(oldest.getRevokeReason()).isEqualTo(SessionRevokeReason.SESSION_LIMIT);
        assertThat(active.get(8).isRevoked()).isFalse();
        verify(revokedSessionStore).markRevoked(oldest.getId());
    }

    @Test
    @DisplayName("FR-AUTH-09: refresh rotates the token and keeps the same session")
    void refresh_shouldRotateToken() {
        UserSession session = session(NOW.minusSeconds(60));
        RefreshToken current = storedToken(session);

        IssuedSession issued = sessionService.refresh(RAW_TOKEN, CLIENT);

        assertThat(current.isUsed()).isTrue();
        assertThat(current.getReplacedById()).isNotNull();
        assertThat(issued.refreshToken()).isNotEqualTo(RAW_TOKEN);
        assertThat(session.getLastIpAddress()).isEqualTo("10.0.0.9");
        assertThat(session.getLastUsedAt()).isEqualTo(NOW);
        verify(jwtTokenService).issue(user.getId(), session.getId(), "ADMIN");
    }

    @Test
    void refresh_shouldReject_whenTokenUnknown() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.refresh(RAW_TOKEN, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Test
    void refresh_shouldReject_whenCookieMissing() {
        assertThatThrownBy(() -> sessionService.refresh(null, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Test
    @DisplayName("Section 7.3: reuse within 5 seconds is a race between tabs, not theft")
    void refresh_shouldReportRace_whenReusedWithinWindow() {
        UserSession session = session(NOW);
        RefreshToken current = storedToken(session);
        current.markRotated(UUID.randomUUID(), NOW.minusSeconds(3));

        assertThatThrownBy(() -> sessionService.refresh(RAW_TOKEN, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_RACE);
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("FR-AUTH-09: reuse after the race window revokes the whole session")
    void refresh_shouldRevokeSession_whenReusedAfterWindow() {
        UserSession session = session(NOW);
        RefreshToken current = storedToken(session);
        current.markRotated(UUID.randomUUID(), NOW.minusSeconds(30));

        assertThatThrownBy(() -> sessionService.refresh(RAW_TOKEN, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_REUSED);
        assertThat(session.getRevokeReason()).isEqualTo(SessionRevokeReason.TOKEN_REUSED);
        verify(revokedSessionStore).markRevoked(session.getId());
    }

    @Test
    @DisplayName("BR-AUTH-06: a session past its absolute expiry cannot be refreshed")
    void refresh_shouldReject_whenSessionExpired() {
        UserSession session = UserSession.open(
                user,
                true,
                null,
                "UA",
                "10.0.0.1",
                NOW.minus(Duration.ofDays(31)),
                Duration.ofDays(30),
                Duration.ofDays(30));
        withId(session);
        storedToken(session);

        assertThatThrownBy(() -> sessionService.refresh(RAW_TOKEN, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Test
    @DisplayName("UC-AUTH-02 3c: refresh of a locked account revokes the session")
    void refresh_shouldRevokeAndReject_whenAccountLocked() {
        UserSession session = session(NOW);
        storedToken(session);
        ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);

        assertThatThrownBy(() -> sessionService.refresh(RAW_TOKEN, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
        assertThat(session.getRevokeReason()).isEqualTo(SessionRevokeReason.ACCOUNT_LOCKED);
    }

    @Test
    void logout_shouldRevokeSessionOfCookie() {
        UserSession session = session(NOW);
        RefreshToken token = RefreshToken.issue(session, TokenHasher.sha256Hex(RAW_TOKEN));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        sessionService.logout(RAW_TOKEN, CLIENT);

        assertThat(session.getRevokeReason()).isEqualTo(SessionRevokeReason.LOGOUT);
        verify(revokedSessionStore).markRevoked(session.getId());
    }

    @Test
    void logout_shouldDoNothing_whenCookieMissing() {
        sessionService.logout(null, CLIENT);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void revokeAllForUser_shouldKeepExceptedSession() {
        UserSession current = session(NOW);
        UserSession other = session(NOW.minusSeconds(60));
        when(sessionRepository.findActiveByUserId(user.getId(), NOW)).thenReturn(List.of(current, other));

        int revoked =
                sessionService.revokeAllForUser(user.getId(), SessionRevokeReason.PASSWORD_CHANGED, current.getId());

        assertThat(revoked).isEqualTo(1);
        assertThat(current.isRevoked()).isFalse();
        assertThat(other.getRevokeReason()).isEqualTo(SessionRevokeReason.PASSWORD_CHANGED);
    }

    @Test
    @DisplayName("UC-AUTH-09: the current session is listed first and flagged")
    void listActiveSessions_shouldPutCurrentFirst() {
        UserSession recent = session(NOW);
        UserSession current = session(NOW.minusSeconds(300));
        when(sessionRepository.findActiveByUserId(user.getId(), NOW)).thenReturn(List.of(recent, current));

        List<SessionResponse> sessions = sessionService.listActiveSessions(user.getId(), current.getId());

        assertThat(sessions).extracting(SessionResponse::id).containsExactly(current.getId(), recent.getId());
        assertThat(sessions.getFirst().current()).isTrue();
    }

    @Test
    @DisplayName("UC-AUTH-09 5b: the current session cannot be revoked through the device list")
    void revokeOtherSession_shouldReject_whenTargetIsCurrent() {
        UUID current = UUID.randomUUID();

        assertThatThrownBy(() -> sessionService.revokeOtherSession(user.getId(), current, current, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SESSION_IS_CURRENT);
    }

    @Test
    @DisplayName("UC-AUTH-09 5a: a session of another user is reported as not found")
    void revokeOtherSession_shouldReject_whenSessionNotOwned() {
        UUID target = UUID.randomUUID();
        when(sessionRepository.findByIdAndUser_Id(target, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revokeOtherSession(user.getId(), UUID.randomUUID(), target, CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SESSION_NOT_FOUND);
    }

    @Test
    void revokeOtherSession_shouldRevokeTarget() {
        UserSession target = session(NOW);
        when(sessionRepository.findByIdAndUser_Id(target.getId(), user.getId())).thenReturn(Optional.of(target));

        sessionService.revokeOtherSession(user.getId(), UUID.randomUUID(), target.getId(), CLIENT);

        assertThat(target.getRevokeReason()).isEqualTo(SessionRevokeReason.USER_REVOKED);
        verify(revokedSessionStore).markRevoked(eq(target.getId()));
    }

    private UserSession session(Instant lastUsedAt) {
        UserSession session = UserSession.open(
                user, false, null, "UA", "10.0.0.1", NOW.minusSeconds(3600), Duration.ofHours(12), Duration.ofDays(30));
        session.touch("10.0.0.1", lastUsedAt, Duration.ofHours(12));
        return withId(session);
    }

    private RefreshToken storedToken(UserSession session) {
        RefreshToken token = RefreshToken.issue(session, TokenHasher.sha256Hex(RAW_TOKEN));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256Hex(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        return token;
    }

    private static UserSession withId(UserSession session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID());
            session.setCreatedAt(NOW.minusSeconds(3600));
        }
        return session;
    }
}
