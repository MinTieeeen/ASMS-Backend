package com.asms.mapper.user;

import com.asms.dto.catalog.SchoolRefResponse;
import com.asms.dto.user.GithubAccountResponse;
import com.asms.dto.user.PublicProfileResponse;
import com.asms.dto.user.UserProfileResponse;
import com.asms.entity.catalog.School;
import com.asms.entity.user.User;
import com.asms.entity.user.UserGithubAccount;
import com.asms.service.user.AvatarUrlResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps a user and its school and GitHub account to the profile responses of Module 2. Written by hand because the
 * avatar URLs come from configuration ({@link AvatarUrlResolver}). Call it inside a transaction: the school is lazy.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Component
@RequiredArgsConstructor
public class ProfileMapper {

    private final AvatarUrlResolver avatarUrls;

    public UserProfileResponse toProfile(User user, @Nullable UserGithubAccount github) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getUserCode(),
                toSchoolRef(user.getSchool()),
                toGithub(github),
                user.getBio(),
                avatarUrls.large(user.getAvatarKey()),
                avatarUrls.thumb(user.getAvatarKey()),
                user.getSystemRole(),
                user.getVersion(),
                user.getUpdatedAt());
    }

    public PublicProfileResponse toPublicProfile(User user, @Nullable UserGithubAccount github) {
        return new PublicProfileResponse(
                user.getId(),
                user.getFullName(),
                avatarUrls.large(user.getAvatarKey()),
                avatarUrls.thumb(user.getAvatarKey()),
                toSchoolRef(user.getSchool()),
                toGithub(github),
                user.getBio());
    }

    @Nullable
    public SchoolRefResponse toSchoolRef(@Nullable School school) {
        if (school == null) {
            return null;
        }
        return new SchoolRefResponse(
                school.getId(), school.getCode(), school.getName(), school.getShortName(), school.isActive());
    }

    @Nullable
    public GithubAccountResponse toGithub(@Nullable UserGithubAccount account) {
        if (account == null) {
            return null;
        }
        return new GithubAccountResponse(
                account.getLogin(),
                account.getName(),
                account.getAvatarUrl(),
                account.getHtmlUrl(),
                account.getBio(),
                account.getLocation(),
                account.getPublicRepos(),
                account.getFollowers(),
                account.getFollowing(),
                account.getGithubCreatedAt(),
                account.getConnectedAt(),
                account.getSyncedAt());
    }
}
