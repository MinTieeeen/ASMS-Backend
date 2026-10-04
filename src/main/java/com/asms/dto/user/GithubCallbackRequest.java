package com.asms.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of API-USER-18: the {@code code} and {@code state} GitHub put in the callback URL.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record GithubCallbackRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 100) String state) {}
