package com.asms.exception;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy %s với id %s".formatted(resourceName, id));
    }
}
