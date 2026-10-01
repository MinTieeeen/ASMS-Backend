package com.asms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Rejects access tokens whose session was revoked (FR-AUTH-10, FR-AUTH-28, section 7.4) or that carry no {@code sid}.
 * Runs after the signature check and reads only Redis, never the database (NFR-AUTH-02).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class RevokedSessionValidator implements OAuth2TokenValidator<Jwt> {

    private final RevokedSessionStore revokedSessionStore;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sessionId = jwt.getClaimAsString(JwtTokenService.CLAIM_SESSION_ID);
        if (!StringUtils.hasText(sessionId)) {
            return failure("Missing session id");
        }
        if (revokedSessionStore.isRevoked(sessionId)) {
            return failure("Session revoked");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
