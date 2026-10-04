package com.asms.dto.user;

import java.time.Instant;

/**
 * Response of API-USER-17: the GitHub page to send the browser to.
 *
 * @param expiresAt the state in the URL is valid until then (10 minutes)
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record GithubAuthorizeResponse(String authorizeUrl, Instant expiresAt) {}
