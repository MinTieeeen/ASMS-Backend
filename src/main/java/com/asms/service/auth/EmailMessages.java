package com.asms.service.auth;

import com.asms.entity.user.Language;
import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Translated email texts from {@code i18n/email.properties} (Vietnamese, default) and {@code i18n/email_en.properties}
 * (English). Templates only reference keys ({@code #{email.passwordReset.title}}), never literal text (NFR14).
 *
 * <p>Kept separate from the application {@code messageSource} bean on purpose, so that email texts never mix with
 * other message bundles.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
public class EmailMessages {

    private static final String BASENAME = "i18n/email";

    private final ResourceBundleMessageSource messageSource = createMessageSource();

    /** Message source handed to the Thymeleaf engine to resolve {@code #{...}} expressions. */
    public MessageSource messageSource() {
        return messageSource;
    }

    public String get(String key, Language language, Object... args) {
        return messageSource.getMessage(key, args, language.getLocale());
    }

    private static ResourceBundleMessageSource createMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename(BASENAME);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Unknown languages fall back to email.properties (Vietnamese), never to the server's OS locale
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
