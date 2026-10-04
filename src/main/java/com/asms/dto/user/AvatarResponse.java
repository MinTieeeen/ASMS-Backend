package com.asms.dto.user;

/**
 * Response of API-USER-03: the URLs of the new avatar.
 *
 * @param avatarUrl 256 px, used everywhere except lists
 * @param avatarThumbUrl 64 px, for lists
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AvatarResponse(String avatarUrl, String avatarThumbUrl) {}
