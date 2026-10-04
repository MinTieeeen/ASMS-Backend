package com.asms.controller.user;

import com.asms.constant.ApiPaths;
import com.asms.dto.user.GithubAccountResponse;
import com.asms.dto.user.GithubAuthorizeResponse;
import com.asms.dto.user.GithubCallbackRequest;
import com.asms.security.SecurityUtils;
import com.asms.service.user.GithubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub account of the signed-in user, on the profile page (SCR-USER-01 (10), (16)–(18)).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@RestController
@RequestMapping(ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "GitHub", description = "Connect a GitHub account to the profile")
public class GithubController {

    private final GithubService githubService;

    @PostMapping(ApiPaths.Users.ME_GITHUB_AUTHORIZE)
    @Operation(operationId = "authorizeGithub", summary = "Start connecting GitHub (API-USER-17)")
    @ApiResponse(responseCode = "200", description = "URL of the GitHub authorization page")
    @ApiResponse(responseCode = "409", description = "GITHUB_ALREADY_CONNECTED")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED")
    @ApiResponse(responseCode = "502", description = "GITHUB_UNAVAILABLE: OAuth App not configured")
    public GithubAuthorizeResponse authorizeGithub() {
        return githubService.authorize(SecurityUtils.getCurrentUserId());
    }

    @PostMapping(ApiPaths.Users.ME_GITHUB_CALLBACK)
    @Operation(operationId = "completeGithubConnection", summary = "Finish connecting GitHub (API-USER-18)")
    @ApiResponse(responseCode = "200", description = "Connected account")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or GITHUB_STATE_INVALID")
    @ApiResponse(responseCode = "409", description = "GITHUB_ALREADY_CONNECTED or GITHUB_ACCOUNT_IN_USE")
    @ApiResponse(responseCode = "502", description = "GITHUB_UNAVAILABLE")
    public GithubAccountResponse completeGithubConnection(@Valid @RequestBody GithubCallbackRequest request) {
        return githubService.completeConnection(SecurityUtils.getCurrentUserId(), request.code(), request.state());
    }

    @PostMapping(ApiPaths.Users.ME_GITHUB_REFRESH)
    @Operation(operationId = "refreshGithub", summary = "Read the GitHub profile again (API-USER-19)")
    @ApiResponse(responseCode = "200", description = "Updated account")
    @ApiResponse(responseCode = "404", description = "GITHUB_NOT_CONNECTED")
    @ApiResponse(responseCode = "429", description = "AUTH_RATE_LIMITED: once a minute")
    @ApiResponse(responseCode = "502", description = "GITHUB_UNAVAILABLE")
    public GithubAccountResponse refreshGithub() {
        return githubService.refresh(SecurityUtils.getCurrentUserId());
    }

    @DeleteMapping(ApiPaths.Users.ME_GITHUB)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "disconnectGithub", summary = "Disconnect GitHub (API-USER-20)")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    @ApiResponse(responseCode = "404", description = "GITHUB_NOT_CONNECTED")
    public void disconnectGithub() {
        githubService.disconnect(SecurityUtils.getCurrentUserId());
    }
}
