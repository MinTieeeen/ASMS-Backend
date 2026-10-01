package com.asms.dto.admin;

import com.asms.entity.user.Language;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user as seen by an Admin: result of API-AUTH-12 and item of the user list. Never exposes the password hash or the
 * lock reason.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String userCode,
        SystemRole systemRole,
        UserStatus status,
        Language language,
        Instant createdAt,
        @Nullable Instant activatedAt,
        @Nullable Instant lastLoginAt) {}
