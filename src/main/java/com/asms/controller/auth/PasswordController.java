package com.asms.controller.auth;

import com.asms.constant.ApiPaths;
import com.asms.dto.auth.ChangePasswordRequest;
import com.asms.dto.auth.ForgotPasswordRequest;
import com.asms.dto.auth.ResetPasswordRequest;
import com.asms.dto.auth.ResetTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.AcceptedResponse;
import com.asms.dto.common.ClientInfo;
import com.asms.security.SecurityUtils;
import com.asms.service.auth.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password endpoints of the Auth module (API-AUTH-06, 07, 08, 11). Reset tokens travel in the body, never in the URL,
 * so they stay out of access logs.
 *
 * @author MinhTien
 * @version 1.0.1
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login, sessions and devices")
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping(ApiPaths.Auth.PASSWORD_FORGOT)
    @SecurityRequirements
    @Operation(
            operationId = "requestPasswordReset",
            summary = "Request a password reset link (API-AUTH-06)",
            description = "Always answers 202 whether or not the email exists (FR-AUTH-12).")
    @ApiResponse(responseCode = "202", description = "Accepted; an email is sent if the account can receive one")
    public ResponseEntity<AcceptedResponse> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequest request, ClientInfo client) {
        passwordService.requestPasswordReset(request, client);
        return ResponseEntity.accepted().body(AcceptedResponse.ACCEPTED);
    }

    @PostMapping(ApiPaths.Auth.PASSWORD_RESET_VALIDATE)
    @SecurityRequirements
    @Operation(operationId = "validateResetToken", summary = "Check a password reset link (API-AUTH-07)")
    @ApiResponse(responseCode = "200", description = "The link can be used")
    @ApiResponse(responseCode = "400", description = "AUTH_TOKEN_INVALID with reason NOT_FOUND, EXPIRED or USED")
    public ResetTokenInfoResponse validateResetToken(@Valid @RequestBody TokenRequest request) {
        return passwordService.validateResetToken(request);
    }

    @PostMapping(ApiPaths.Auth.PASSWORD_RESET)
    @SecurityRequirements
    @Operation(operationId = "resetPassword", summary = "Set a new password with a reset link (API-AUTH-08)")
    @ApiResponse(responseCode = "204", description = "Password reset; every session is logged out")
    @ApiResponse(
            responseCode = "400",
            description = "AUTH_TOKEN_INVALID, AUTH_PASSWORD_POLICY (violations) or AUTH_PASSWORD_SAME_AS_OLD")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, ClientInfo client) {
        passwordService.resetPassword(request, client);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(ApiPaths.Auth.PASSWORD)
    @Operation(operationId = "changePassword", summary = "Change the password while signed in (API-AUTH-11)")
    @ApiResponse(responseCode = "204", description = "Password changed; other devices are logged out")
    @ApiResponse(
            responseCode = "400",
            description = "AUTH_CURRENT_PASSWORD_WRONG, AUTH_PASSWORD_POLICY (violations) or AUTH_PASSWORD_SAME_AS_OLD")
    @ApiResponse(responseCode = "423", description = "AUTH_ACCOUNT_TEMP_LOCKED: this session was logged out")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, ClientInfo client) {
        passwordService.changePassword(
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId(), request, client);
        return ResponseEntity.noContent().build();
    }
}
