package com.asms.controller.admin;

import com.asms.constant.ApiPaths;
import com.asms.dto.admin.ActivationEmailResponse;
import com.asms.dto.admin.AdminUserFilter;
import com.asms.dto.admin.AdminUserResponse;
import com.asms.dto.admin.CreateUserRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.dto.common.PageResponse;
import com.asms.security.SecurityUtils;
import com.asms.service.auth.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account management by an Admin (API-AUTH-12, API-AUTH-13, SCR-AUTH-06). Every path is under
 * {@code /api/v1/admin/**}, which requires the {@code ADMIN} role (BR-AUTH-15).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@RestController
@RequestMapping(ApiPaths.ADMIN_USERS)
@RequiredArgsConstructor
@Tag(name = "Admin users", description = "Account creation and activation emails")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createUser", summary = "Create an account and send the activation email")
    @ApiResponse(responseCode = "201", description = "Account created in PENDING_ACTIVATION")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (errors per field)")
    @ApiResponse(responseCode = "403", description = "AUTH_FORBIDDEN")
    @ApiResponse(responseCode = "409", description = "USER_EMAIL_EXISTS or USER_CODE_EXISTS")
    public AdminUserResponse createUser(@Valid @RequestBody CreateUserRequest request, ClientInfo client) {
        return adminUserService.createUser(SecurityUtils.getCurrentUserId(), request, client);
    }

    @PostMapping(ApiPaths.AdminUsers.ACTIVATION_EMAIL)
    @Operation(operationId = "resendActivationEmail", summary = "Send the activation email again")
    @ApiResponse(responseCode = "200", description = "New link sent; the previous one no longer works")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "USER_NOT_PENDING")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED: more than 5 times per hour")
    public ActivationEmailResponse resendActivationEmail(@PathVariable UUID userId, ClientInfo client) {
        return adminUserService.resendActivation(SecurityUtils.getCurrentUserId(), userId, client);
    }

    @GetMapping
    @Operation(operationId = "listUsers", summary = "Users, filtered by status and keyword")
    public PageResponse<AdminUserResponse> listUsers(
            @Valid @ParameterObject AdminUserFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return adminUserService.listUsers(filter, pageable);
    }
}
