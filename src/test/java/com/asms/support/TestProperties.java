package com.asms.support;

import com.asms.config.AppProperties;
import java.time.Duration;
import java.util.List;

/**
 * {@link AppProperties} with the default values of application.yml, for unit tests that do not start Spring.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class TestProperties {

    public static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private TestProperties() {}

    public static AppProperties appProperties() {
        return new AppProperties(
                FRONTEND_ORIGIN,
                "Asia/Ho_Chi_Minh",
                new AppProperties.Jwt("test-secret-key-must-be-at-least-32-characters", "asms", "k1"),
                new AppProperties.Cors(List.of(FRONTEND_ORIGIN)),
                new AppProperties.Auth(
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        Duration.ofHours(12),
                        Duration.ofDays(30),
                        10,
                        new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                        Duration.ofHours(72),
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(5),
                        Duration.ofDays(7),
                        "0 17 3 * * *",
                        Duration.ofDays(180),
                        new AppProperties.Cookie("rt", "/api/v1/auth", null, false)),
                new AppProperties.BootstrapAdmin(null, "admin", null, "Quản trị viên"),
                new AppProperties.Mail("ASMS <no-reply@asms.local>"));
    }
}
