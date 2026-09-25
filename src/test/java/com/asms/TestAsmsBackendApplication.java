package com.asms;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against Testcontainers instead of docker compose: {@code ./mvnw spring-boot:test-run}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public class TestAsmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(AsmsBackendApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
