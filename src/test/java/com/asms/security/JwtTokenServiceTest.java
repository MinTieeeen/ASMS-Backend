package com.asms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asms.config.AppProperties;
import com.asms.exception.ErrorCode;
import com.asms.support.TestProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");

    private final AppProperties props = TestProperties.appProperties();
    private final SecurityConfig securityConfig = new SecurityConfig();
    private final RevokedSessionStore revokedSessionStore = mock(RevokedSessionStore.class);

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService =
                new JwtTokenService(securityConfig.jwtEncoder(props), props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Section 7.2: access token carries sub, sid, role, jti, iss and a kid header")
    void issue_shouldProduceTokenWithExpectedClaims() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        JwtTokenService.AccessToken token = jwtTokenService.issue(userId, sessionId, "ADMIN");
        Jwt jwt = decoderAt(NOW).decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("kid", "k1");
        assertThat(jwt.getClaims()).doesNotContainKey("email");
    }

    @Test
    @DisplayName("Section 7.4: token of a revoked session is rejected")
    void decode_shouldReject_whenSessionRevoked() {
        when(revokedSessionStore.isRevoked(anyString())).thenReturn(true);
        String token = jwtTokenService
                .issue(UUID.randomUUID(), UUID.randomUUID(), "USER")
                .value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void entryPoint_shouldReportExpired_whenTokenExpired() {
        String token = jwtTokenService
                .issue(UUID.randomUUID(), UUID.randomUUID(), "USER")
                .value();
        JwtDecoder later = decoderAt(NOW.plusSeconds(3600));

        Throwable error = catchThrowable(() -> later.decode(token));

        assertThat(AuthProblemEntryPoint.resolveCode(new InvalidBearerTokenException(error.getMessage(), error)))
                .isEqualTo(ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void entryPoint_shouldReportInvalid_whenSignatureWrong() {
        String token = jwtTokenService
                .issue(UUID.randomUUID(), UUID.randomUUID(), "USER")
                .value();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        Throwable error = catchThrowable(() -> decoderAt(NOW).decode(tampered));

        assertThat(AuthProblemEntryPoint.resolveCode(new InvalidBearerTokenException(error.getMessage(), error)))
                .isEqualTo(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
    }

    private JwtDecoder decoderAt(Instant instant) {
        return securityConfig.jwtDecoder(
                props, new RevokedSessionValidator(revokedSessionStore), Clock.fixed(instant, ZoneOffset.UTC));
    }
}
