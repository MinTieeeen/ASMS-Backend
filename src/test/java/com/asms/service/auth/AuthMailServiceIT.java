package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.user.User;
import java.time.Instant;
import java.util.List;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.AmountRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Checks that emails become JobRunr jobs in PostgreSQL, only after commit (NFR-AUTH-08). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthMailServiceIT {

    @Autowired
    private AuthMailService authMailService;

    @Autowired
    private StorageProvider storageProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("NFR-AUTH-08: the email job is stored with its arguments readable back")
    void sendPasswordChanged_shouldEnqueueJob() {
        String email = "queued" + System.nanoTime() + "@gmail.com";

        authMailService.sendPasswordChanged(user(email), Instant.now());

        assertThat(enqueuedMails()).anySatisfy(mail -> {
            assertThat(mail.to()).isEqualTo(email);
            assertThat(mail.template()).isEqualTo(AuthMailTemplate.PASSWORD_CHANGED);
            assertThat(mail.variables()).containsKey("changedAt");
        });
    }

    @Test
    @DisplayName("No email is queued for a transaction that rolls back")
    void sendPasswordChanged_shouldNotEnqueue_whenTransactionRollsBack() {
        String email = "rolledback" + System.nanoTime() + "@gmail.com";

        transactionTemplate.executeWithoutResult(status -> {
            authMailService.sendPasswordChanged(user(email), Instant.now());
            status.setRollbackOnly();
        });

        assertThat(enqueuedMails()).noneSatisfy(mail -> assertThat(mail.to()).isEqualTo(email));
    }

    private List<AuthMail> enqueuedMails() {
        return storageProvider.getJobList(StateName.ENQUEUED, new AmountRequest("updatedAt:ASC", 100)).stream()
                .map(Job::getJobDetails)
                .map(details -> (AuthMail) details.getJobParameterValues()[0])
                .toList();
    }

    private static User user(String email) {
        return User.createBootstrapAdmin(email, "Nguyen Van A", "hash", Instant.now());
    }
}
