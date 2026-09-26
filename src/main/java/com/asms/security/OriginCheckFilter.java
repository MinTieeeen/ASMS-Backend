package com.asms.security;

import com.asms.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF protection for the endpoints that act on the refresh token cookie (NFR-AUTH-07): they only accept requests whose
 * {@code Origin} header is an allowed frontend origin. Together with {@code SameSite=Strict} this replaces CSRF tokens
 * (section 7.5).
 *
 * <p>Not a Spring bean on purpose: it is added to the security filter chain only, never to the servlet filter chain.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private final RequestMatcher protectedEndpoints;
    private final List<String> allowedOrigins;
    private final ProblemResponseWriter writer;

    public OriginCheckFilter(
            RequestMatcher protectedEndpoints, List<String> allowedOrigins, ProblemResponseWriter writer) {
        this.protectedEndpoints = protectedEndpoints;
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin)) {
            writer.write(response, ErrorCode.AUTH_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
