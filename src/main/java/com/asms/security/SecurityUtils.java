package com.asms.security;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Helpers to read the current user from the SecurityContext. The JWT {@code sub} claim holds the user id and the
 * {@code sid} claim the session id (UUIDs).
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UUID> findCurrentUserId() {
        return currentJwt().map(jwt -> UUID.fromString(jwt.getSubject()));
    }

    public static UUID getCurrentUserId() {
        return findCurrentUserId().orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_MISSING));
    }

    /** Session of the access token making the request (UC-AUTH-06, UC-AUTH-09). */
    public static UUID getCurrentSessionId() {
        return currentJwt()
                .map(jwt -> jwt.getClaimAsString(JwtTokenService.CLAIM_SESSION_ID))
                .map(UUID::fromString)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_MISSING));
    }

    private static Optional<Jwt> currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }
}
