package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fundkeeper.backend.auth.domain.AuthSession;

@Entity
@Table(name = "auth_sessions")
class AuthSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AuthSessionJpaEntity() {
    }

    private AuthSessionJpaEntity(AuthSession session) {
        this.id = session.id();
        this.publicId = session.publicId();
        this.userId = session.userId();
        this.refreshTokenHash = session.refreshTokenHash();
        this.expiresAt = session.expiresAt();
        this.revokedAt = session.revokedAt();
        this.lastUsedAt = session.lastUsedAt();
        this.createdAt = session.createdAt();
        this.updatedAt = session.updatedAt();
    }

    static AuthSessionJpaEntity fromDomain(AuthSession session) {
        return new AuthSessionJpaEntity(session);
    }

    void apply(AuthSession session) {
        this.revokedAt = session.revokedAt();
        this.lastUsedAt = session.lastUsedAt();
        this.updatedAt = session.updatedAt();
    }

    AuthSession toDomain() {
        return new AuthSession(
                id,
                publicId,
                userId,
                refreshTokenHash,
                expiresAt,
                revokedAt,
                lastUsedAt,
                createdAt,
                updatedAt);
    }
}
