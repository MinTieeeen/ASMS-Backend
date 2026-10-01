package com.asms.repository.user;

import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filters of the Admin user list. A {@code null} argument means "no filter".
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {}

    public static Specification<User> hasStatus(@Nullable UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive "contains" on email, full name or user ID; LIKE wildcards in the input are literal. */
    public static Specification<User> matchesKeyword(@Nullable String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + escapeLike(keyword.strip().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("userCode")), pattern, LIKE_ESCAPE));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
