package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSessionJpaEntity> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AuthSessionJpaEntity> findByPublicId(String publicId);

    @Modifying
    @Query("""
            update AuthSessionJpaEntity session
               set session.revokedAt = :revokedAt,
                   session.updatedAt = :revokedAt
             where session.userId = :userId
               and session.revokedAt is null
            """)
    int revokeAllByUserId(
            @Param("userId") long userId,
            @Param("revokedAt") Instant revokedAt);
}
