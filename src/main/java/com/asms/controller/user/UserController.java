package com.asms.controller.user;

import com.asms.constant.ApiPaths;
import com.asms.dto.auth.CurrentUserResponse;
import com.asms.dto.user.UpdateLanguageRequest;
import com.asms.security.SecurityUtils;
import com.asms.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings of the signed-in user.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@RestController
@RequestMapping(ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "Users", description = "Settings of the signed-in user")
public class UserController {

    private final UserService userService;

    @PutMapping(ApiPaths.Users.ME_LANGUAGE)
    @Operation(
            operationId = "updateMyLanguage",
            summary = "Change the language of emails and the UI (NFR14)",
            description = "Returns the updated user, same shape as GET /api/v1/auth/me.")
    public CurrentUserResponse updateMyLanguage(@Valid @RequestBody UpdateLanguageRequest request) {
        return userService.changeLanguage(SecurityUtils.getCurrentUserId(), request.language());
    }
}
