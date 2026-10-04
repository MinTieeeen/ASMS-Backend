package com.asms.controller.user;

import com.asms.constant.ApiPaths;
import com.asms.dto.user.AvatarResponse;
import com.asms.dto.user.AvatarUploadRequest;
import com.asms.dto.user.PublicProfileResponse;
import com.asms.dto.user.UpdateProfileRequest;
import com.asms.dto.user.UserProfileResponse;
import com.asms.security.SecurityUtils;
import com.asms.service.user.AvatarService;
import com.asms.service.user.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile of the signed-in user and public profile cards (SCR-USER-01, SCR-USER-03).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@RestController
@RequestMapping(ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "Profiles", description = "Own profile and public profile cards")
public class ProfileController {

    private final ProfileService profileService;
    private final AvatarService avatarService;

    @GetMapping(ApiPaths.Users.ME_PROFILE)
    @Operation(operationId = "getMyProfile", summary = "My profile (API-USER-01)")
    public UserProfileResponse getMyProfile() {
        return profileService.getMyProfile(SecurityUtils.getCurrentUserId());
    }

    @PatchMapping(ApiPaths.Users.ME_PROFILE)
    @Operation(
            operationId = "updateMyProfile",
            summary = "Edit my profile (API-USER-02)",
            description = "Send only the fields to change, plus the version read. A field sent as null is cleared.")
    @ApiResponse(responseCode = "200", description = "Profile saved; version increased")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (NOT_ALLOWED, NOT_AVAILABLE, TOO_LONG...)")
    @ApiResponse(responseCode = "409", description = "USER_VERSION_CONFLICT")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED: more than 30 saves per hour")
    public UserProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateMyProfile(SecurityUtils.getCurrentUserId(), request);
    }

    @PutMapping(value = ApiPaths.Users.ME_AVATAR, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "uploadMyAvatar",
            summary = "Upload my avatar (API-USER-03)",
            description = "JPEG, PNG or WebP up to 5 MB, 128 to 6000 px per side; crop square in pixels of the"
                    + " upright original. Stored as 256 and 64 px WebP.")
    @ApiResponse(responseCode = "200", description = "New avatar URLs")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or AVATAR_INVALID_IMAGE")
    @ApiResponse(responseCode = "413", description = "AVATAR_TOO_LARGE")
    @ApiResponse(responseCode = "415", description = "AVATAR_UNSUPPORTED_TYPE")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED: more than 10 uploads per hour")
    public AvatarResponse uploadMyAvatar(@Valid @ModelAttribute AvatarUploadRequest request) {
        return avatarService.upload(
                SecurityUtils.getCurrentUserId(), request.file(), request.cropX(), request.cropY(), request.cropSize());
    }

    @DeleteMapping(ApiPaths.Users.ME_AVATAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeMyAvatar", summary = "Remove my avatar (API-USER-04)")
    @ApiResponse(responseCode = "204", description = "Back to the initials avatar")
    public void removeMyAvatar() {
        avatarService.remove(SecurityUtils.getCurrentUserId());
    }

    @GetMapping(ApiPaths.Users.PROFILE)
    @Operation(operationId = "getPublicProfile", summary = "Public profile card of a user (API-USER-05)")
    @ApiResponse(responseCode = "200", description = "Public fields only")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND, also for accounts that are not active")
    public PublicProfileResponse getPublicProfile(@PathVariable UUID userId) {
        return profileService.getPublicProfile(userId);
    }
}
