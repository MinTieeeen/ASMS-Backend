package com.asms.service.user;

import com.asms.repository.user.UserRepository;
import com.asms.service.storage.StorageService;
import com.asms.service.storage.StorageService.StoredObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes avatar files no user points to any more (NFR-USER-14): left behind when an upload failed after storing the
 * files, or when deleting the previous photo failed. Only files older than a day are touched, so an upload in progress
 * is never removed.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarCleanupService {

    static final Duration MIN_AGE = Duration.ofHours(24);

    private final StorageService storage;
    private final UserRepository userRepository;
    private final Clock clock;

    /** @return number of files deleted */
    @Transactional(readOnly = true)
    public int deleteOrphans() {
        if (!storage.isConfigured()) {
            return 0;
        }
        Instant cutoff = Instant.now(clock).minus(MIN_AGE);
        // Keys are read after the listing: a photo saved meanwhile is younger than the cutoff anyway
        List<StoredObject> objects = storage.list(AvatarService.KEY_ROOT);
        Set<String> inUse = userRepository.findAllAvatarKeys();
        List<String> orphans = objects.stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(StoredObject::key)
                .filter(key -> !inUse.contains(avatarKeyOf(key)))
                .toList();
        if (!orphans.isEmpty()) {
            storage.delete(orphans);
            log.info("Deleted {} orphan avatar files", orphans.size());
        }
        return orphans.size();
    }

    /** "avatars/u/k-256.webp" → "avatars/u/k" */
    static String avatarKeyOf(String objectKey) {
        int dash = objectKey.lastIndexOf('-');
        return dash < 0 ? objectKey : objectKey.substring(0, dash);
    }
}
