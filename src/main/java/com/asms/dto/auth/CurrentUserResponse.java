package com.asms.dto.auth;

import com.asms.entity.user.Language;
import com.asms.entity.user.SystemRole;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in user (API-AUTH-05), also embedded in login and refresh responses.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record CurrentUserResponse(
        UUID id,
        String email,
        String fullName,
        @Nullable String avatarUrl,
        String userCode,
        SystemRole systemRole,
        Language language,
        @Nullable Instant passwordChangedAt) {}
