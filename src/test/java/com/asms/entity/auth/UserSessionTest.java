package com.asms.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.entity.user.User;
import com.asms.support.TestUserCodes;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSessionTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final Duration ABSOLUTE_TTL = Duration.ofDays(30);

    @Test
    void open_shouldSetSlidingAndAbsoluteExpiry() {
        UserSession session = open(Duration.ofHours(12));

        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(NOW.plus(ABSOLUTE_TTL));
        assertThat(session.isActive(NOW)).isTrue();
    }

    @Test
    @DisplayName("BR-AUTH-06: refreshing never extends the session past its absolute expiry")
    void touch_shouldCapExpiryAtAbsoluteExpiry() {
        UserSession session = open(ABSOLUTE_TTL);
        Instant later = NOW.plus(Duration.ofDays(20));

        session.touch("10.0.0.2", later, ABSOLUTE_TTL);

        assertThat(session.getExpiresAt()).isEqualTo(session.getAbsoluteExpiresAt());
        assertThat(session.getLastUsedAt()).isEqualTo(later);
        assertThat(session.getLastIpAddress()).isEqualTo("10.0.0.2");
    }

    @Test
    void isActive_shouldBeFalse_whenSlidingExpiryPassed() {
        UserSession session = open(Duration.ofHours(12));

        assertThat(session.isActive(NOW.plus(Duration.ofHours(12)))).isFalse();
    }

    @Test
    void revoke_shouldKeepFirstReason_whenRevokedTwice() {
        UserSession session = open(Duration.ofHours(12));

        session.revoke(SessionRevokeReason.LOGOUT, NOW);
        session.revoke(SessionRevokeReason.LOGOUT_ALL, NOW.plusSeconds(5));

        assertThat(session.isActive(NOW)).isFalse();
        assertThat(session.getRevokeReason()).isEqualTo(SessionRevokeReason.LOGOUT);
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void open_shouldTruncateLongUserAgent() {
        String longUserAgent = "x".repeat(600);

        UserSession session = UserSession.open(
                user(), false, null, longUserAgent, "10.0.0.1", NOW, Duration.ofHours(12), ABSOLUTE_TTL);

        assertThat(session.getUserAgent()).hasSize(512);
    }

    private static UserSession open(Duration slidingTtl) {
        return UserSession.open(
                user(), true, "Chrome 128 · Windows 11", "UA", "10.0.0.1", NOW, slidingTtl, ABSOLUTE_TTL);
    }

    private static User user() {
        return User.createBootstrapAdmin(
                "admin@gmail.com", TestUserCodes.codeFor("admin@gmail.com"), "Admin", "hash", NOW);
    }
}
