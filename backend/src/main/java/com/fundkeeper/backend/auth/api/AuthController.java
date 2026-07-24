package com.fundkeeper.backend.auth.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fundkeeper.backend.auth.application.AuthFacade;
import com.fundkeeper.backend.auth.application.AuthTokens;
import com.fundkeeper.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @PostMapping("/email-codes")
    ResponseEntity<ApiResponse<Void>> requestEmailCode(
            @Valid @RequestBody EmailCodeRequest request) {
        authFacade.requestEmailCode(request.email(), request.purpose());
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(
                        "OK",
                        "如果该邮箱可用于此操作，验证码邮件将会发送",
                        null,
                        java.time.Instant.now()));
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthTokens.UserView>> register(
            @Valid @RequestBody RegisterRequest request) {
        var user = authFacade.register(
                request.email(),
                request.password(),
                request.code());
        var view = new AuthTokens.UserView(user.publicId(), user.email());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(view));
    }

    @PostMapping("/login")
    ApiResponse<AuthTokens> login(
            @Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(
                authFacade.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthTokens> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(
                authFacade.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authFacade.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        authFacade.resetPassword(
                request.email(),
                request.code(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    ApiResponse<AuthTokens.UserView> currentUser(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(
                authFacade.currentUser(jwt.getSubject()));
    }
}
