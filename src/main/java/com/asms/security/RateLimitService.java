package com.asms.security;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * Distributed rate limiting with Bucket4j on Redis (BR-AUTH-09, BR-AUTH-10).
 *
 * <p>The Redis connection is opened lazily so that the application starts even when Redis is down. NFR-AUTH-10: if
 * Redis is unreachable, requests are allowed and a warning is logged.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Service
public class RateLimitService implements DisposableBean {

    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(1);
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final RedisConnectionFactory connectionFactory;
    private final Map<RateLimitPolicy, BucketConfiguration> configurations = new EnumMap<>(RateLimitPolicy.class);

    @Nullable
    private volatile StatefulRedisConnection<String, byte[]> connection;

    @Nullable
    private volatile ProxyManager<String> proxyManager;

    public RateLimitService(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            configurations.put(
                    policy,
                    BucketConfiguration.builder()
                            .addLimit(limit -> limit.capacity(policy.getCapacity())
                                    .refillIntervally(policy.getCapacity(), policy.getPeriod()))
                            .build());
        }
    }

    /**
     * Consumes one request for {@code subject} and throws {@code AUTH_RATE_LIMITED} with {@code retryAfterSeconds}
     * when the limit is reached.
     */
    public void check(RateLimitPolicy policy, String subject) {
        long waitSeconds = consume(policy, subject);
        if (waitSeconds > 0) {
            throw BusinessException.retryAfter(ErrorCode.AUTH_RATE_LIMITED, waitSeconds);
        }
    }

    /** Consumes one request and returns whether it is allowed, for flows that must fail silently (FR-AUTH-12). */
    public boolean tryConsume(RateLimitPolicy policy, String subject) {
        return consume(policy, subject) == 0;
    }

    /** @return 0 when allowed, otherwise the number of seconds to wait */
    private long consume(RateLimitPolicy policy, String subject) {
        try {
            ConsumptionProbe probe = proxyManager()
                    .getProxy(policy.getKeyPrefix() + subject, () -> configurations.get(policy))
                    .tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return 0;
            }
            return Math.max(1, ceilDiv(probe.getNanosToWaitForRefill(), NANOS_PER_SECOND));
        } catch (RuntimeException e) {
            log.warn("Rate limiting skipped for {}: {}", policy, e.getMessage());
            return 0;
        }
    }

    private ProxyManager<String> proxyManager() {
        ProxyManager<String> current = proxyManager;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (proxyManager == null) {
                StatefulRedisConnection<String, byte[]> opened = openConnection();
                connection = opened;
                proxyManager = Bucket4jLettuce.casBasedBuilder(opened)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofSeconds(10)))
                        .requestTimeout(REDIS_TIMEOUT)
                        .build();
            }
            return proxyManager;
        }
    }

    private StatefulRedisConnection<String, byte[]> openConnection() {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)
                || !(lettuce.getNativeClient() instanceof RedisClient client)) {
            throw new IllegalStateException("Rate limiting requires a standalone Lettuce Redis client");
        }
        StatefulRedisConnection<String, byte[]> opened =
                client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        opened.setTimeout(REDIS_TIMEOUT);
        return opened;
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    @Override
    public void destroy() {
        StatefulRedisConnection<String, byte[]> current = connection;
        if (current != null) {
            current.close();
        }
    }
}
