package com.fundkeeper.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthSession {

    private final Long id;
    private final String publicId;
    private final long userId;
    private final String refreshTokenHash;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final Instant lastUsedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public AuthSession(
            Long id,
            String publicId,
            long userId,
            String refreshTokenHash,
            Instant expiresAt,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.publicId = Objects.requireNonNull(publicId);
        this.userId = userId;
        this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AuthSession create(
            long userId,
            String refreshTokenHash,
            Instant expiresAt,
            Instant now) {
        return new AuthSession(
                null,
                UUID.randomUUID().toString(),
                userId,
                refreshTokenHash,
                expiresAt,
                null,
                null,
                now,
                now);
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public AuthSession revoke(Instant now) {
        if (revokedAt != null) {
            return this;
        }
        return new AuthSession(
                id,
                publicId,
                userId,
                refreshTokenHash,
                expiresAt,
                now,
                lastUsedAt,
                createdAt,
                now);
    }

    public AuthSession markUsedAndRevoke(Instant now) {
        return new AuthSession(
                id,
                publicId,
                userId,
                refreshTokenHash,
                expiresAt,
                now,
                now,
                createdAt,
                now);
    }

    public Long id() {
        return id;
    }

    public String publicId() {
        return publicId;
    }

    public long userId() {
        return userId;
    }

    public String refreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
