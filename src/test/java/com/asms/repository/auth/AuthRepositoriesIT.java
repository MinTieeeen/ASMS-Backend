package com.asms.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.auth.AuthEvent;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.RefreshToken;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserSession;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.support.TestUserCodes;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Checks the Flyway schema (V2-V6) against the entities and the custom queries of the Auth repositories. */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthRepositoriesIT {

    private static final Duration TWELVE_HOURS = Duration.ofHours(12);
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void findByEmail_shouldReturnSavedUser() {
        userRepository.saveAndFlush(
                User.createPending("a@gmail.com", "Nguyen Van A", "21120001", SystemRole.USER, null));

        assertThat(userRepository.findByEmail("a@gmail.com")).isPresent();
        assertThat(userRepository.existsByUserCode("21120001")).isTrue();
        assertThat(userRepository.existsBySystemRole(SystemRole.ADMIN)).isFalse();
    }

    @Test
    void findActiveByUserId_shouldExcludeRevokedSessionsAndOrderByLastUse() {
        Instant now = Instant.now(clock);
        User user = savedActiveUser("b@gmail.com");
        UserSession older = saveSession(user, now.minusSeconds(60));
        UserSession newer = saveSession(user, now);
        UserSession revoked = saveSession(user, now);
        revoked.revoke(SessionRevokeReason.LOGOUT, now);
        entityManager.flush();

        var active = sessionRepository.findActiveByUserId(user.getId(), now);

        assertThat(active).extracting(UserSession::getId).containsExactly(newer.getId(), older.getId());
        assertThat(sessionRepository.findByIdAndUser_Id(newer.getId(), user.getId()))
                .isPresent();
    }

    @Test
    void findByTokenHashForUpdate_shouldFindRotatedChain() {
        Instant now = Instant.now(clock);
        UserSession session = saveSession(savedActiveUser("c@gmail.com"), now);
        RefreshToken first = refreshTokenRepository.save(RefreshToken.issue(session, "a".repeat(64)));
        RefreshToken second = refreshTokenRepository.save(RefreshToken.issue(session, "b".repeat(64)));
        first.markRotated(second.getId(), now);
        entityManager.flush();
        entityManager.clear();

        RefreshToken found =
                refreshTokenRepository.findByTokenHashForUpdate("a".repeat(64)).orElseThrow();

        assertThat(found.isUsed()).isTrue();
        assertThat(found.getReplacedById()).isEqualTo(second.getId());
    }

    @Test
    void invalidateActive_shouldInvalidateOnlyUnusedTokensOfSameType() {
        Instant now = Instant.now(clock);
        User user = savedActiveUser("d@gmail.com");
        UserToken reset = userTokenRepository.save(UserToken.issue(
                user, UserTokenType.PASSWORD_RESET, "c".repeat(64), now, Duration.ofMinutes(30), null, null));
        userTokenRepository.save(
                UserToken.issue(user, UserTokenType.ACTIVATION, "d".repeat(64), now, Duration.ofHours(72), null, null));

        int invalidated = userTokenRepository.invalidateActive(user.getId(), UserTokenType.PASSWORD_RESET, now);
        entityManager.clear();

        assertThat(invalidated).isEqualTo(1);
        assertThat(userTokenRepository
                        .findByTokenHash(reset.getTokenHash())
                        .orElseThrow()
                        .isConsumed())
                .isTrue();
        assertThat(userTokenRepository
                        .findByTokenHash("d".repeat(64))
                        .orElseThrow()
                        .isConsumed())
                .isFalse();
    }

    @Test
    void save_shouldStoreEventWithJsonMetadata() {
        AuthEvent event = authEventRepository.saveAndFlush(AuthEvent.of(
                AuthEventType.LOGIN_FAILED,
                null,
                null,
                "unknown@gmail.com",
                null,
                "10.0.0.1",
                "UA",
                "{\"reason\":\"USER_NOT_FOUND\"}"));

        assertThat(event.getId()).isNotNull();
    }

    @Test
    void deleteEndedBefore_shouldRemoveRevokedSessionsOlderThanCutoff() {
        Instant now = Instant.now(clock);
        UserSession session = saveSession(savedActiveUser("e@gmail.com"), now);
        session.revoke(SessionRevokeReason.LOGOUT, now.minus(Duration.ofDays(8)));
        entityManager.flush();

        int deleted = sessionRepository.deleteEndedBefore(now.minus(Duration.ofDays(7)));

        assertThat(deleted).isEqualTo(1);
    }

    private User savedActiveUser(String email) {
        return userRepository.save(User.createBootstrapAdmin(
                email, TestUserCodes.codeFor(email), "Test User", "hash", Instant.now(clock)));
    }

    private UserSession saveSession(User user, Instant lastUsedAt) {
        UserSession session =
                UserSession.open(user, false, null, "UA", "10.0.0.1", Instant.now(clock), TWELVE_HOURS, THIRTY_DAYS);
        session.touch("10.0.0.1", lastUsedAt, TWELVE_HOURS);
        return sessionRepository.save(session);
    }
}
