package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;

interface SpringDataFundTransactionRepository
        extends JpaRepository<FundTransactionJpaEntity, Long> {

    Optional<FundTransactionJpaEntity> findByUserIdAndRequestId(
            long userId,
            String requestId);

    Optional<FundTransactionJpaEntity> findByPublicIdAndUserId(
            String publicId,
            long userId);

    boolean existsByUserIdAndAccountIdAndFundIdAndTypeAndStatusIn(
            long userId,
            long accountId,
            long fundId,
            TransactionType type,
            Collection<TransactionStatus> statuses);
}
