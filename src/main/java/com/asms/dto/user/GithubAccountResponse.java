package com.asms.dto.user;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Connected GitHub account (schema GithubAccount): public data copied from GitHub at the last sync.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record GithubAccountResponse(
        String login,
        @Nullable String name,
        String avatarUrl,
        String htmlUrl,
        @Nullable String bio,
        @Nullable String location,
        int publicRepos,
        int followers,
        int following,
        Instant githubCreatedAt,
        Instant connectedAt,
        Instant syncedAt) {}
