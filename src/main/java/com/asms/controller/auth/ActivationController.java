package com.asms.controller.auth;

import com.asms.constant.ApiPaths;
import com.asms.dto.auth.ActivateAccountRequest;
import com.asms.dto.auth.ActivationTokenInfoResponse;
import com.asms.dto.auth.TokenRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.service.auth.ActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account activation endpoints (API-AUTH-09, 10). The activation token travels in the body, never in the URL.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login, sessions and devices")
public class ActivationController {

    private final ActivationService activationService;

    @PostMapping(ApiPaths.Auth.ACTIVATION_VALIDATE)
    @SecurityRequirements
    @Operation(operationId = "validateActivationToken", summary = "Check an activation link (API-AUTH-09)")
    @ApiResponse(responseCode = "400", description = "AUTH_TOKEN_INVALID with reason NOT_FOUND, EXPIRED or USED")
    @ApiResponse(responseCode = "409", description = "AUTH_ACCOUNT_ALREADY_ACTIVE")
    public ActivationTokenInfoResponse validateActivationToken(@Valid @RequestBody TokenRequest request) {
        return activationService.validateActivationToken(request);
    }

    @PostMapping(ApiPaths.Auth.ACTIVATE)
    @SecurityRequirements
    @Operation(operationId = "activateAccount", summary = "Set the first password and activate (API-AUTH-10)")
    @ApiResponse(responseCode = "204", description = "Account activated; the user must now log in")
    @ApiResponse(responseCode = "400", description = "AUTH_TOKEN_INVALID or AUTH_PASSWORD_POLICY (violations)")
    @ApiResponse(responseCode = "409", description = "AUTH_ACCOUNT_ALREADY_ACTIVE")
    public ResponseEntity<Void> activateAccount(@Valid @RequestBody ActivateAccountRequest request, ClientInfo client) {
        activationService.activate(request, client);
        return ResponseEntity.noContent().build();
    }
}
