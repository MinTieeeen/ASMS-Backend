package com.asms.security;

import com.asms.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers unauthenticated requests with a Problem Details body whose {@code code} tells the frontend what to do
 * (section 7.6): {@code AUTH_ACCESS_TOKEN_EXPIRED} triggers a silent refresh, {@code AUTH_ACCESS_TOKEN_MISSING} and
 * {@code AUTH_ACCESS_TOKEN_INVALID} send the user back to the login page.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class AuthProblemEntryPoint implements AuthenticationEntryPoint {

    // Description produced by Spring Security's JwtTimestampValidator
    private static final String EXPIRED_DESCRIPTION_PREFIX = "Jwt expired at";

    private final ProblemResponseWriter writer;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(response, resolveCode(ex));
    }

    static ErrorCode resolveCode(AuthenticationException ex) {
        if (!(ex instanceof InvalidBearerTokenException)) {
            return ErrorCode.AUTH_ACCESS_TOKEN_MISSING;
        }
        return isExpired(ex) ? ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED : ErrorCode.AUTH_ACCESS_TOKEN_INVALID;
    }

    private static boolean isExpired(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation) {
                return validation.getErrors().stream()
                        .anyMatch(e -> e.getDescription() != null
                                && e.getDescription().startsWith(EXPIRED_DESCRIPTION_PREFIX));
            }
        }
        return false;
    }
}
