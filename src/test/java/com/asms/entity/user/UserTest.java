package com.asms.entity.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.support.TestUserCodes;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");

    @Test
    void createPending_shouldHaveNoPasswordAndPendingStatus() {
        User user = User.createPending(
                "a@gmail.com", "Nguyen Van A", TestUserCodes.codeFor("a@gmail.com"), SystemRole.USER, null);

        assertThat(user.isPendingActivation()).isTrue();
        assertThat(user.hasPassword()).isFalse();
    }

    @Test
    void activate_shouldSetPasswordStatusAndTimestamps() {
        User user = User.createPending(
                "a@gmail.com", "Nguyen Van A", TestUserCodes.codeFor("a@gmail.com"), SystemRole.USER, null);

        user.activate("hash", NOW);

        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getActivatedAt()).isEqualTo(NOW);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
    }

    @Test
    void recordFailedLogin_shouldCountConsecutiveFailures() {
        User user = activeUser();

        user.recordFailedLogin(NOW);
        int count = user.recordFailedLogin(NOW.plusSeconds(1));

        assertThat(count).isEqualTo(2);
        assertThat(user.getLastFailedLoginAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("BR-AUTH-04: counter restarts after an expired temporary lock")
    void recordFailedLogin_shouldRestartCounter_whenTemporaryLockExpired() {
        User user = activeUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW);
        }
        user.lockTemporarily(NOW.plus(Duration.ofMinutes(15)));

        int count = user.recordFailedLogin(NOW.plus(Duration.ofMinutes(16)));

        assertThat(count).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void isTemporarilyLocked_shouldBeTrueOnlyBeforeLockedUntil() {
        User user = activeUser();
        user.lockTemporarily(NOW.plusSeconds(60));

        assertThat(user.isTemporarilyLocked(NOW)).isTrue();
        assertThat(user.isTemporarilyLocked(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("BR-AUTH-04: successful login resets the counter and the temporary lock")
    void recordSuccessfulLogin_shouldResetCounterAndLock() {
        User user = activeUser();
        user.recordFailedLogin(NOW);
        user.lockTemporarily(NOW.plusSeconds(60));

        user.recordSuccessfulLogin(NOW);

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("FR-AUTH-16: password reset clears the temporary lock")
    void changePassword_shouldClearTemporaryLock() {
        User user = activeUser();
        user.recordFailedLogin(NOW);
        user.lockTemporarily(NOW.plusSeconds(60));

        user.changePassword("new-hash", NOW);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(user.isTemporarilyLocked(NOW)).isFalse();
        assertThat(user.getFailedLoginCount()).isZero();
    }

    private static User activeUser() {
        return User.createBootstrapAdmin(
                "admin@gmail.com", TestUserCodes.codeFor("admin@gmail.com"), "Admin", "hash", NOW.minusSeconds(3600));
    }
}
