package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.entity.user.Language;
import com.asms.entity.user.User;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Queues the Auth emails (section 7.9) as background jobs (NFR-AUTH-08), in the recipient's preferred language
 * (NFR14).
 *
 * <p>Jobs are enqueued only after the surrounding transaction commits, so no email goes out for a rolled-back change
 * and the job always finds its data. Queueing failures are logged and never fail the API call (FR-AUTH-21).
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Service
public class AuthMailService {

    private static final String DATE_TIME_PATTERN_KEY = "email.format.dateTime";

    private final JobScheduler jobScheduler;
    private final AuthMailJob authMailJob;
    private final EmailMessages messages;
    private final AppProperties props;
    private final ZoneId zone;

    public AuthMailService(
            JobScheduler jobScheduler, AuthMailJob authMailJob, EmailMessages messages, AppProperties props) {
        this.jobScheduler = jobScheduler;
        this.authMailJob = authMailJob;
        this.messages = messages;
        this.props = props;
        this.zone = ZoneId.of(props.defaultTimezone());
    }

    /** Welcome email with the activation link, valid 72 hours (UC-AUTH-05, UC-AUTH-07). */
    public void sendActivation(User user, String rawToken, Instant expiresAt) {
        enqueueAfterCommit(new AuthMail(
                AuthMailTemplate.WELCOME_ACTIVATION,
                user.getEmail(),
                user.getLanguage(),
                Map.of(
                        "fullName", user.getFullName(),
                        "userCode", user.getUserCode(),
                        "link", link("/activate", rawToken),
                        "expiresAt", formatDateTime(expiresAt, user.getLanguage()))));
    }

    /** Password reset link, valid 30 minutes (UC-AUTH-04). */
    public void sendPasswordReset(User user, String rawToken, Instant expiresAt) {
        enqueueAfterCommit(new AuthMail(
                AuthMailTemplate.PASSWORD_RESET,
                user.getEmail(),
                user.getLanguage(),
                Map.of(
                        "fullName", user.getFullName(),
                        "link", link("/reset-password", rawToken),
                        "expiresAt", formatDateTime(expiresAt, user.getLanguage()))));
    }

    /** Notice that the password was changed or reset (FR-AUTH-16, FR-AUTH-19). */
    public void sendPasswordChanged(User user, Instant changedAt) {
        enqueueAfterCommit(new AuthMail(
                AuthMailTemplate.PASSWORD_CHANGED,
                user.getEmail(),
                user.getLanguage(),
                Map.of(
                        "fullName", user.getFullName(),
                        "changedAt", formatDateTime(changedAt, user.getLanguage()),
                        "forgotPasswordLink", props.frontendUrl() + "/forgot-password")));
    }

    /** Formats with the pattern of the recipient's language, in the application time zone (BR13). */
    String formatDateTime(Instant instant, Language language) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                        messages.get(DATE_TIME_PATTERN_KEY, language), language.getLocale())
                .withZone(zone);
        return formatter.format(instant);
    }

    private String link(String path, String rawToken) {
        return props.frontendUrl() + path + "?token=" + rawToken;
    }

    private void enqueueAfterCommit(AuthMail mail) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue(mail);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueue(mail);
            }
        });
    }

    private void enqueue(AuthMail mail) {
        try {
            jobScheduler.enqueue(() -> authMailJob.send(mail, 0));
        } catch (RuntimeException e) {
            log.error("Could not queue {} email", mail.template(), e);
        }
    }
}
