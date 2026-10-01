package com.asms.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of API-AUTH-10: set the first password and activate the account (UC-AUTH-05).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record ActivateAccountRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(max = 128) String password) {}
