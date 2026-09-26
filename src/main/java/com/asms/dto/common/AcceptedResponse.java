package com.asms.dto.common;

/**
 * Body of requests accepted for asynchronous processing whose outcome must not be revealed, such as "forgot password"
 * (FR-AUTH-12).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record AcceptedResponse(String status) {

    public static final AcceptedResponse ACCEPTED = new AcceptedResponse("ACCEPTED");
}
