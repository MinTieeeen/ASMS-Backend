package com.asms.dto.user;

import com.asms.dto.catalog.SchoolRefResponse;
import com.asms.entity.user.SystemRole;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Full profile of the signed-in user (schema UserProfile, API-USER-01, API-USER-02). Email, user ID and role are
 * read-only for the user.
 *
 * @param version to send back with the next update (BR-USER-14)
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String userCode,
        @Nullable SchoolRefResponse school,
        @Nullable GithubAccountResponse github,
        @Nullable String bio,
        @Nullable String avatarUrl,
        @Nullable String avatarThumbUrl,
        SystemRole systemRole,
        int version,
        Instant updatedAt) {}
