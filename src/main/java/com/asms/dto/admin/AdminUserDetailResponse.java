package com.asms.dto.admin;

import com.asms.dto.catalog.SchoolRefResponse;
import com.asms.dto.user.GithubAccountResponse;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user as seen on the Admin detail page (schema AdminUserDetail, API-USER-08, API-USER-09).
 *
 * @param activeSessionCount sessions not revoked nor expired, at the time of reading
 * @param version to send back with an edit (BR-USER-14)
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AdminUserDetailResponse(
        UUID id,
        String email,
        String fullName,
        String userCode,
        @Nullable String avatarUrl,
        @Nullable String avatarThumbUrl,
        SystemRole systemRole,
        UserStatus status,
        @Nullable SchoolRefResponse school,
        @Nullable GithubAccountResponse github,
        @Nullable String bio,
        @Nullable Instant lastLoginAt,
        Instant createdAt,
        @Nullable Instant activatedAt,
        @Nullable Instant lockedAt,
        @Nullable String lockedReason,
        @Nullable UserRefResponse lockedBy,
        @Nullable UserRefResponse createdBy,
        int activeSessionCount,
        int failedLoginCount,
        @Nullable Instant lockedUntil,
        int version,
        Instant updatedAt) {}
