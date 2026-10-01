package com.asms.security;

import com.asms.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Creates, reads and clears the refresh token cookie {@code rt}: {@code HttpOnly; Secure; SameSite=Strict;
 * Path=/api/v1/auth} (NFR-AUTH-05, section 7.2).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    private static final String SAME_SITE = "Strict";

    private final AppProperties props;

    /**
     * @param maxAge lifetime when "remember me" is on; {@code null} makes a browser-session cookie (BR-AUTH-05)
     */
    public ResponseCookie create(String refreshToken, @Nullable Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookie(refreshToken);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    @Nullable
    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, props.auth().cookie().name());
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        AppProperties.Cookie config = props.auth().cookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(SAME_SITE)
                .path(config.path());
        if (config.hasDomain()) {
            builder.domain(config.domain());
        }
        return builder;
    }
}
