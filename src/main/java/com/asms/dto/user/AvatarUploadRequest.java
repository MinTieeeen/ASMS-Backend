package com.asms.dto.user;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart body of API-USER-03: the original photo and the square chosen in the browser, in pixels of the upright
 * original. Bounds are checked by the service ({@code AVATAR_INVALID_IMAGE}), only presence here.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AvatarUploadRequest(
        @NotNull MultipartFile file,
        @NotNull Integer cropX,
        @NotNull Integer cropY,
        @NotNull Integer cropSize) {}
