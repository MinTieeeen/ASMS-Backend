package com.asms.scheduler;

import com.asms.service.auth.AuthCleanupService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AuthCleanupService} once a day (NFR-AUTH-09). ShedLock guarantees a single run when several instances
 * of the API are deployed.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Component
@RequiredArgsConstructor
public class AuthCleanupJob {

    private final AuthCleanupService authCleanupService;

    @Scheduled(cron = "${app.auth.cleanup-cron}", zone = "${app.default-timezone}")
    @SchedulerLock(name = "auth-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purgeExpiredData() {
        authCleanupService.purgeExpiredData();
    }
}
