package com.asms.security;

import com.asms.config.AppProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis blocklist of revoked session ids (Auth specification section 7.4). Access tokens are stateless, so a revoked
 * session's tokens stay valid until they expire unless their {@code sid} is listed here. Each key lives exactly as long
 * as an access token can.
 *
 * <p>NFR-AUTH-10: when Redis is unreachable, authentication keeps working without the blocklist and a warning is
 * logged.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokedSessionStore {

    private static final String KEY_PREFIX = "auth:revoked-sid:";
    private static final String MARKER = "1";

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public void markRevoked(UUID sessionId) {
        try {
            redis.opsForValue().set(KEY_PREFIX + sessionId, MARKER, props.auth().accessTokenTtl());
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, session {} not added to the blocklist: {}", sessionId, e.getMessage());
        }
    }

    public boolean isRevoked(String sessionId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, skipping revoked-session check: {}", e.getMessage());
            return false;
        }
    }
}
