package com.fundkeeper.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class CryptoSupport {

    private final SecureRandom secureRandom = new SecureRandom();

    public String randomRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String randomSixDigitCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    public String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String hmacSha256(String base64Secret, String value) {
        try {
            byte[] secret = decodeSecret(base64Secret, "verification");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    public byte[] decodeSecret(String base64Secret, String name) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(name + " secret must be configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Secret);
            if (decoded.length < 32) {
                throw new IllegalStateException(name + " secret must contain at least 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " secret must be valid Base64", exception);
        }
    }
}
