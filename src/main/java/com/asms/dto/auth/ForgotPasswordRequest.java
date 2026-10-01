package com.asms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of API-AUTH-06: request a password reset link (UC-AUTH-04).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email) {}
