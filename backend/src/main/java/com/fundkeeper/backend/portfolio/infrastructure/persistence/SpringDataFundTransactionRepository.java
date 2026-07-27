package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FundTransactionJpaEntity> findForUpdateByPublicIdAndUserId(
            String publicId,
            long userId);

    boolean existsByUserIdAndAccountIdAndFundIdAndTypeAndStatusIn(
            long userId,
            long accountId,
            long fundId,
            TransactionType type,
            Collection<TransactionStatus> statuses);

    boolean existsByUserIdAndAccountIdAndTypeAndStatusIn(
            long userId,
            long accountId,
            TransactionType type,
            Collection<TransactionStatus> statuses);
}
