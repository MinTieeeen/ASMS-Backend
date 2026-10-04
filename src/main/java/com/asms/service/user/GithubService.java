package com.asms.service.user;

import com.asms.config.AppProperties;
import com.asms.dto.user.GithubAccountResponse;
import com.asms.dto.user.GithubAuthorizeResponse;
import com.asms.entity.user.UserGithubAccount;
import com.asms.entity.user.UserGithubAccount.GithubProfile;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.user.ProfileMapper;
import com.asms.repository.user.UserGithubAccountRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.service.user.GithubClient.GithubUnavailableException;
import com.asms.util.RandomCodeGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub account of the signed-in user (FR-USER-27, FR-USER-28, BR-USER-22; API-USER-17 to 20).
 *
 * <p>The OAuth state is 32 random bytes stored in Redis under {@code github:oauth-state:{state}} for 10 minutes and
 * bound to the user who asked for it; the callback reads it with GETDEL, so a state works once and only for its owner
 * (CSRF protection). Calls to GitHub run outside database transactions.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    static final String STATE_KEY_PREFIX = "github:oauth-state:";
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;
    private static final String SCOPE = "read:user";

    private final GithubClient github;
    private final UserGithubAccountRepository accountRepository;
    private final StringRedisTemplate redis;
    private final RateLimitService rateLimitService;
    private final ProfileMapper profileMapper;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties props;
    private final Clock clock;

    /**
     * API-USER-17: a state for this user and the GitHub page to send the browser to.
     *
     * @throws BusinessException {@code GITHUB_ALREADY_CONNECTED}, {@code AUTH_RATE_LIMITED} or {@code GITHUB_UNAVAILABLE}
     *     when the OAuth App is not configured
     */
    public GithubAuthorizeResponse authorize(UUID userId) {
        requireConfigured();
        rateLimitService.check(RateLimitPolicy.GITHUB_AUTHORIZE_USER, userId.toString());
        if (accountRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.GITHUB_ALREADY_CONNECTED);
        }
        String state = RandomCodeGenerator.urlSafeToken(STATE_BYTES);
        redis.opsForValue().set(STATE_KEY_PREFIX + state, userId.toString(), STATE_TTL);
        String url = UriComponentsBuilder.fromUriString(props.github().oauthBaseUrl())
                .path("/login/oauth/authorize")
                .queryParam("client_id", github.clientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("allow_signup", false)
                .encode()
                .toUriString();
        return new GithubAuthorizeResponse(url, Instant.now(clock).plus(STATE_TTL));
    }

    /**
     * API-USER-18: checks the state, trades the code for a token, copies the public profile and revokes the token.
     *
     * @throws BusinessException {@code GITHUB_STATE_INVALID}, {@code GITHUB_ALREADY_CONNECTED},
     *     {@code GITHUB_ACCOUNT_IN_USE}, {@code GITHUB_UNAVAILABLE} or {@code AUTH_RATE_LIMITED}
     */
    public GithubAccountResponse completeConnection(UUID userId, String code, String state) {
        requireConfigured();
        rateLimitService.check(RateLimitPolicy.GITHUB_CALLBACK_USER, userId.toString());
        String owner = redis.opsForValue().getAndDelete(STATE_KEY_PREFIX + state);
        if (!userId.toString().equals(owner)) {
            throw new BusinessException(ErrorCode.GITHUB_STATE_INVALID);
        }
        if (accountRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.GITHUB_ALREADY_CONNECTED);
        }

        GithubProfile profile;
        try {
            String token = github.exchangeCode(code, redirectUri());
            try {
                profile = github.currentUser(token);
            } finally {
                github.revokeToken(token);
            }
        } catch (GithubUnavailableException e) {
            log.warn("GitHub connection of user {} failed: {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.GITHUB_UNAVAILABLE);
        }
        if (accountRepository.existsByGithubId(profile.githubId())) {
            throw new BusinessException(ErrorCode.GITHUB_ACCOUNT_IN_USE);
        }
        try {
            UserGithubAccount account =
                    accountRepository.saveAndFlush(UserGithubAccount.connect(userId, profile, Instant.now(clock)));
            return profileMapper.toGithub(account);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another connection of the same GitHub account or of this user
            throw new BusinessException(
                    accountRepository.existsById(userId)
                            ? ErrorCode.GITHUB_ALREADY_CONNECTED
                            : ErrorCode.GITHUB_ACCOUNT_IN_USE);
        }
    }

    /**
     * API-USER-19: reads the public profile again. When the GitHub account no longer exists the copy is kept.
     *
     * @throws BusinessException {@code GITHUB_NOT_CONNECTED}, {@code AUTH_RATE_LIMITED} or {@code GITHUB_UNAVAILABLE}
     */
    public GithubAccountResponse refresh(UUID userId) {
        requireConfigured();
        UserGithubAccount current = accountRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));
        rateLimitService.check(RateLimitPolicy.GITHUB_REFRESH_USER, userId.toString());
        GithubProfile profile;
        try {
            profile = github.userById(current.getGithubId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_UNAVAILABLE));
        } catch (GithubUnavailableException e) {
            log.warn("GitHub refresh of user {} failed: {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.GITHUB_UNAVAILABLE);
        }
        return transactionTemplate.execute(status -> {
            UserGithubAccount account = accountRepository
                    .findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));
            account.sync(profile, Instant.now(clock));
            return profileMapper.toGithub(accountRepository.saveAndFlush(account));
        });
    }

    /** API-USER-20: forgets the account; revoking ASMS for good is done by the user on GitHub. */
    public void disconnect(UUID userId) {
        if (!accountRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }
        accountRepository.deleteById(userId);
    }

    private String redirectUri() {
        return StringUtils.trimTrailingCharacter(props.frontendUrl(), '/')
                + props.github().callbackPath();
    }

    private void requireConfigured() {
        if (!github.isConfigured()) {
            log.warn("GitHub OAuth App not configured (GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET)");
            throw new BusinessException(ErrorCode.GITHUB_UNAVAILABLE);
        }
    }
}
