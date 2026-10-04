package com.asms.service.user;

import com.asms.config.AppProperties;
import com.asms.dto.user.AvatarResponse;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.service.storage.StorageService;
import com.asms.service.storage.StorageUnavailableException;
import com.asms.service.user.AvatarImageProcessor.ProcessedAvatar;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Avatar of the signed-in user (UC-USER-02; API-USER-03, API-USER-04).
 *
 * <p>The heavy work (decoding, encoding, uploading) happens outside any database transaction; only the switch of
 * {@code users.avatar_key} is transactional. The previous files are deleted after the commit. If anything fails in
 * between, the files left behind have no reference and {@code AvatarCleanupJob} removes them (NFR-USER-14). Every
 * upload gets a new random key, so browsers never show a cached old photo.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    static final String KEY_ROOT = "avatars/";
    private static final String CONTENT_TYPE = "image/webp";
    /** The key changes with every upload, so the files never change and can be cached forever */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final AvatarImageProcessor processor;
    private final StorageService storage;
    private final UserRepository userRepository;
    private final RateLimitService rateLimitService;
    private final AvatarUrlResolver avatarUrls;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties props;

    /**
     * @throws BusinessException {@code AUTH_RATE_LIMITED}, {@code AVATAR_TOO_LARGE}, {@code AVATAR_UNSUPPORTED_TYPE}
     *     or {@code AVATAR_INVALID_IMAGE}
     */
    public AvatarResponse upload(UUID userId, MultipartFile file, int cropX, int cropY, int cropSize) {
        if (!storage.isConfigured() || !StringUtils.hasText(props.storage().publicBaseUrl())) {
            throw new StorageUnavailableException(
                    "Set STORAGE_BUCKET and STORAGE_PUBLIC_BASE_URL to enable avatars", null);
        }
        rateLimitService.check(RateLimitPolicy.AVATAR_UPLOAD_USER, userId.toString());
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.AVATAR_TOO_LARGE);
        }
        ProcessedAvatar avatar = processor.process(readBytes(file), cropX, cropY, cropSize);

        String newKey = KEY_ROOT + userId + "/" + UUID.randomUUID();
        storage.put(
                AvatarUrlResolver.objectKey(newKey, AvatarUrlResolver.LARGE_SIZE),
                avatar.large(),
                CONTENT_TYPE,
                CACHE_CONTROL);
        storage.put(
                AvatarUrlResolver.objectKey(newKey, AvatarUrlResolver.THUMB_SIZE),
                avatar.thumb(),
                CONTENT_TYPE,
                CACHE_CONTROL);
        switchAvatarKey(userId, newKey);
        return new AvatarResponse(
                Objects.requireNonNull(avatarUrls.large(newKey)), Objects.requireNonNull(avatarUrls.thumb(newKey)));
    }

    /** Back to the initials avatar (API-USER-04); nothing to do when there is no photo. */
    public void remove(UUID userId) {
        switchAvatarKey(userId, null);
    }

    private void switchAvatarKey(UUID userId, @Nullable String newKey) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository
                    .findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            String oldKey = user.getAvatarKey();
            user.changeAvatarKey(newKey);
            if (oldKey != null) {
                deleteAfterCommit(oldKey);
            }
        });
    }

    private void deleteAfterCommit(String avatarKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(List.of(
                            AvatarUrlResolver.objectKey(avatarKey, AvatarUrlResolver.LARGE_SIZE),
                            AvatarUrlResolver.objectKey(avatarKey, AvatarUrlResolver.THUMB_SIZE)));
                } catch (StorageUnavailableException e) {
                    log.warn("Old avatar {} not deleted, the cleanup job will: {}", avatarKey, e.getMessage());
                }
            }
        });
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AVATAR_INVALID_IMAGE);
        }
    }
}
