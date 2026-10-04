package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.dto.auth.ChangePasswordRequest;
import com.asms.dto.auth.ForgotPasswordRequest;
import com.asms.dto.auth.ResetPasswordRequest;
import com.asms.dto.auth.ResetTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.SessionRevokeReason;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.User;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.util.EmailMasker;
import com.asms.util.EmailNormalizer;
import com.asms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot, reset and change password (UC-AUTH-04, UC-AUTH-06; FR-AUTH-12 to FR-AUTH-16, FR-AUTH-19).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final String METADATA_OUTCOME = "outcome";

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final SessionService sessionService;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final AuthMailService mailService;
    private final AuthEventPublisher events;
    private final AppProperties props;
    private final Clock clock;

    /**
     * Sends a reset link to an active account, or a new activation link to a pending one (FR-AUTH-13). Always completes
     * the same way whatever the email, so the caller cannot learn whether it exists (FR-AUTH-12); when the rate limit
     * is exceeded nothing is sent (BR-AUTH-10).
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request, ClientInfo client) {
        String email = EmailNormalizer.normalize(request.email());
        AuthEventOccurred event = AuthEventOccurred.of(AuthEventType.PASSWORD_RESET_REQUESTED, client)
                .withEmail(email);
        // Non-short-circuit "&": every request counts against both limits
        boolean allowed = rateLimitService.tryConsume(RateLimitPolicy.FORGOT_PASSWORD_IP, client.ipAddress())
                & rateLimitService.tryConsume(RateLimitPolicy.FORGOT_PASSWORD_EMAIL, TokenHasher.sha256Hex(email));
        if (!allowed) {
            events.publish(event.withMetadata(METADATA_OUTCOME, "RATE_LIMITED"));
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            events.publish(event.withMetadata(METADATA_OUTCOME, "USER_NOT_FOUND"));
            return;
        }
        event = event.withUser(user.getId());
        switch (user.getStatus()) {
            case ACTIVE -> {
                UserTokenService.IssuedToken token =
                        userTokenService.issue(user, UserTokenType.PASSWORD_RESET, null, client.ipAddress());
                mailService.sendPasswordReset(user, token.rawToken(), token.expiresAt());
                events.publish(event.withMetadata(METADATA_OUTCOME, "RESET_LINK_SENT"));
            }
            case PENDING_ACTIVATION -> {
                UserTokenService.IssuedToken token =
                        userTokenService.issue(user, UserTokenType.ACTIVATION, null, client.ipAddress());
                mailService.sendActivation(user, token.rawToken(), token.expiresAt());
                events.publish(event.withMetadata(METADATA_OUTCOME, "ACTIVATION_LINK_SENT"));
            }
            case LOCKED -> events.publish(event.withMetadata(METADATA_OUTCOME, "ACCOUNT_LOCKED"));
        }
    }

    /**
     * Checks a reset link before the form is shown (API-AUTH-07, FR-AUTH-15).
     *
     * @throws BusinessException {@code AUTH_TOKEN_INVALID} with {@code reason}
     */
    @Transactional(readOnly = true)
    public ResetTokenInfoResponse validateResetToken(TokenRequest request) {
        UserToken token = requireResetToken(request.token());
        return new ResetTokenInfoResponse(EmailMasker.mask(token.getUser().getEmail()), token.getExpiresAt());
    }

    /**
     * Sets a new password from a reset link: ends the temporary lock, revokes every session and sends a notice
     * (FR-AUTH-16). The user is not logged in afterwards.
     *
     * @throws BusinessException {@code AUTH_TOKEN_INVALID}, {@code AUTH_PASSWORD_POLICY} or
     *     {@code AUTH_PASSWORD_SAME_AS_OLD}
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, ClientInfo client) {
        UserToken token = requireResetToken(request.token());
        User user = token.getUser();
        checkNewPassword(user, request.newPassword());

        Instant now = Instant.now(clock);
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        userTokenService.consume(token);
        sessionService.revokeAllForUser(user.getId(), SessionRevokeReason.PASSWORD_RESET, null);
        mailService.sendPasswordChanged(user, now);
        events.publish(
                AuthEventOccurred.of(AuthEventType.PASSWORD_RESET, client).withUser(user.getId()));
    }

    /**
     * Changes the password of the signed-in user and logs out every other device (UC-AUTH-06, FR-AUTH-19).
     *
     * <p>A wrong current password counts as a failed login (BR-AUTH-04): the fifth one in a row locks the account for a
     * while and also revokes the current session. {@code noRollbackFor} keeps that counter and lock.
     *
     * @throws BusinessException {@code AUTH_RATE_LIMITED}, {@code AUTH_CURRENT_PASSWORD_WRONG},
     *     {@code AUTH_ACCOUNT_TEMP_LOCKED}, {@code AUTH_PASSWORD_POLICY} or {@code AUTH_PASSWORD_SAME_AS_OLD}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void changePassword(UUID userId, UUID sessionId, ChangePasswordRequest request, ClientInfo client) {
        rateLimitService.check(RateLimitPolicy.CHANGE_PASSWORD_USER, userId.toString());
        // Row lock: a wrong current password counts towards the lockout (BR-AUTH-04), outside @Version
        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Instant now = Instant.now(clock);
        if (!user.hasPassword() || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            recordWrongCurrentPassword(user, sessionId, client, now);
        }
        checkNewPassword(user, request.newPassword());

        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        sessionService.revokeAllForUser(userId, SessionRevokeReason.PASSWORD_CHANGED, sessionId);
        mailService.sendPasswordChanged(user, now);
        events.publish(AuthEventOccurred.of(AuthEventType.PASSWORD_CHANGED, client)
                .withUser(userId)
                .withSession(sessionId));
    }

    private UserToken requireResetToken(String rawToken) {
        UserToken token = userTokenService.requireUsable(rawToken, UserTokenType.PASSWORD_RESET);
        // A reset link must not reopen an account that was locked after the link was sent
        if (!token.getUser().isActive()) {
            throw UserTokenService.invalid(UserTokenService.InvalidReason.NOT_FOUND);
        }
        return token;
    }

    /** BR-AUTH-01 then BR-AUTH-12. */
    private void checkNewPassword(User user, String newPassword) {
        passwordPolicy.check(newPassword, user.getEmail());
        if (user.hasPassword() && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);
        }
    }

    /** Always throws: {@code AUTH_CURRENT_PASSWORD_WRONG}, or {@code AUTH_ACCOUNT_TEMP_LOCKED} at the limit. */
    private void recordWrongCurrentPassword(User user, UUID sessionId, ClientInfo client, Instant now) {
        int attempt = user.recordFailedLogin(now);
        AppProperties.Lockout lockout = props.auth().lockout();
        if (attempt < lockout.maxAttempts()) {
            throw new BusinessException(ErrorCode.AUTH_CURRENT_PASSWORD_WRONG);
        }
        Instant lockedUntil = now.plus(lockout.duration());
        user.lockTemporarily(lockedUntil);
        sessionService.revokeSession(sessionId, SessionRevokeReason.ACCOUNT_LOCKED);
        events.publish(AuthEventOccurred.of(AuthEventType.ACCOUNT_TEMP_LOCKED, client)
                .withUser(user.getId())
                .withSession(sessionId));
        throw BusinessException.retryAfter(
                ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED,
                Duration.between(now, lockedUntil).toSeconds());
    }
}
