package com.asms.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Root business exception. Services throw this class (or a subclass) with an {@link ErrorCode}; never throw a bare
 * {@link RuntimeException}.
 *
 * <p>Extra Problem Details fields (for example {@code retryAfterSeconds}, {@code reason}, {@code violations}) are
 * passed in {@link #getProperties()} and copied to the response by {@link GlobalExceptionHandler}.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public class BusinessException extends RuntimeException {

    public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> properties) {
        this(errorCode, errorCode.getDefaultMessage(), properties);
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> properties) {
        super(message);
        this.errorCode = errorCode;
        this.properties = Map.copyOf(properties);
    }

    /** Error that asks the client to wait, such as {@code AUTH_RATE_LIMITED} or {@code AUTH_ACCOUNT_TEMP_LOCKED}. */
    public static BusinessException retryAfter(ErrorCode errorCode, long retryAfterSeconds) {
        return new BusinessException(errorCode, Map.of(RETRY_AFTER_SECONDS, Math.max(1, retryAfterSeconds)));
    }
}
