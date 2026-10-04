package com.asms.dto.user;

import com.asms.dto.catalog.SchoolRefResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public profile card of another user (schema PublicProfile, API-USER-05). A separate DTO on purpose: it never carries
 * the email, user ID, role or status (NFR-USER-07).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record PublicProfileResponse(
        UUID id,
        String fullName,
        @Nullable String avatarUrl,
        @Nullable String avatarThumbUrl,
        @Nullable SchoolRefResponse school,
        @Nullable GithubAccountResponse github,
        @Nullable String bio) {}
