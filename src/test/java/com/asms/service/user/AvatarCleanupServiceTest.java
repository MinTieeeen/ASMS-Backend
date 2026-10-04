package com.asms.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.repository.user.UserRepository;
import com.asms.service.storage.StorageService;
import com.asms.service.storage.StorageService.StoredObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvatarCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:40:00Z");

    private final StorageService storage = mock(StorageService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AvatarCleanupService service =
            new AvatarCleanupService(storage, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("NFR-USER-14: deletes old files nobody uses, keeps used and recent ones")
    void deleteOrphans_shouldDeleteOldUnusedFilesOnly() {
        Instant old = NOW.minus(Duration.ofDays(2));
        when(storage.isConfigured()).thenReturn(true);
        when(storage.list("avatars/"))
                .thenReturn(List.of(
                        new StoredObject("avatars/u1/used-256.webp", old),
                        new StoredObject("avatars/u1/used-64.webp", old),
                        new StoredObject("avatars/u1/orphan-256.webp", old),
                        new StoredObject("avatars/u1/orphan-64.webp", old),
                        new StoredObject("avatars/u2/uploading-256.webp", NOW.minusSeconds(60))));
        when(userRepository.findAllAvatarKeys()).thenReturn(Set.of("avatars/u1/used"));

        int deleted = service.deleteOrphans();

        assertThat(deleted).isEqualTo(2);
        verify(storage).delete(List.of("avatars/u1/orphan-256.webp", "avatars/u1/orphan-64.webp"));
    }

    @Test
    void deleteOrphans_shouldDoNothing_whenStorageNotConfigured() {
        assertThat(service.deleteOrphans()).isZero();
    }

    @Test
    void avatarKeyOf_shouldStripTheSizeSuffix() {
        assertThat(AvatarCleanupService.avatarKeyOf("avatars/u/7a1d-0c55-256.webp"))
                .isEqualTo("avatars/u/7a1d-0c55");
    }
}
