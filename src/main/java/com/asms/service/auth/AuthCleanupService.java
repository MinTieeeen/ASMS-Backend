package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.repository.auth.AuthEventRepository;
import com.asms.repository.auth.RefreshTokenRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.repository.auth.UserTokenRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes Auth data that is no longer useful (NFR-AUTH-09, NFR-AUTH-14): sessions, refresh tokens and email tokens
 * that ended more than 7 days ago, and auth events older than 180 days.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCleanupService {

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserTokenRepository userTokenRepository;
    private final AuthEventRepository authEventRepository;
    private final AppProperties props;
    private final Clock clock;

    /** Counts of deleted rows, for logs and tests. */
    public record Result(int sessions, int refreshTokens, int userTokens, int authEvents) {}

    @Transactional
    public Result purgeExpiredData() {
        Instant now = Instant.now(clock);
        Instant endedBefore = now.minus(props.auth().cleanupGracePeriod());
        // Sessions first: their refresh tokens go with them through ON DELETE CASCADE
        Result result = new Result(
                sessionRepository.deleteEndedBefore(endedBefore),
                refreshTokenRepository.deleteExpiredBefore(endedBefore),
                userTokenRepository.deleteExpiredBefore(endedBefore),
                authEventRepository.deleteCreatedBefore(now.minus(props.auth().eventRetention())));
        log.info("Auth cleanup done: {}", result);
        return result;
    }
}
