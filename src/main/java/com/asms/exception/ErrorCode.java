package com.asms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business error codes returned in the {@code code} field of Problem Details (RFC 9457).
 *
 * <p>Naming: UPPER_SNAKE_CASE grouped by module. The frontend maps these codes to translated messages, so the default
 * message is only a fallback. Errors caused by a business rule should reference its BR code.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public enum ErrorCode {

    // ===== Common =====
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên đã hết hạn"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không đủ quyền thực hiện thao tác"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Gửi yêu cầu quá nhiều, vui lòng thử lại sau"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),

    // ===== Group =====
    GROUP_FULL(HttpStatus.CONFLICT, "Nhóm đã đủ thành viên (BR02)"),
    GROUP_ARCHIVED(HttpStatus.CONFLICT, "Nhóm đã lưu trữ, chỉ được xem (BR09)");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
