package com.asms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body of API-AUTH-01. The email is normalized by the service; the password is never trimmed and the password policy
 * is not applied here (UC-AUTH-01).
 *
 * <p>{@code rememberMe} is a nullable {@code Boolean}: Jackson 3 rejects a missing primitive, and an omitted field must
 * simply mean "do not remember".
 *
 * @author MinhTien
 * @version 1.0.1
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String password,
        @Nullable Boolean rememberMe) {

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
