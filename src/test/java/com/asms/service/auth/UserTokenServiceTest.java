package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.repository.auth.UserTokenRepository;
import com.asms.support.TestProperties;
import com.asms.support.TestUserCodes;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final String RAW = "raw-token";

    private final UserTokenRepository repository = mock(UserTokenRepository.class);
    private final UserTokenService service =
            new UserTokenService(repository, TestProperties.appProperties(), Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createPending(
                "a@gmail.com", "Nguyen Van A", TestUserCodes.codeFor("a@gmail.com"), SystemRole.USER, null);
        user.setId(UUID.randomUUID());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("BR-AUTH-11: issuing invalidates older tokens and stores only the hash")
    void issue_shouldInvalidateOlderTokensAndStoreHash() {
        UserTokenService.IssuedToken issued = service.issue(user, UserTokenType.PASSWORD_RESET, null, "10.0.0.1");

        verify(repository).invalidateActive(user.getId(), UserTokenType.PASSWORD_RESET, NOW);
        ArgumentCaptor<UserToken> saved = ArgumentCaptor.forClass(UserToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.sha256Hex(issued.rawToken()));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("BR-AUTH-11: activation tokens last 72 hours")
    void issue_shouldUseActivationTtl() {
        UserTokenService.IssuedToken issued = service.issue(user, UserTokenType.ACTIVATION, null, null);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
    }

    @Test
    void requireUsable_shouldReturnToken_whenValid() {
        UserToken token = stored(UserTokenType.PASSWORD_RESET, NOW.minusSeconds(60));

        assertThat(service.requireUsable(RAW, UserTokenType.PASSWORD_RESET)).isSameAs(token);
    }

    @Test
    void requireUsable_shouldReportNotFound_whenTypeDiffers() {
        stored(UserTokenType.ACTIVATION, NOW.minusSeconds(60));

        assertReason(() -> service.requireUsable(RAW, UserTokenType.PASSWORD_RESET), "NOT_FOUND");
    }

    @Test
    void requireUsable_shouldReportUsed_whenAlreadyUsed() {
        stored(UserTokenType.PASSWORD_RESET, NOW.minusSeconds(60)).markUsed(NOW);

        assertReason(() -> service.requireUsable(RAW, UserTokenType.PASSWORD_RESET), "USED");
    }

    @Test
    void requireUsable_shouldReportExpired_whenPastExpiry() {
        stored(UserTokenType.PASSWORD_RESET, NOW.minus(Duration.ofMinutes(31)));

        assertReason(() -> service.requireUsable(RAW, UserTokenType.PASSWORD_RESET), "EXPIRED");
    }

    private UserToken stored(UserTokenType type, Instant issuedAt) {
        Duration ttl = type == UserTokenType.ACTIVATION ? Duration.ofHours(72) : Duration.ofMinutes(30);
        UserToken token = UserToken.issue(user, type, TokenHasher.sha256Hex(RAW), issuedAt, ttl, null, null);
        when(repository.findByTokenHash(TokenHasher.sha256Hex(RAW))).thenReturn(Optional.of(token));
        return token;
    }

    private static void assertReason(ThrowingCallable call, String reason) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
            assertThat(e.getProperties()).containsEntry(UserTokenService.REASON, reason);
        });
    }
}
