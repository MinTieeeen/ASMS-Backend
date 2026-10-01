package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asms.repository.auth.AuthEventRepository;
import com.asms.repository.auth.RefreshTokenRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.repository.auth.UserTokenRepository;
import com.asms.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T03:17:00Z");

    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserTokenRepository userTokenRepository = mock(UserTokenRepository.class);
    private final AuthEventRepository authEventRepository = mock(AuthEventRepository.class);

    @Test
    @DisplayName("NFR-AUTH-09, NFR-AUTH-14: 7-day grace for sessions and tokens, 180 days for events")
    void purgeExpiredData_shouldUseConfiguredCutoffs() {
        Instant sevenDaysAgo = NOW.minus(Duration.ofDays(7));
        when(sessionRepository.deleteEndedBefore(sevenDaysAgo)).thenReturn(3);
        when(refreshTokenRepository.deleteExpiredBefore(sevenDaysAgo)).thenReturn(5);
        when(userTokenRepository.deleteExpiredBefore(sevenDaysAgo)).thenReturn(2);
        when(authEventRepository.deleteCreatedBefore(NOW.minus(Duration.ofDays(180))))
                .thenReturn(7);
        AuthCleanupService service = new AuthCleanupService(
                sessionRepository,
                refreshTokenRepository,
                userTokenRepository,
                authEventRepository,
                TestProperties.appProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        AuthCleanupService.Result result = service.purgeExpiredData();

        assertThat(result).isEqualTo(new AuthCleanupService.Result(3, 5, 2, 7));
    }
}
