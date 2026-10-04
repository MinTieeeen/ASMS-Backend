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
        @Valid @NotNull BootstrapAdmin bootstrapAdmin,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Storage storage) {

    /**
     * S3-compatible object storage of avatars (Cloudflare R2 or AWS S3, section 8.3 of the Module 2 spec). Every value
     * is optional so that the application starts without it; avatar uploads then fail until it is configured.
     *
     * @param publicBaseUrl base of the public avatar URLs, e.g. https://cdn.asms.vn (R2 public bucket or custom domain)
     */
    public record Storage(
            @Nullable String endpoint,
            @Nullable String region,
            @Nullable String bucket,
            @Nullable String accessKey,
            @Nullable String secretKey,
            @Nullable String publicBaseUrl) {}

    /** Outgoing email settings (section 7.9). {@code frontendUrl} is the base of every link in emails. */
    public record Mail(@NotBlank String from) {}

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
            @NotBlank String cleanupCron,
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

    /**
     * First Admin account created on startup when no Admin exists (FR-AUTH-24). {@code userCode} is the code the
     * Admin signs in with.
     */
    public record BootstrapAdmin(
            @Nullable String email,
            @NotBlank String userCode,
            @Nullable String password,
            @NotBlank String fullName) {

        public boolean isConfigured() {
            return StringUtils.hasText(email) && StringUtils.hasText(password);
        }
    }
}
