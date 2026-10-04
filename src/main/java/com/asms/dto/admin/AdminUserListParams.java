package com.asms.dto.admin;

import com.asms.entity.user.SystemRole;
import com.asms.entity.user.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Query parameters of API-USER-07 (BR-USER-17).
 *
 * @param q full name (contains, accent-insensitive), email or user ID (starts with), 2 to 100 characters
 * @param status repeat the parameter to pick several
 * @param sort {@code createdAt}, {@code fullName} or {@code lastLoginAt}, with {@code ,asc} or {@code ,desc}
 * @param page first page is 1
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record AdminUserListParams(
        @Nullable @Size(min = 2, max = 100) String q,
        @Nullable List<UserStatus> status,
        @Nullable SystemRole role,
        @Nullable UUID schoolId,

        @Nullable
        @Pattern(regexp = "^(createdAt|fullName|lastLoginAt)(,(asc|desc))?$")
        @Schema(example = "createdAt,desc")
        String sort,

        @Nullable @Min(1) Integer page,
        @Nullable @Min(1) @Max(100) Integer size) {

    public static final int DEFAULT_SIZE = 20;
}
