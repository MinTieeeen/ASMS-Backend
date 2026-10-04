package com.asms.security;

import java.time.Duration;
import lombok.Getter;

/**
 * Rate limits of the Auth module and their Redis key prefixes (BR-AUTH-09, BR-AUTH-10, UC-AUTH-08; Redis sheet of the
 * table description). Subjects that are personal data (emails, user IDs) are hashed before they become part of a
 * key.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public enum RateLimitPolicy {
    LOGIN_IP("rl:login:ip:", 10, Duration.ofMinutes(1)),
    LOGIN_USER_CODE("rl:login:user-code:", 20, Duration.ofHours(1)),
    FORGOT_PASSWORD_EMAIL("rl:forgot:email:", 3, Duration.ofHours(1)),
    FORGOT_PASSWORD_IP("rl:forgot:ip:", 10, Duration.ofHours(1)),
    CHANGE_PASSWORD_USER("rl:change-password:user:", 10, Duration.ofHours(1)),
    ACTIVATION_RESEND_USER("rl:activation-resend:user:", 5, Duration.ofHours(1)),
    // Module 2 (BR-USER-16, sheet Redis)
    PROFILE_UPDATE_USER("rl:profile-update:user:", 30, Duration.ofHours(1)),
    AVATAR_UPLOAD_USER("rl:avatar-upload:user:", 10, Duration.ofHours(1)),
    ADMIN_WRITE("rl:admin-write:user:", 120, Duration.ofMinutes(1)),
    GITHUB_AUTHORIZE_USER("rl:github-authorize:user:", 10, Duration.ofHours(1)),
    GITHUB_REFRESH_USER("rl:github-refresh:user:", 1, Duration.ofMinutes(1));

    private final String keyPrefix;
    private final long capacity;
    private final Duration period;

    RateLimitPolicy(String keyPrefix, long capacity, Duration period) {
        this.keyPrefix = keyPrefix;
        this.capacity = capacity;
        this.period = period;
    }
}
