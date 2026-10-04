package com.asms.repository.user;

import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.util.SearchNormalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
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

    public static Specification<User> hasStatusIn(@Nullable Collection<UserStatus> statuses) {
        return (root, query, cb) -> statuses == null || statuses.isEmpty()
                ? null
                : root.get("status").in(statuses);
    }

    public static Specification<User> hasRole(@Nullable SystemRole role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("systemRole"), role);
    }

    public static Specification<User> hasSchool(@Nullable UUID schoolId) {
        return (root, query, cb) ->
                schoolId == null ? null : cb.equal(root.get("school").get("id"), schoolId);
    }

    /**
     * Admin search (BR-USER-17, section 8.4): full name contains the keyword, accents and case ignored
     * ({@code full_name_search}, trigram index), or email / user ID start with it.
     */
    public static Specification<User> matchesSearch(@Nullable String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String trimmed = keyword.strip();
            String name = "%" + SearchNormalizer.escapeLike(SearchNormalizer.toSearchKey(trimmed)) + "%";
            String email = SearchNormalizer.escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
            String code = SearchNormalizer.escapeLike(trimmed.toUpperCase(Locale.ROOT)) + "%";
            char escape = SearchNormalizer.likeEscape();
            return cb.or(
                    cb.like(root.get("fullNameSearch"), name, escape),
                    cb.like(root.get("email"), email, escape),
                    cb.like(root.get("userCode"), code, escape));
        };
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
