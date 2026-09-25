package com.asms.security;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Helpers to read the current user from the SecurityContext. The JWT {@code sub} claim holds the user id (UUID).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UUID> findCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(UUID.fromString(jwtAuth.getToken().getSubject()));
        }
        return Optional.empty();
    }

    public static UUID getCurrentUserId() {
        return findCurrentUserId().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
