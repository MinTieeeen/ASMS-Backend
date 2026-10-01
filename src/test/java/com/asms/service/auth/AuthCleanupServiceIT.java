package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.auth.RefreshToken;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserSession;
import com.asms.entity.user.User;
import com.asms.repository.auth.RefreshTokenRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.repository.user.UserRepository;
import com.asms.support.TestUserCodes;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** NFR-AUTH-09 on real PostgreSQL: old sessions go, their refresh tokens follow by cascade, active ones stay. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthCleanupServiceIT {

    @Autowired
    private AuthCleanupService cleanupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("NFR-AUTH-09: sessions revoked more than 7 days ago are deleted with their tokens")
    void purgeExpiredData_shouldDeleteOldSessionsOnly() {
        Instant now = Instant.now(clock);
        User user = userRepository.save(User.createBootstrapAdmin(
                "cleanup" + System.nanoTime() + "@gmail.com",
                TestUserCodes.codeFor("cleanup" + System.nanoTime() + "@gmail.com"),
                "Cleanup",
                "hash",
                now));
        UserSession old = session(user, now);
        old.revoke(SessionRevokeReason.LOGOUT, now.minus(Duration.ofDays(8)));
        sessionRepository.save(old);
        RefreshToken oldToken =
                refreshTokenRepository.save(RefreshToken.issue(old, TokenHasher.sha256Hex("old" + System.nanoTime())));
        UserSession active = session(user, now);

        cleanupService.purgeExpiredData();

        assertThat(sessionRepository.findById(old.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldToken.getId())).isEmpty();
        assertThat(sessionRepository.findById(active.getId())).isPresent();
    }

    private UserSession session(User user, Instant now) {
        return sessionRepository.save(
                UserSession.open(user, false, null, "UA", "10.0.0.1", now, Duration.ofHours(12), Duration.ofDays(30)));
    }
}
