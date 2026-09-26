package com.asms.security;

import com.asms.config.AppProperties;
import com.asms.constant.ApiPaths;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security configuration (Auth specification section 7.5).
 *
 * <p>The JWT access token is sent in the {@code Authorization} header and validated without touching the database:
 * signature, issuer, expiry and the Redis blocklist of revoked sessions. The refresh token lives in the httpOnly
 * {@code rt} cookie; the endpoints that use it are protected by an {@code Origin} check instead of CSRF tokens.
 * Group-level permissions are checked in services, not here.
 *
 * @author MinhTien
 * @version 2.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Argon2id parameters of NFR-AUTH-03: 19 MiB memory, 2 iterations, parallelism 1
    private static final int ARGON2_SALT_LENGTH = 16;
    private static final int ARGON2_HASH_LENGTH = 32;
    private static final int ARGON2_PARALLELISM = 1;
    private static final int ARGON2_MEMORY_KIB = 19 * 1024;
    private static final int ARGON2_ITERATIONS = 2;

    private static final Duration JWT_CLOCK_SKEW = Duration.ofSeconds(30);

    private static final String[] DOCS_ENDPOINTS = {
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthProblemEntryPoint entryPoint,
            AuthProblemAccessDeniedHandler accessDeniedHandler,
            ProblemResponseWriter problemWriter,
            AppProperties props)
            throws Exception {
        RequestMatcher publicAuthEndpoints = publicAuthEndpoints();
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(publicAuthEndpoints)
                        .permitAll()
                        .requestMatchers(DOCS_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(ApiPaths.ADMIN + "/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(o -> o.bearerTokenResolver(bearerTokenResolver(publicAuthEndpoints))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new OriginCheckFilter(cookieEndpoints(), props.cors().allowedOrigins(), problemWriter),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties props, RevokedSessionValidator revokedSessionValidator, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(JWT_CLOCK_SKEW);
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator, new JwtIssuerValidator(props.jwt().issuer()), revokedSessionValidator));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties props) {
        // The JWK carries the kid so that the encoder can select it from the "kid" header (key rotation, section 7.2)
        OctetSequenceKey key = new OctetSequenceKey.Builder(jwtSecretKey(props))
                .keyID(props.jwt().keyId())
                .algorithm(JWSAlgorithm.HS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                ARGON2_SALT_LENGTH, ARGON2_HASH_LENGTH, ARGON2_PARALLELISM, ARGON2_MEMORY_KIB, ARGON2_ITERATIONS);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.addExposedHeader("Retry-After");
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Endpoints reachable without an access token (section 7.5). */
    private static RequestMatcher publicAuthEndpoints() {
        return postMatchers(
                ApiPaths.Auth.LOGIN,
                ApiPaths.Auth.REFRESH,
                ApiPaths.Auth.LOGOUT,
                ApiPaths.Auth.PASSWORD_FORGOT,
                ApiPaths.Auth.PASSWORD_RESET_VALIDATE,
                ApiPaths.Auth.PASSWORD_RESET,
                ApiPaths.Auth.ACTIVATION_VALIDATE,
                ApiPaths.Auth.ACTIVATE);
    }

    /** Endpoints that read the refresh token cookie and therefore need the Origin check (NFR-AUTH-07). */
    private static RequestMatcher cookieEndpoints() {
        return postMatchers(ApiPaths.Auth.REFRESH, ApiPaths.Auth.LOGOUT);
    }

    private static RequestMatcher postMatchers(String... authSubPaths) {
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        RequestMatcher[] matchers = new RequestMatcher[authSubPaths.length];
        for (int i = 0; i < authSubPaths.length; i++) {
            matchers[i] = builder.matcher(HttpMethod.POST, ApiPaths.AUTH + authSubPaths[i]);
        }
        return new OrRequestMatcher(matchers);
    }

    /**
     * Ignores the Authorization header on public auth endpoints, so that an expired access token cannot block login,
     * refresh or logout with a 401.
     */
    private static BearerTokenResolver bearerTokenResolver(RequestMatcher publicAuthEndpoints) {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> publicAuthEndpoints.matches(request) ? null : delegate.resolve(request);
    }

    /** Maps the {@code role} claim ({@code USER} / {@code ADMIN}) to {@code ROLE_USER} / {@code ROLE_ADMIN}. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey jwtSecretKey(AppProperties props) {
        return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
