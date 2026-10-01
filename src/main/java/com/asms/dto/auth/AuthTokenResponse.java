package com.asms.dto.auth;

/**
 * Response of login (API-AUTH-01) and refresh (API-AUTH-02). The refresh token is never in the body; it is set as the
 * httpOnly {@code rt} cookie (NFR-AUTH-05).
 *
 * @param accessToken JWT to send in the {@code Authorization: Bearer} header
 * @param expiresIn access token lifetime in seconds
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record AuthTokenResponse(String accessToken, long expiresIn, CurrentUserResponse user) {}
