package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;

interface SpringDataFundTransactionRepository
        extends JpaRepository<FundTransactionJpaEntity, Long>,
        JpaSpecificationExecutor<FundTransactionJpaEntity> {

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

    @Query("""
            SELECT
                COALESCE(SUM(CASE
                    WHEN t.status = :confirmedStatus
                    THEN 1 ELSE 0 END), 0) AS confirmedSellCount,
                COALESCE(SUM(CASE
                    WHEN t.status = :confirmedStatus
                    THEN t.actualReceivedAmount ELSE 0 END), 0)
                    AS totalActualReceivedAmount,
                COALESCE(SUM(CASE
                    WHEN t.status = :confirmedStatus
                    THEN t.removedCost ELSE 0 END), 0)
                    AS totalRemovedCost,
                COALESCE(SUM(CASE
                    WHEN t.status = :confirmedStatus
                    THEN t.realizedProfit ELSE 0 END), 0)
                    AS totalRealizedProfit,
                COALESCE(SUM(CASE
                    WHEN t.status IN (:openStatuses)
                    THEN 1 ELSE 0 END), 0) AS openSellCount
            FROM FundTransactionJpaEntity t
            WHERE t.userId = :userId
              AND t.type = :sellType
              AND (:accountId IS NULL
                   OR t.accountId = :accountId)
              AND (:fundId IS NULL
                   OR t.fundId = :fundId)
            """)
    SellSummaryProjection summarizeSells(
            @Param("userId") long userId,
            @Param("accountId") Long accountId,
            @Param("fundId") Long fundId,
            @Param("sellType") TransactionType sellType,
            @Param("confirmedStatus") TransactionStatus confirmedStatus,
            @Param("openStatuses")
            Collection<TransactionStatus> openStatuses);
}
