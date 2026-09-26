package com.asms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Application-specific settings bound from the {@code app.*} prefix in application.yml.
 *
 * <p>Every business setting must be declared here instead of scattering {@code @Value} annotations. The {@code auth}
 * group mirrors section 7.10 of the Auth specification.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotBlank String defaultTimezone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Auth auth,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin) {

    /** Signing settings of the access token (section 7.2). The secret must be at least 256 bits. */
    public record Jwt(
            @NotBlank @Size(min = 32) String secret,
            @NotBlank String issuer,
            @NotBlank String keyId) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    /** Session, token and lockout settings of the Auth module (BR-AUTH-04 to BR-AUTH-11). */
    public record Auth(
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTtlRemember,
            @NotNull Duration refreshTtlDefault,
            @NotNull Duration sessionAbsoluteTtl,
            @Min(1) int maxSessionsPerUser,
            @Valid @NotNull Lockout lockout,
            @NotNull Duration activationTokenTtl,
            @NotNull Duration resetTokenTtl,
            @NotNull Duration refreshRaceWindow,
            @NotNull Duration cleanupGracePeriod,
            @NotNull Duration eventRetention,
            @Valid @NotNull Cookie cookie) {}

    public record Lockout(@Min(1) int maxAttempts, @NotNull Duration duration) {}

    /** Refresh token cookie (NFR-AUTH-05). {@code secure} is only disabled for plain-HTTP local development. */
    public record Cookie(
            @NotBlank String name,
            @NotBlank String path,
            @Nullable String domain,
            boolean secure) {

        public boolean hasDomain() {
            return StringUtils.hasText(domain);
        }
    }

    /** First Admin account created on startup when no Admin exists (FR-AUTH-24). */
    public record BootstrapAdmin(
            @Nullable String email,
            @Nullable String password,
            @NotBlank String fullName) {

        public boolean isConfigured() {
            return StringUtils.hasText(email) && StringUtils.hasText(password);
        }
    }
}
