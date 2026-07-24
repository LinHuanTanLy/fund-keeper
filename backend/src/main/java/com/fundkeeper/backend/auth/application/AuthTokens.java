package com.fundkeeper.backend.auth.application;

import java.time.Instant;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserView user) {

    public static AuthTokens of(
            AccessTokenService.IssuedAccessToken accessToken,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserView user) {
        return new AuthTokens(
                accessToken.value(),
                refreshToken,
                "Bearer",
                accessToken.expiresAt(),
                refreshTokenExpiresAt,
                user);
    }

    public record UserView(String id, String email) {
    }
}
