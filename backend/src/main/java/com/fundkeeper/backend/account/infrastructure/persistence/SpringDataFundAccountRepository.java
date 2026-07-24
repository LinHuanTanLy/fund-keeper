package com.fundkeeper.backend.account.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fundkeeper.backend.account.domain.AccountStatus;

interface SpringDataFundAccountRepository extends JpaRepository<FundAccountJpaEntity, Long> {

    List<FundAccountJpaEntity> findAllByUserIdOrderByCreatedAtAsc(long userId);

    List<FundAccountJpaEntity> findAllByUserIdAndStatusOrderByCreatedAtAsc(
            long userId,
            AccountStatus status);

    Optional<FundAccountJpaEntity> findByPublicIdAndUserId(
            String publicId,
            long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
              from FundAccountJpaEntity account
             where account.publicId = :publicId
               and account.userId = :userId
            """)
    Optional<FundAccountJpaEntity> findByPublicIdAndUserIdForUpdate(
            @Param("publicId") String publicId,
            @Param("userId") long userId);

    boolean existsByUserIdAndNormalizedNameAndStatusAndPublicIdNot(
            long userId,
            String normalizedName,
            AccountStatus status,
            String excludedPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
              from FundAccountJpaEntity account
             where account.userId = :userId
               and account.status = :status
            """)
    List<FundAccountJpaEntity> findActiveByUserIdForUpdate(
            @Param("userId") long userId,
            @Param("status") AccountStatus status);
}
