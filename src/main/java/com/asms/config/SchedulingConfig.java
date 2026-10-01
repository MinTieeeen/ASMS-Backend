package com.asms.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs with ShedLock distributed locks stored in Redis, so that each job runs on one
 * instance only. Keys look like {@code shedlock:asms-backend:auth-cleanup} (Redis sheet of the table description).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfig {

    private static final String KEY_PREFIX = "shedlock";

    @Bean
    LockProvider lockProvider(
            RedisConnectionFactory connectionFactory, @Value("${spring.application.name}") String applicationName) {
        return new RedisLockProvider(connectionFactory, applicationName, KEY_PREFIX);
    }
}
