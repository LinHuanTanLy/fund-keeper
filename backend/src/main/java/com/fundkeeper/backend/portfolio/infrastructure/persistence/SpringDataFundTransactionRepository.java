package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFundTransactionRepository
        extends JpaRepository<FundTransactionJpaEntity, Long> {

    Optional<FundTransactionJpaEntity> findByUserIdAndRequestId(
            long userId,
            String requestId);

    Optional<FundTransactionJpaEntity> findByPublicIdAndUserId(
            String publicId,
            long userId);
}
