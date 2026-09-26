package com.asms.controller.auth;

import com.asms.constant.ApiPaths;
import com.asms.dto.auth.AuthTokenResponse;
import com.asms.dto.auth.CurrentUserResponse;
import com.asms.dto.auth.IssuedSession;
import com.asms.dto.auth.LoginRequest;
import com.asms.dto.auth.SessionResponse;
import com.asms.dto.common.ClientInfo;
import com.asms.security.RefreshTokenCookies;
import com.asms.security.SecurityUtils;
import com.asms.service.auth.AuthService;
import com.asms.service.auth.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, session and device endpoints of the Auth module (API-AUTH-01 to 05, 14, 15).
 *
 * <p>The refresh token only travels in the httpOnly {@code rt} cookie; it never appears in a body.
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
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final RefreshTokenCookies refreshTokenCookies;

    @PostMapping(ApiPaths.Auth.LOGIN)
    @SecurityRequirements
    @Operation(operationId = "login", summary = "Log in with email and password (API-AUTH-01)")
    @ApiResponse(responseCode = "200", description = "Logged in; the refresh token is set in the rt cookie")
    @ApiResponse(responseCode = "401", description = "AUTH_INVALID_CREDENTIALS")
    @ApiResponse(responseCode = "403", description = "AUTH_ACCOUNT_LOCKED")
    @ApiResponse(responseCode = "423", description = "AUTH_ACCOUNT_TEMP_LOCKED with retryAfterSeconds")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED with retryAfterSeconds")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, ClientInfo client) {
        return withRefreshCookie(authService.login(request, client));
    }

    @PostMapping(ApiPaths.Auth.REFRESH)
    @SecurityRequirements
    @Operation(
            operationId = "refreshSession",
            summary = "Rotate the refresh token cookie and get a new access token (API-AUTH-02)")
    @ApiResponse(responseCode = "200", description = "Refreshed; a new rt cookie is set")
    @ApiResponse(responseCode = "401", description = "AUTH_REFRESH_INVALID or AUTH_REFRESH_REUSED")
    @ApiResponse(
            responseCode = "403",
            description = "AUTH_ACCOUNT_LOCKED, or AUTH_FORBIDDEN when Origin is not allowed")
    @ApiResponse(responseCode = "409", description = "AUTH_REFRESH_RACE: retry once after 300 ms")
    public ResponseEntity<AuthTokenResponse> refreshSession(HttpServletRequest request, ClientInfo client) {
        return withRefreshCookie(sessionService.refresh(refreshTokenCookies.read(request), client));
    }

    @PostMapping(ApiPaths.Auth.LOGOUT)
    @SecurityRequirements
    @Operation(operationId = "logout", summary = "Log out this device (API-AUTH-03)")
    @ApiResponse(responseCode = "204", description = "Logged out; the rt cookie is cleared")
    public ResponseEntity<Void> logout(HttpServletRequest request, ClientInfo client) {
        sessionService.logout(refreshTokenCookies.read(request), client);
        return clearedRefreshCookie();
    }

    @PostMapping(ApiPaths.Auth.LOGOUT_ALL)
    @Operation(operationId = "logoutAll", summary = "Log out every device, including this one (API-AUTH-04)")
    @ApiResponse(responseCode = "204", description = "Logged out everywhere; the rt cookie is cleared")
    public ResponseEntity<Void> logoutAll(ClientInfo client) {
        sessionService.logoutAll(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId(), client);
        return clearedRefreshCookie();
    }

    @GetMapping(ApiPaths.Auth.ME)
    @Operation(operationId = "getCurrentUser", summary = "Signed-in user (API-AUTH-05)")
    public CurrentUserResponse getCurrentUser() {
        return authService.getCurrentUser(SecurityUtils.getCurrentUserId());
    }

    @GetMapping(ApiPaths.Auth.SESSIONS)
    @Operation(operationId = "listSessions", summary = "Devices currently signed in, this one first (API-AUTH-14)")
    public List<SessionResponse> listSessions() {
        return sessionService.listActiveSessions(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId());
    }

    @DeleteMapping(ApiPaths.Auth.SESSION)
    @Operation(operationId = "revokeSession", summary = "Log out another device (API-AUTH-15)")
    @ApiResponse(responseCode = "204", description = "Device logged out")
    @ApiResponse(responseCode = "400", description = "AUTH_SESSION_IS_CURRENT: use logout instead")
    @ApiResponse(responseCode = "404", description = "AUTH_SESSION_NOT_FOUND")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, ClientInfo client) {
        sessionService.revokeOtherSession(
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId(), sessionId, client);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(IssuedSession issued) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookies
                                .create(issued.refreshToken(), issued.cookieMaxAge())
                                .toString())
                .body(issued.body());
    }

    private ResponseEntity<Void> clearedRefreshCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }
}
