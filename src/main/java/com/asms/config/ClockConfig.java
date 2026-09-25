package com.asms.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the shared UTC {@link Clock} (BR13).
 *
 * <p>Services that need the current time must inject {@link Clock} instead of calling {@code Instant.now()} so that
 * time-dependent logic stays testable.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
