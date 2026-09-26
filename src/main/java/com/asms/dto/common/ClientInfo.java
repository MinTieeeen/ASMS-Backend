package com.asms.dto.common;

import org.jspecify.annotations.Nullable;

/**
 * Network details of the current HTTP request, resolved by the web layer and passed to services so that services
 * stay independent of the Servlet API. Used for sessions, rate limiting and auth event logs (FR-AUTH-23).
 *
 * @param ipAddress client IP (IPv4 or IPv6); behind a proxy it comes from the trusted forwarded headers
 * @param userAgent raw {@code User-Agent} header, if any
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record ClientInfo(String ipAddress, @Nullable String userAgent) {}
