package com.asms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-specific settings bound from the {@code app.*} prefix in application.yml.
 *
 * <p>Every business setting must be declared here instead of scattering {@code @Value} annotations.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotBlank String defaultTimezone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors) {

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}
}
