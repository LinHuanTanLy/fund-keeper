package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByEmail(String email);

    Optional<UserJpaEntity> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user
              from UserJpaEntity user
             where user.publicId = :publicId
            """)
    Optional<UserJpaEntity> findByPublicIdForUpdate(
            @Param("publicId") String publicId);
}
