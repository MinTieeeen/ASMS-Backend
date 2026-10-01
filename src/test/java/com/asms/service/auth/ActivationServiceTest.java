package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.dto.auth.ActivateAccountRequest;
import com.asms.dto.auth.ActivationTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
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

class ActivationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "UA");

    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthEventPublisher events = mock(AuthEventPublisher.class);

    private ActivationService activationService;
    private User user;
    private UserToken token;

    @BeforeEach
    void setUp() {
        activationService = new ActivationService(
                userTokenService,
                new PasswordPolicyValidator(),
                passwordEncoder,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.createPending(
                "a@gmail.com",
                "Nguyen Van A",
                TestUserCodes.codeFor("a@gmail.com"),
                SystemRole.USER,
                UUID.randomUUID());
        user.setId(UUID.randomUUID());
        token = UserToken.issue(
                user, UserTokenType.ACTIVATION, "h".repeat(64), NOW.minusSeconds(60), Duration.ofHours(72), null, null);
        when(userTokenService.find("raw", UserTokenType.ACTIVATION)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Secret123")).thenReturn("hash");
    }

    @Test
    void validateActivationToken_shouldReturnAccountDetails() {
        ActivationTokenInfoResponse info = activationService.validateActivationToken(new TokenRequest("raw"));

        assertThat(info.email()).isEqualTo("a@gmail.com");
        assertThat(info.userCode()).isEqualTo(TestUserCodes.codeFor("a@gmail.com"));
        assertThat(info.fullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    @DisplayName("FR-AUTH-17: activation sets the password and makes the account active")
    void activate_shouldActivateAccountAndUseToken() {
        activationService.activate(new ActivateAccountRequest("raw", "Secret123"), CLIENT);

        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(userTokenService).consume(token);
    }

    @Test
    @DisplayName("UC-AUTH-05 2b: an account already active is reported as such, not as an invalid link")
    void validateActivationToken_shouldReportAlreadyActive() {
        user.activate("hash", NOW.minusSeconds(10));
        token.markUsed(NOW.minusSeconds(10));

        assertThatThrownBy(() -> activationService.validateActivationToken(new TokenRequest("raw")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_ALREADY_ACTIVE);
    }

    @Test
    void validateActivationToken_shouldReportInvalid_whenTokenUnknown() {
        when(userTokenService.find("other", UserTokenType.ACTIVATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activationService.validateActivationToken(new TokenRequest("other")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void activate_shouldReject_whenTokenExpired() {
        doThrow(UserTokenService.invalid(UserTokenService.InvalidReason.EXPIRED))
                .when(userTokenService)
                .checkUsable(token);

        assertThatThrownBy(() -> activationService.activate(new ActivateAccountRequest("raw", "Secret123"), CLIENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getProperties())
                        .containsEntry(UserTokenService.REASON, "EXPIRED"));
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void activate_shouldReject_whenPasswordViolatesPolicy() {
        assertThatThrownBy(() -> activationService.activate(new ActivateAccountRequest("raw", "short"), CLIENT))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_POLICY);
        assertThat(user.isActive()).isFalse();
    }
}
