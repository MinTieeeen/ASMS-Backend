package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.entity.user.Language;
import com.asms.support.TestProperties;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class AuthMailJobTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");
    private static final String LINK = "http://localhost:5173/reset-password?token=abc";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final JobScheduler jobScheduler = mock(JobScheduler.class);

    private AuthMailJob job;

    @BeforeEach
    void setUp() {
        job = new AuthMailJob(
                mailSender,
                jobScheduler,
                new EmailMessages(),
                TestProperties.appProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @DisplayName("NFR14: Vietnamese texts come from email.properties")
    void render_shouldUseVietnamese_whenLanguageVi() throws Exception {
        MimeMessage message = job.render(mail(AuthMailTemplate.PASSWORD_RESET, Language.VI));

        assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu");
        assertThat(part(message, "text/html"))
                .contains("Xin chào Nguyen Van A,")
                .contains("lang=\"vi\"");
        assertThat(part(message, "text/plain"))
                .contains("Xin chào Nguyen Van A,")
                .contains(LINK);
    }

    @Test
    @DisplayName("NFR14: English texts come from email_en.properties")
    void render_shouldUseEnglish_whenLanguageEn() throws Exception {
        MimeMessage message = job.render(mail(AuthMailTemplate.PASSWORD_RESET, Language.EN));

        assertThat(message.getSubject()).isEqualTo("Reset your password");
        assertThat(part(message, "text/html"))
                .contains("Hello Nguyen Van A,")
                .contains("Reset password")
                .contains("lang=\"en\"")
                .doesNotContain("Xin chào");
        assertThat(part(message, "text/plain")).contains("Hello Nguyen Van A,").contains(LINK);
    }

    @ParameterizedTest
    @EnumSource(AuthMailTemplate.class)
    @DisplayName("Section 7.9: every template renders in both languages with the copyable link")
    void render_shouldRenderEveryTemplateInEveryLanguage(AuthMailTemplate template) throws Exception {
        for (Language language : Language.values()) {
            MimeMessage message = job.render(mail(template, language));

            assertThat(message.getSubject()).isNotBlank().doesNotContain("??");
            assertThat(part(message, "text/html")).doesNotContain("??email.").contains("href=");
            assertThat(part(message, "text/plain")).doesNotContain("??email.").contains("http://localhost:5173/");
        }
    }

    @Test
    @DisplayName("NFR-AUTH-08: a failed delivery schedules a retry one minute later")
    void send_shouldScheduleRetry_whenDeliveryFails() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        job.send(mail(AuthMailTemplate.PASSWORD_RESET, Language.VI), 0);

        verify(jobScheduler).schedule(eq(NOW.plus(Duration.ofMinutes(1))), any(JobLambda.class));
    }

    @Test
    @DisplayName("NFR-AUTH-08: after the third retry the job fails for good")
    void send_shouldFail_afterLastRetry() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> job.send(mail(AuthMailTemplate.PASSWORD_RESET, Language.VI), 3))
                .isInstanceOf(IllegalStateException.class);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryDelays_shouldBeOneFiveAndFifteenMinutes() {
        assertThat(AuthMailJob.RETRY_DELAYS)
                .containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    }

    private static AuthMail mail(AuthMailTemplate template, Language language) {
        return new AuthMail(
                template,
                "a@gmail.com",
                language,
                Map.of(
                        "fullName", "Nguyen Van A",
                        "link", LINK,
                        "expiresAt", "08:30 26/09/2026",
                        "changedAt", "08:30 26/09/2026",
                        "forgotPasswordLink", "http://localhost:5173/forgot-password"));
    }

    /** Decoded content of the first MIME part with this content type. */
    private static String part(MimeMessage message, String contentType) throws Exception {
        // Content-Type headers of the parts are only written by saveChanges()
        message.saveChanges();
        return findPart(message, contentType);
    }

    private static String findPart(Part part, String contentType) throws Exception {
        if (part.isMimeType(contentType)) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String found = findPart(child, contentType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
