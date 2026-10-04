package com.asms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business error codes returned in the {@code code} field of Problem Details (RFC 9457).
 *
 * <p>Naming: UPPER_SNAKE_CASE grouped by module. The frontend maps these codes to translated messages, so the default
 * message is only a fallback. Errors caused by a business rule should reference its BR code. Authentication and
 * authorization codes follow section 7.7 of the Auth specification and are used system-wide.
 *
 * @author MinhTien
 * @version 2.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public enum ErrorCode {

    // ===== Common =====
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),

    // ===== Auth: access token and permissions =====
    AUTH_ACCESS_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Thiếu access token"),
    AUTH_ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Access token không hợp lệ hoặc phiên đã bị thu hồi"),
    AUTH_ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token đã hết hạn"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "Không đủ quyền thực hiện thao tác"),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Gửi yêu cầu quá nhiều, vui lòng thử lại sau"),

    // ===== Auth: login and account state =====
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "ID người dùng hoặc mật khẩu không đúng"),
    AUTH_ACCOUNT_TEMP_LOCKED(HttpStatus.LOCKED, "Tài khoản đang bị khóa tạm (BR-AUTH-04)"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa bởi quản trị viên"),
    AUTH_ACCOUNT_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Tài khoản đã được kích hoạt"),

    // ===== Auth: session and refresh token =====
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn"),
    AUTH_REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token đã bị dùng lại, phiên đã bị thu hồi"),
    AUTH_REFRESH_RACE(HttpStatus.CONFLICT, "Phiên đang được làm mới ở tab khác, vui lòng thử lại"),
    AUTH_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên đăng nhập"),
    AUTH_SESSION_IS_CURRENT(HttpStatus.BAD_REQUEST, "Không thể thu hồi phiên hiện tại qua thao tác này"),

    // ===== Auth: email tokens and passwords =====
    AUTH_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Link đã hết hạn hoặc đã được sử dụng"),
    AUTH_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "Mật khẩu chưa đạt yêu cầu (BR-AUTH-01)"),
    AUTH_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại (BR-AUTH-12)"),
    AUTH_CURRENT_PASSWORD_WRONG(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_EMAIL_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    USER_CODE_EXISTS(HttpStatus.CONFLICT, "ID người dùng đã được sử dụng"),
    USER_NOT_PENDING(HttpStatus.CONFLICT, "Tài khoản không ở trạng thái chờ kích hoạt"),
    USER_VERSION_CONFLICT(HttpStatus.CONFLICT, "Thông tin đã được cập nhật ở nơi khác (BR-USER-14)"),
    USER_EMAIL_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "Chỉ đổi được email khi tài khoản chờ kích hoạt (BR-USER-08)"),
    USER_SELF_ACTION_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thực hiện được thao tác này với chính mình (BR-USER-09)"),
    USER_LAST_ADMIN(HttpStatus.CONFLICT, "Hệ thống cần ít nhất một quản trị viên đang hoạt động (BR-USER-10)"),
    USER_ALREADY_LOCKED(HttpStatus.CONFLICT, "Tài khoản đã bị khóa"),
    USER_NOT_LOCKED(HttpStatus.CONFLICT, "Tài khoản không bị khóa"),

    // ===== Avatar =====
    AVATAR_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ nhận ảnh JPEG, PNG hoặc WebP (BR-USER-07)"),
    AVATAR_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Ảnh lớn hơn 5 MB (BR-USER-07)"),
    AVATAR_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Ảnh hỏng, kích thước hoặc vùng cắt không hợp lệ (BR-USER-07)"),

    // ===== Catalogs =====
    SCHOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy trường"),
    SCHOOL_CODE_EXISTS(HttpStatus.CONFLICT, "Mã trường đã có"),
    SCHOOL_NAME_EXISTS(HttpStatus.CONFLICT, "Tên trường đã có"),
    SCHOOL_IN_USE(HttpStatus.CONFLICT, "Trường đang có người dùng, hãy chuyển sang ngừng dùng"),
    SCHOOL_VERSION_CONFLICT(HttpStatus.CONFLICT, "Trường đã được sửa ở nơi khác"),
    HOLIDAY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ngày nghỉ"),
    HOLIDAY_OVERLAP(HttpStatus.CONFLICT, "Khoảng ngày chồng lên ngày nghỉ đã có"),
    HOLIDAY_VERSION_CONFLICT(HttpStatus.CONFLICT, "Ngày nghỉ đã được sửa ở nơi khác"),

    // ===== GitHub =====
    GITHUB_ALREADY_CONNECTED(HttpStatus.CONFLICT, "Đã kết nối GitHub, hãy ngắt kết nối trước"),
    GITHUB_NOT_CONNECTED(HttpStatus.NOT_FOUND, "Chưa kết nối GitHub"),
    GITHUB_STATE_INVALID(HttpStatus.BAD_REQUEST, "Phiên kết nối GitHub không hợp lệ hoặc đã hết hạn"),
    GITHUB_ACCOUNT_IN_USE(HttpStatus.CONFLICT, "Tài khoản GitHub đã liên kết với người dùng khác"),
    GITHUB_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "GitHub không phản hồi"),

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
