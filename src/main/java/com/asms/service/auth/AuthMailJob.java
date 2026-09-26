package com.asms.service.auth;

import com.asms.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Background job that renders and sends one Auth email (section 7.9) as HTML with a plain-text alternative.
 *
 * <p>Texts come from {@link EmailMessages} in the recipient's language (NFR14); templates hold only markup and keys.
 *
 * <p>NFR-AUTH-08: on a delivery failure the job schedules its own next attempt after 1, 5 and then 15 minutes, and
 * fails for good after the third retry. JobRunr's built-in retries are disabled because their exponential backoff
 * cannot express these delays.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Component
public class AuthMailJob {

    static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    private static final String TEMPLATE_PREFIX = "templates/email/";

    private final JavaMailSender mailSender;
    private final JobScheduler jobScheduler;
    private final EmailMessages messages;
    private final AppProperties props;
    private final Clock clock;
    private final SpringTemplateEngine templateEngine;

    public AuthMailJob(
            JavaMailSender mailSender,
            JobScheduler jobScheduler,
            EmailMessages messages,
            AppProperties props,
            Clock clock) {
        this.mailSender = mailSender;
        this.jobScheduler = jobScheduler;
        this.messages = messages;
        this.props = props;
        this.clock = clock;
        this.templateEngine = createTemplateEngine(messages);
    }

    /**
     * Sends the email; {@code attempt} is 0 for the first try.
     *
     * @throws IllegalStateException when the last retry fails, so that JobRunr marks the job as failed
     */
    @Job(name = "Send auth email", retries = 0)
    public void send(AuthMail mail, int attempt) {
        try {
            mailSender.send(render(mail));
        } catch (MailException | MessagingException e) {
            scheduleRetry(mail, attempt, e);
        }
    }

    MimeMessage render(AuthMail mail) throws MessagingException {
        Context context = new Context(mail.language().getLocale());
        mail.variables().forEach(context::setVariable);
        String name = mail.template().getTemplateName();
        String text = templateEngine.process(name + ".txt", context);
        String html = templateEngine.process(name + ".html", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.mail().from());
        helper.setTo(mail.to());
        helper.setSubject(messages.get(mail.template().subjectKey(), mail.language()));
        helper.setText(text, html);
        return message;
    }

    private void scheduleRetry(AuthMail mail, int attempt, Exception cause) {
        if (attempt >= RETRY_DELAYS.size()) {
            log.error("Giving up sending {} email after {} retries", mail.template(), attempt, cause);
            throw new IllegalStateException("Email delivery failed after " + attempt + " retries", cause);
        }
        int nextAttempt = attempt + 1;
        Instant retryAt = Instant.now(clock).plus(RETRY_DELAYS.get(attempt));
        log.warn(
                "Sending {} email failed, retry {} at {}: {}",
                mail.template(),
                nextAttempt,
                retryAt,
                cause.getMessage());
        jobScheduler.schedule(retryAt, () -> send(mail, nextAttempt));
    }

    private static SpringTemplateEngine createTemplateEngine(EmailMessages messages) {
        // Spring engine: expressions are evaluated with SpEL (plain Thymeleaf would need OGNL)
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateEngineMessageSource(messages.messageSource());
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
        return engine;
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_PREFIX);
        resolver.setTemplateMode(mode);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setOrder(order);
        resolver.setCheckExistence(true);
        return resolver;
    }
}
