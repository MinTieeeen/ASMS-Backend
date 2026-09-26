package com.asms.exception;

/**
 * Thrown when the current user lacks the system or group permission for an action (HTTP 403).
 *
 * @author MinhTien
 * @version 1.0.1
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.AUTH_FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.AUTH_FORBIDDEN, message);
    }
}
