package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.dto.auth.CurrentUserResponse;
import com.asms.dto.auth.IssuedSession;
import com.asms.dto.auth.LoginRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.LoginFailedReason;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.user.UserMapper;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.util.EmailNormalizer;
import com.asms.util.RandomCodeGenerator;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and account checks of the Auth module (UC-AUTH-01, FR-AUTH-01 to FR-AUTH-06).
 *
 * <p>{@link #login} uses {@code noRollbackFor} so that the failed-login counter and the temporary lock are saved even
 * though the call ends with an error.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Service
public class AuthService {

    private static final String METADATA_REASON = "reason";
    private static final String METADATA_ATTEMPT = "attempt";

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher events;
    private final UserMapper userMapper;
    private final AppProperties props;
    private final Clock clock;

    /** Hash checked when the email is unknown, so that response time does not reveal it (NFR-AUTH-06). */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            SessionService sessionService,
            RateLimitService rateLimitService,
            PasswordEncoder passwordEncoder,
            AuthEventPublisher events,
            UserMapper userMapper,
            AppProperties props,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.userMapper = userMapper;
        this.props = props;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(RandomCodeGenerator.urlSafeToken(16));
    }

    /**
     * Authenticates with email and password and opens a session (UC-AUTH-01).
     *
     * @throws BusinessException {@code AUTH_RATE_LIMITED}, {@code AUTH_INVALID_CREDENTIALS},
     *     {@code AUTH_ACCOUNT_TEMP_LOCKED} or {@code AUTH_ACCOUNT_LOCKED}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedSession login(LoginRequest request, ClientInfo client) {
        String email = EmailNormalizer.normalize(request.email());
        AuthEventOccurred failure =
                AuthEventOccurred.of(AuthEventType.LOGIN_FAILED, client).withEmail(email);
        checkLoginRateLimits(email, failure);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Same Argon2 cost as a real check: FR-AUTH-02, NFR-AUTH-06
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            events.publish(failure.withMetadata(METADATA_REASON, LoginFailedReason.USER_NOT_FOUND));
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        failure = failure.withUser(user.getId());

        Instant now = Instant.now(clock);
        if (user.isTemporarilyLocked(now)) {
            events.publish(failure.withMetadata(METADATA_REASON, LoginFailedReason.TEMP_LOCKED));
            throw temporarilyLocked(user, now);
        }
        if (!passwordMatches(user, request.password())) {
            recordWrongPassword(user, failure, client, now);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            events.publish(failure.withMetadata(METADATA_REASON, LoginFailedReason.ACCOUNT_LOCKED));
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        user.recordSuccessfulLogin(now);
        IssuedSession issued = sessionService.openSession(user, request.isRememberMe(), client);
        events.publish(AuthEventOccurred.of(AuthEventType.LOGIN_SUCCESS, client)
                .withUser(user.getId())
                .withEmail(email));
        return issued;
    }

    /** The signed-in user (API-AUTH-05). */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId) {
        return userRepository
                .findById(userId)
                .map(userMapper::toCurrentUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkLoginRateLimits(String email, AuthEventOccurred failure) {
        try {
            rateLimitService.check(RateLimitPolicy.LOGIN_IP, failure.ipAddress());
            rateLimitService.check(RateLimitPolicy.LOGIN_EMAIL, TokenHasher.sha256Hex(email));
        } catch (BusinessException e) {
            events.publish(failure.withMetadata(METADATA_REASON, LoginFailedReason.RATE_LIMITED));
            throw e;
        }
    }

    private boolean passwordMatches(User user, String rawPassword) {
        if (!user.hasPassword()) {
            // Pending accounts have no password yet (BR-AUTH-03); still spend the hashing time
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /** Counts the failure and locks the account for a while after too many in a row (BR-AUTH-04). Always throws. */
    private void recordWrongPassword(User user, AuthEventOccurred failure, ClientInfo client, Instant now) {
        int attempt = user.recordFailedLogin(now);
        LoginFailedReason reason =
                user.hasPassword() ? LoginFailedReason.INVALID_PASSWORD : LoginFailedReason.NO_PASSWORD;
        events.publish(failure.withMetadata(METADATA_REASON, reason).withMetadata(METADATA_ATTEMPT, attempt));

        AppProperties.Lockout lockout = props.auth().lockout();
        if (attempt >= lockout.maxAttempts()) {
            user.lockTemporarily(now.plus(lockout.duration()));
            events.publish(AuthEventOccurred.of(AuthEventType.ACCOUNT_TEMP_LOCKED, client)
                    .withUser(user.getId())
                    .withEmail(user.getEmail()));
            throw temporarilyLocked(user, now);
        }
        throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    private static BusinessException temporarilyLocked(User user, Instant now) {
        long seconds = Duration.between(now, user.getLockedUntil()).toSeconds();
        return BusinessException.retryAfter(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED, seconds);
    }
}
