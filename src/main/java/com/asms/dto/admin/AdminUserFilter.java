package com.asms.dto.admin;

import com.asms.entity.user.UserStatus;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Query parameters of {@code GET /api/v1/admin/users}.
 *
 * @param keyword case-insensitive match on email, full name or user ID
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public record AdminUserFilter(
        @Nullable UserStatus status,
        @Nullable @Size(max = 100) String keyword) {}
