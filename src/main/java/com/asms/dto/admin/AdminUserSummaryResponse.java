package com.asms.dto.admin;

import com.asms.entity.user.SystemRole;
import com.asms.entity.user.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One row of the Admin user list (schema AdminUserSummary, API-USER-07); also the result of the Admin actions on a
 * user (API-USER-11 to 13), so the list can update the row without reloading.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        String userCode,
        @Nullable String avatarThumbUrl,
        SystemRole systemRole,
        UserStatus status,
        @Nullable Instant lastLoginAt,
        Instant createdAt) {}
