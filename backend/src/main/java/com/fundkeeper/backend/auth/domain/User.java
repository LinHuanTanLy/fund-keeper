package com.fundkeeper.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final Long id;
    private final String publicId;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final long tokenVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(
            Long id,
            String publicId,
            String email,
            String passwordHash,
            UserStatus status,
            long tokenVersion,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.publicId = Objects.requireNonNull(publicId);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = Objects.requireNonNull(status);
        this.tokenVersion = tokenVersion;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static User register(String email, String passwordHash, Instant now) {
        return new User(
                null,
                UUID.randomUUID().toString(),
                email,
                passwordHash,
                UserStatus.ACTIVE,
                0,
                now,
                now);
    }

    public User changePassword(String newPasswordHash, Instant now) {
        return new User(
                id,
                publicId,
                email,
                newPasswordHash,
                status,
                tokenVersion + 1,
                createdAt,
                now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public Long id() {
        return id;
    }

    public String publicId() {
        return publicId;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
