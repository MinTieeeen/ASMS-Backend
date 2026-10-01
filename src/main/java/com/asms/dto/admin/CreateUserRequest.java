package com.asms.dto.admin;

import com.asms.entity.user.Language;
import com.asms.entity.user.SystemRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body of API-AUTH-12: an Admin creates an account (UC-AUTH-07, BR-AUTH-14). There is no password: the user sets it
 * when activating. Trimming and the user ID format are checked by the service.
 *
 * @param userCode sign-in identifier: the MSSV of a student, a code such as "admin" for an Admin; case does not matter
 * @param language language of the welcome email and the UI; Vietnamese when omitted
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 20) String userCode,
        @NotNull SystemRole systemRole,
        @Nullable Language language) {}
