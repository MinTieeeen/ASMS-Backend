package com.asms.dto.auth;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Result of opening or refreshing a session, handed from the service to the controller. Not part of the API: the
 * controller returns {@link #body()} and turns the refresh token into a cookie.
 *
 * @param refreshToken raw refresh token, only ever sent in the {@code rt} cookie
 * @param cookieMaxAge cookie lifetime when "remember me" is on; {@code null} for a browser-session cookie (BR-AUTH-05)
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record IssuedSession(
        AuthTokenResponse body,
        String refreshToken,
        @Nullable Duration cookieMaxAge) {}
