package com.asms.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of API-AUTH-07 and API-AUTH-09: a one-time email token, sent in the body so it never reaches access logs.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record TokenRequest(@NotBlank @Size(max = 100) String token) {}
