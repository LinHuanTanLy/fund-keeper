package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserStatus;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "email_normalized", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserJpaEntity() {
    }

    private UserJpaEntity(User user) {
        this.id = user.id();
        this.publicId = user.publicId();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.status = user.status();
        this.tokenVersion = user.tokenVersion();
        this.createdAt = user.createdAt();
        this.updatedAt = user.updatedAt();
    }

    static UserJpaEntity fromDomain(User user) {
        return new UserJpaEntity(user);
    }

    void apply(User user) {
        this.passwordHash = user.passwordHash();
        this.status = user.status();
        this.tokenVersion = user.tokenVersion();
        this.updatedAt = user.updatedAt();
    }

    User toDomain() {
        return new User(
                id,
                publicId,
                email,
                passwordHash,
                status,
                tokenVersion,
                createdAt,
                updatedAt);
    }
}
