package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.dto.auth.AuthTokenResponse;
import com.asms.dto.auth.IssuedSession;
import com.asms.dto.auth.LoginRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.LoginFailedReason;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.user.UserMapperImpl;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.service.user.AvatarUrlResolver;
import com.asms.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "UA");
    private static final String EMAIL = "a@gmail.com";
    private static final String USER_CODE = "SE170001";
    private static final String PASSWORD = "Secret123";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthEventPublisher events = mock(AuthEventPublisher.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(
                userRepository,
                sessionService,
                rateLimitService,
                passwordEncoder,
                events,
                new UserMapperImpl(new AvatarUrlResolver(TestProperties.appProperties())),
                TestProperties.appProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("FR-AUTH-01: the user ID is trimmed and uppercased before lookup")
    void login_shouldNormalizeUserCode() {
        User user = activeUser();
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(sessionService.openSession(user, true, CLIENT)).thenReturn(issued());

        authService.login(new LoginRequest("  se170001 ", PASSWORD, true), CLIENT);

        verify(userRepository).findByUserCodeForUpdate(USER_CODE);
    }

    @Test
    void login_shouldOpenSessionAndResetCounter_whenCredentialsValid() {
        User user = activeUser();
        user.recordFailedLogin(NOW.minusSeconds(10));
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(sessionService.openSession(user, false, CLIENT)).thenReturn(issued());

        IssuedSession result = authService.login(login(), CLIENT);

        assertThat(result.body().accessToken()).isEqualTo("access");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        assertThat(publishedEvent().type()).isEqualTo(AuthEventType.LOGIN_SUCCESS);
    }

    @Test
    @DisplayName("NFR-AUTH-06: unknown user ID still runs a password hash and returns the generic error")
    void login_shouldHashDummyPassword_whenUserCodeUnknown() {
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(passwordEncoder).matches(PASSWORD, "dummy-hash");
        assertThat(publishedEvent().metadata()).containsEntry("reason", LoginFailedReason.USER_NOT_FOUND);
    }

    @Test
    void login_shouldCountFailure_whenPasswordWrong() {
        User user = activeUser();
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(user.getFailedLoginCount()).isEqualTo((short) 1);
        verify(sessionService, never()).openSession(any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("BR-AUTH-04: the fifth wrong password locks the account for 15 minutes")
    void login_shouldLockTemporarily_whenFifthFailure() {
        User user = activeUser();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(NOW.minusSeconds(60));
        }
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
                    assertThat(e.getProperties()).containsEntry(BusinessException.RETRY_AFTER_SECONDS, 900L);
                });
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("UC-AUTH-01 4b: a temporarily locked account is rejected without checking the password")
    void login_shouldRejectWithoutPasswordCheck_whenTemporarilyLocked() {
        User user = activeUser();
        user.lockTemporarily(NOW.plusSeconds(120));
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
                    assertThat(e.getProperties()).containsEntry(BusinessException.RETRY_AFTER_SECONDS, 120L);
                });
        verify(passwordEncoder, never()).matches(PASSWORD, "hash");
    }

    @Test
    @DisplayName("BR-AUTH-03: a pending account has no password and always fails")
    void login_shouldFail_whenAccountPendingActivation() {
        User user = User.createPending(EMAIL, "Nguyen Van A", USER_CODE, SystemRole.USER, null);
        user.setId(UUID.randomUUID());
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(publishedEvent().metadata()).containsEntry("reason", LoginFailedReason.NO_PASSWORD);
    }

    @Test
    @DisplayName("UC-AUTH-01 6a: correct password on an account locked by an Admin")
    void login_shouldRejectLockedAccount_afterPasswordCheck() {
        User user = activeUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);
        when(userRepository.findByUserCodeForUpdate(USER_CODE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
        verify(sessionService, never()).openSession(any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("BR-AUTH-09: rate-limited login is logged and rejected before any lookup")
    void login_shouldRejectAndLog_whenRateLimited() {
        doThrow(BusinessException.retryAfter(ErrorCode.AUTH_RATE_LIMITED, 30))
                .when(rateLimitService)
                .check(eq(RateLimitPolicy.LOGIN_IP), anyString());

        assertThatThrownBy(() -> authService.login(login(), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_RATE_LIMITED);
        verify(userRepository, never()).findByUserCodeForUpdate(anyString());
        assertThat(publishedEvent().metadata()).containsEntry("reason", LoginFailedReason.RATE_LIMITED);
    }

    private static LoginRequest login() {
        return new LoginRequest(USER_CODE, PASSWORD, false);
    }

    private static User activeUser() {
        User user = User.createBootstrapAdmin(EMAIL, USER_CODE, "Nguyen Van A", "hash", NOW.minus(Duration.ofDays(1)));
        user.setId(UUID.randomUUID());
        return user;
    }

    private static IssuedSession issued() {
        return new IssuedSession(new AuthTokenResponse("access", 900, null), "refresh", null);
    }

    private AuthEventOccurred publishedEvent() {
        ArgumentCaptor<AuthEventOccurred> captor = ArgumentCaptor.forClass(AuthEventOccurred.class);
        verify(events, atLeastOnce()).publish(captor.capture());
        return captor.getValue();
    }
}
