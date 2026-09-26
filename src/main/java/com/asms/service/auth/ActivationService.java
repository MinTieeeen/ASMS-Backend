package com.asms.service.auth;

import com.asms.dto.auth.ActivateAccountRequest;
import com.asms.dto.auth.ActivationTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.UserToken;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.User;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account activation: the user opens the link from the welcome email and sets a first password (UC-AUTH-05,
 * FR-AUTH-15, FR-AUTH-17).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Service
@RequiredArgsConstructor
public class ActivationService {

    private final UserTokenService userTokenService;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher events;
    private final Clock clock;

    /**
     * Checks an activation link before the form is shown (API-AUTH-09).
     *
     * @throws BusinessException {@code AUTH_TOKEN_INVALID} with {@code reason}, or {@code AUTH_ACCOUNT_ALREADY_ACTIVE}
     */
    @Transactional(readOnly = true)
    public ActivationTokenInfoResponse validateActivationToken(TokenRequest request) {
        UserToken token = requireActivationToken(request.token());
        User user = token.getUser();
        return new ActivationTokenInfoResponse(user.getEmail(), user.getFullName(), token.getExpiresAt());
    }

    /**
     * Sets the first password and activates the account (API-AUTH-10). The user is not logged in afterwards.
     *
     * @throws BusinessException {@code AUTH_TOKEN_INVALID}, {@code AUTH_ACCOUNT_ALREADY_ACTIVE} or
     *     {@code AUTH_PASSWORD_POLICY}
     */
    @Transactional
    public void activate(ActivateAccountRequest request, ClientInfo client) {
        UserToken token = requireActivationToken(request.token());
        User user = token.getUser();
        passwordPolicy.check(request.password(), user.getEmail());

        user.activate(passwordEncoder.encode(request.password()), Instant.now(clock));
        userTokenService.consume(token);
        events.publish(AuthEventOccurred.of(AuthEventType.ACCOUNT_ACTIVATED, client)
                .withUser(user.getId())
                .withEmail(user.getEmail()));
    }

    /**
     * UC-AUTH-05 2b: an account that is already active is reported as such even when its link was used, so the page
     * can offer "go to login" instead of "invalid link".
     */
    private UserToken requireActivationToken(String rawToken) {
        UserToken token = userTokenService
                .find(rawToken, UserTokenType.ACTIVATION)
                .orElseThrow(() -> UserTokenService.invalid(UserTokenService.InvalidReason.NOT_FOUND));
        User user = token.getUser();
        if (user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_ALREADY_ACTIVE);
        }
        if (!user.isPendingActivation()) {
            throw UserTokenService.invalid(UserTokenService.InvalidReason.NOT_FOUND);
        }
        userTokenService.checkUsable(token);
        return token;
    }
}
