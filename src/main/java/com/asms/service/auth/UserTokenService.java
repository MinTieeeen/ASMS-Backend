package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.repository.auth.UserTokenRepository;
import com.asms.util.RandomCodeGenerator;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, checks and consumes the one-time email tokens for activation and password reset (BR-AUTH-11, NFR-AUTH-04).
 * Only the SHA-256 hash is stored; the raw token exists only in the email link.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Service
@RequiredArgsConstructor
public class UserTokenService {

    public static final String REASON = "reason";

    private static final int TOKEN_BYTES = 32;

    private final UserTokenRepository userTokenRepository;
    private final AppProperties props;
    private final Clock clock;

    /** A freshly issued token: the raw value goes into the email, never into the database. */
    public record IssuedToken(String rawToken, Instant expiresAt) {}

    /** Why a token cannot be used, returned as {@code reason} of {@code AUTH_TOKEN_INVALID}. */
    public enum InvalidReason {
        NOT_FOUND,
        EXPIRED,
        USED
    }

    /** Issues a new token after invalidating every unused token of the same type for this user (BR-AUTH-11). */
    @Transactional
    public IssuedToken issue(User user, UserTokenType type, @Nullable UUID createdBy, @Nullable String requestIp) {
        Instant now = Instant.now(clock);
        userTokenRepository.invalidateActive(user.getId(), type, now);
        String rawToken = RandomCodeGenerator.urlSafeToken(TOKEN_BYTES);
        UserToken token = userTokenRepository.save(
                UserToken.issue(user, type, TokenHasher.sha256Hex(rawToken), now, ttl(type), createdBy, requestIp));
        return new IssuedToken(rawToken, token.getExpiresAt());
    }

    /** Finds a token of the given type without checking whether it is still usable. */
    @Transactional(readOnly = true)
    public Optional<UserToken> find(String rawToken, UserTokenType type) {
        return userTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .filter(token -> token.getType() == type);
    }

    /**
     * Finds a token that can still be used. Checks follow the table description: unknown hash, then used or
     * invalidated, then expired.
     *
     * @throws BusinessException {@code AUTH_TOKEN_INVALID} with {@code reason}
     */
    @Transactional(readOnly = true)
    public UserToken requireUsable(String rawToken, UserTokenType type) {
        UserToken token = find(rawToken, type).orElseThrow(() -> invalid(InvalidReason.NOT_FOUND));
        checkUsable(token);
        return token;
    }

    /** @throws BusinessException {@code AUTH_TOKEN_INVALID} with reason {@code USED} or {@code EXPIRED} */
    public void checkUsable(UserToken token) {
        if (token.isConsumed()) {
            throw invalid(InvalidReason.USED);
        }
        if (token.isExpired(Instant.now(clock))) {
            throw invalid(InvalidReason.EXPIRED);
        }
    }

    public void consume(UserToken token) {
        token.markUsed(Instant.now(clock));
    }

    public static BusinessException invalid(InvalidReason reason) {
        return new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, Map.of(REASON, reason.name()));
    }

    private Duration ttl(UserTokenType type) {
        return switch (type) {
            case ACTIVATION -> props.auth().activationTokenTtl();
            case PASSWORD_RESET -> props.auth().resetTokenTtl();
        };
    }
}
