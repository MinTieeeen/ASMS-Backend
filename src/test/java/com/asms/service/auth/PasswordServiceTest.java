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

import com.asms.dto.auth.ChangePasswordRequest;
import com.asms.dto.auth.ForgotPasswordRequest;
import com.asms.dto.auth.ResetPasswordRequest;
import com.asms.dto.auth.ResetTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitService;
import com.asms.support.TestProperties;
import com.asms.support.TestUserCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "UA");
    private static final String EMAIL = "a@gmail.com";
    private static final String CURRENT = "Current123";
    private static final String NEW = "NewSecret456";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final AuthMailService mailService = mock(AuthMailService.class);
    private final AuthEventPublisher events = mock(AuthEventPublisher.class);

    private PasswordService passwordService;
    private User user;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(
                userRepository,
                userTokenService,
                sessionService,
                new PasswordPolicyValidator(),
                passwordEncoder,
                rateLimitService,
                mailService,
                events,
                TestProperties.appProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.createBootstrapAdmin(
                EMAIL, TestUserCodes.codeFor(EMAIL), "Nguyen Van A", "current-hash", NOW.minus(Duration.ofDays(1)));
        user.setId(UUID.randomUUID());
        when(passwordEncoder.matches(CURRENT, "current-hash")).thenReturn(true);
        when(passwordEncoder.encode(NEW)).thenReturn("new-hash");
        when(rateLimitService.tryConsume(any(), anyString())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("UC-AUTH-04: an active account gets a reset link")
    void requestPasswordReset_shouldSendResetLink_whenAccountActive() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userTokenService.issue(user, UserTokenType.PASSWORD_RESET, null, "10.0.0.1"))
                .thenReturn(new UserTokenService.IssuedToken("raw", NOW.plusSeconds(1800)));

        passwordService.requestPasswordReset(new ForgotPasswordRequest(" A@gmail.com "), CLIENT);

        verify(mailService).sendPasswordReset(user, "raw", NOW.plusSeconds(1800));
    }

    @Test
    @DisplayName("FR-AUTH-13: a pending account gets a new activation link instead")
    void requestPasswordReset_shouldSendActivation_whenAccountPending() {
        User pending = User.createPending(EMAIL, "Nguyen Van A", TestUserCodes.codeFor(EMAIL), SystemRole.USER, null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
        when(userTokenService.issue(pending, UserTokenType.ACTIVATION, null, "10.0.0.1"))
                .thenReturn(new UserTokenService.IssuedToken("raw", NOW.plusSeconds(3600)));

        passwordService.requestPasswordReset(new ForgotPasswordRequest(EMAIL), CLIENT);

        verify(mailService).sendActivation(pending, "raw", NOW.plusSeconds(3600));
        verify(mailService, never()).sendPasswordReset(any(), anyString(), any());
    }

    @Test
    @DisplayName("FR-AUTH-12: unknown email completes silently")
    void requestPasswordReset_shouldSendNothing_whenEmailUnknown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        passwordService.requestPasswordReset(new ForgotPasswordRequest(EMAIL), CLIENT);

        verify(userTokenService, never()).issue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("BR-AUTH-10: over the limit, nothing is sent and no error is returned")
    void requestPasswordReset_shouldSendNothing_whenRateLimited() {
        when(rateLimitService.tryConsume(any(), anyString())).thenReturn(false);

        passwordService.requestPasswordReset(new ForgotPasswordRequest(EMAIL), CLIENT);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void validateResetToken_shouldReturnMaskedEmail() {
        UserToken token = resetToken();

        ResetTokenInfoResponse info = passwordService.validateResetToken(new TokenRequest("raw"));

        assertThat(info.maskedEmail()).isEqualTo("a***@gmail.com");
        assertThat(info.expiresAt()).isEqualTo(token.getExpiresAt());
    }

    @Test
    @DisplayName("FR-AUTH-16: reset changes the password, uses the token, revokes every session and notifies")
    void resetPassword_shouldChangePasswordAndRevokeAllSessions() {
        UserToken token = resetToken();
        user.lockTemporarily(NOW.plusSeconds(600));

        passwordService.resetPassword(new ResetPasswordRequest("raw", NEW), CLIENT);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isTemporarilyLocked(NOW)).isFalse();
        verify(userTokenService).consume(token);
        verify(sessionService).revokeAllForUser(user.getId(), SessionRevokeReason.PASSWORD_RESET, null);
        verify(mailService).sendPasswordChanged(user, NOW);
    }

    @Test
    @DisplayName("BR-AUTH-12: the new password must differ from the current one")
    void resetPassword_shouldReject_whenSameAsCurrent() {
        resetToken();
        when(passwordEncoder.matches(NEW, "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> passwordService.resetPassword(new ResetPasswordRequest("raw", NEW), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);
    }

    @Test
    void resetPassword_shouldReject_whenPolicyViolated() {
        resetToken();

        assertThatThrownBy(() -> passwordService.resetPassword(new ResetPasswordRequest("raw", "short"), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_POLICY);
    }

    @Test
    @DisplayName("FR-AUTH-19: change keeps the current session and logs out the others")
    void changePassword_shouldKeepCurrentSession() {
        UUID sessionId = UUID.randomUUID();

        passwordService.changePassword(user.getId(), sessionId, new ChangePasswordRequest(CURRENT, NEW), CLIENT);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(sessionService).revokeAllForUser(user.getId(), SessionRevokeReason.PASSWORD_CHANGED, sessionId);
        verify(mailService).sendPasswordChanged(user, NOW);
    }

    @Test
    void changePassword_shouldReject_whenCurrentPasswordWrong() {
        assertThatThrownBy(() -> passwordService.changePassword(
                        user.getId(), UUID.randomUUID(), new ChangePasswordRequest("Wrong123", NEW), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_CURRENT_PASSWORD_WRONG);
        assertThat(user.getFailedLoginCount()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("UC-AUTH-06 3a: the fifth wrong current password locks the account and ends this session")
    void changePassword_shouldLockAndRevokeCurrentSession_afterFiveFailures() {
        UUID sessionId = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(NOW.minusSeconds(30));
        }

        assertThatThrownBy(() -> passwordService.changePassword(
                        user.getId(), sessionId, new ChangePasswordRequest("Wrong123", NEW), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
        assertThat(user.isTemporarilyLocked(NOW)).isTrue();
        verify(sessionService).revokeSession(eq(sessionId), eq(SessionRevokeReason.ACCOUNT_LOCKED));
    }

    private UserToken resetToken() {
        UserToken token = UserToken.issue(
                user, UserTokenType.PASSWORD_RESET, "h".repeat(64), NOW, Duration.ofMinutes(30), null, null);
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        when(userTokenService.requireUsable("raw", UserTokenType.PASSWORD_RESET))
                .thenReturn(token);
        return token;
    }
}
