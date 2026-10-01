package com.asms.dto.user;

import com.asms.entity.user.Language;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/v1/users/me/language}: the language of emails and the UI (NFR14).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public record UpdateLanguageRequest(@NotNull Language language) {}
