package com.asms.scheduler;

import com.asms.service.user.AvatarCleanupService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AvatarCleanupService} once a day at 03:40 (NFR-USER-14). ShedLock key
 * {@code shedlock:...:avatar-orphan-cleanup} keeps it to one instance.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Component
@RequiredArgsConstructor
public class AvatarCleanupJob {

    private final AvatarCleanupService avatarCleanupService;

    @Scheduled(cron = "0 40 3 * * *", zone = "${app.default-timezone}")
    @SchedulerLock(name = "avatar-orphan-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void deleteOrphans() {
        avatarCleanupService.deleteOrphans();
    }
}
