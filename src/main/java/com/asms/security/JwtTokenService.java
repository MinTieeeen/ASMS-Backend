package com.asms.security;

import com.asms.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Signs access tokens (Auth specification section 7.2): HS256 with a {@code kid} header, lifetime 15 minutes
 * (BR-AUTH-07), claims {@code sub}, {@code sid}, {@code role}, {@code iat}, {@code exp}, {@code iss}, {@code jti}.
 * No email or other personal data goes into the token.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final Clock clock;

    /** A signed access token and its lifetime in seconds. */
    public record AccessToken(String value, long expiresInSeconds) {}

    /**
     * @param role system role name ({@code USER} or {@code ADMIN}), mapped to {@code ROLE_*} authorities
     */
    public AccessToken issue(UUID userId, UUID sessionId, String role) {
        Instant now = Instant.now(clock);
        long ttlSeconds = props.auth().accessTokenTtl().toSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_ROLE, role)
                .build();
        JwsHeader header =
                JwsHeader.with(MacAlgorithm.HS256).keyId(props.jwt().keyId()).build();
        String value =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, ttlSeconds);
    }
}
