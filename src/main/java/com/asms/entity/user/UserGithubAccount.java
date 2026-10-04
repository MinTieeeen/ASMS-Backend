package com.asms.entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * GitHub account a user connected through OAuth (FR-USER-27). Only a copy of the public profile is kept; the OAuth
 * token is used once and revoked. Disconnecting deletes the row.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Getter
@Entity
@Table(name = "user_github_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGithubAccount {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Numeric GitHub id: stays the same when the login changes; one ASMS user per GitHub account */
    @Column(name = "github_id", nullable = false, updatable = false)
    private long githubId;

    @Column(nullable = false, length = 39)
    private String login;

    @Nullable
    @Column(length = 255)
    private String name;

    @Column(name = "avatar_url", nullable = false, length = 500)
    private String avatarUrl;

    @Column(name = "html_url", nullable = false, length = 500)
    private String htmlUrl;

    @Nullable
    @Column(length = 500)
    private String bio;

    @Nullable
    @Column(length = 255)
    private String location;

    @Column(name = "public_repos", nullable = false)
    private int publicRepos;

    @Column(nullable = false)
    private int followers;

    @Column(nullable = false)
    private int following;

    @Column(name = "github_created_at", nullable = false)
    private Instant githubCreatedAt;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public static UserGithubAccount connect(UUID userId, GithubProfile profile, Instant now) {
        UserGithubAccount account = new UserGithubAccount();
        account.userId = userId;
        account.githubId = profile.githubId();
        account.connectedAt = now;
        account.sync(profile, now);
        return account;
    }

    /** Copies the latest public data read from GitHub (API-USER-18, API-USER-19). */
    public void sync(GithubProfile profile, Instant now) {
        login = profile.login();
        name = profile.name();
        avatarUrl = profile.avatarUrl();
        htmlUrl = profile.htmlUrl();
        bio = profile.bio();
        location = profile.location();
        publicRepos = profile.publicRepos();
        followers = profile.followers();
        following = profile.following();
        githubCreatedAt = profile.createdAt();
        syncedAt = now;
    }

    /** Public profile fields read from the GitHub API ({@code GET /user}). */
    public record GithubProfile(
            long githubId,
            String login,
            @Nullable String name,
            String avatarUrl,
            String htmlUrl,
            @Nullable String bio,
            @Nullable String location,
            int publicRepos,
            int followers,
            int following,
            Instant createdAt) {}
}
