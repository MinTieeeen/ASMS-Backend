package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.entity.user.Language;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailMessagesTest {

    private final EmailMessages messages = new EmailMessages();

    @Test
    @DisplayName("NFR14: Vietnamese and English bundles define exactly the same keys")
    void bundles_shouldHaveSameKeys() throws Exception {
        assertThat(load("i18n/email_en.properties").stringPropertyNames())
                .containsExactlyInAnyOrderElementsOf(
                        load("i18n/email.properties").stringPropertyNames());
    }

    @Test
    void get_shouldFormatArgumentsPerLanguage() {
        assertThat(messages.get("email.common.greeting", Language.VI, "An")).isEqualTo("Xin chào An,");
        assertThat(messages.get("email.common.greeting", Language.EN, "An")).isEqualTo("Hello An,");
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = EmailMessagesTest.class.getClassLoader().getResourceAsStream(path)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
