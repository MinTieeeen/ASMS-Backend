package com.asms.service.user;

import com.asms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the public avatar URLs from {@code users.avatar_key} (section 8.3 of the Module 2 spec): the key is an object
 * prefix and the files are {@code {key}-256.webp} and {@code {key}-64.webp}. A user without an avatar gets
 * {@code null}, and the frontend shows the initials (BR-USER-19).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Component
@RequiredArgsConstructor
public class AvatarUrlResolver {

    public static final int LARGE_SIZE = 256;
    public static final int THUMB_SIZE = 64;
    // MapStruct qualifiers. Every String -> String method here must carry one, otherwise MapStruct applies it to
    // every String property of the mappers that use this class.
    public static final String LARGE = "avatarLarge";
    public static final String THUMB = "avatarThumb";

    private final AppProperties props;

    /** 256 px avatar, used everywhere except lists */
    @Named(LARGE)
    @Nullable
    public String large(@Nullable String avatarKey) {
        return url(avatarKey, LARGE_SIZE);
    }

    /** 64 px avatar for lists */
    @Named(THUMB)
    @Nullable
    public String thumb(@Nullable String avatarKey) {
        return url(avatarKey, THUMB_SIZE);
    }

    /** Object key of one size, also used to write and delete the files */
    public static String objectKey(String avatarKey, int size) {
        return avatarKey + "-" + size + ".webp";
    }

    @Nullable
    private String url(@Nullable String avatarKey, int size) {
        String baseUrl = props.storage().publicBaseUrl();
        if (avatarKey == null || !StringUtils.hasText(baseUrl)) {
            return null;
        }
        return StringUtils.trimTrailingCharacter(baseUrl, '/') + "/" + objectKey(avatarKey, size);
    }
}
