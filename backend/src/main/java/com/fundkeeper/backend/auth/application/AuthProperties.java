package com.fundkeeper.backend.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fundkeeper.auth")
public record AuthProperties(
        String issuer,
        String audience,
        String jwtSecretBase64,
        String verificationSecretBase64,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration verificationCodeTtl,
        Duration verificationCodeCooldown,
        int verificationCodeMaxAttempts,
        Duration loginAttemptWindow,
        Duration loginBlockDuration,
        int loginMaxAttempts,
        String emailCodeStore,
        String loginAttemptStore,
        String mailSender,
        String mailFrom) {

    public AuthProperties {
        requireText(issuer, "issuer");
        requireText(audience, "audience");
        requireText(jwtSecretBase64, "jwt-secret-base64");
        requireText(verificationSecretBase64, "verification-secret-base64");
        requirePositive(accessTokenTtl, "access-token-ttl");
        requirePositive(refreshTokenTtl, "refresh-token-ttl");
        requirePositive(verificationCodeTtl, "verification-code-ttl");
        requirePositive(verificationCodeCooldown, "verification-code-cooldown");
        requirePositive(loginAttemptWindow, "login-attempt-window");
        requirePositive(loginBlockDuration, "login-block-duration");
        if (verificationCodeMaxAttempts < 1 || loginMaxAttempts < 1) {
            throw new IllegalArgumentException("Authentication attempt limits must be positive");
        }
        requireChoice(emailCodeStore, "email-code-store", "redis", "memory");
        requireChoice(loginAttemptStore, "login-attempt-store", "redis", "memory");
        requireChoice(mailSender, "mail-sender", "smtp", "memory");
        requireText(mailFrom, "mail-from");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "fundkeeper.auth." + name + " must be configured");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "fundkeeper.auth." + name + " must be positive");
        }
    }

    private static void requireChoice(
            String value,
            String name,
            String first,
            String second) {
        if (!first.equals(value) && !second.equals(value)) {
            throw new IllegalArgumentException(
                    "fundkeeper.auth." + name
                            + " must be '" + first + "' or '" + second + "'");
        }
    }
}
