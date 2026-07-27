package com.fundkeeper.backend.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FundTransaction(
        Long id,
        String publicId,
        long userId,
        long accountId,
        long fundId,
        String requestId,
        String requestFingerprint,
        TransactionType type,
        SellMode sellMode,
        TransactionStatus status,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        BigDecimal expectedAmount,
        BigDecimal actualReceivedAmount,
        BigDecimal removedCost,
        BigDecimal realizedProfit,
        BigDecimal shares,
        BigDecimal positionSharesBefore,
        BigDecimal positionCostBefore,
        PositionStatus positionStatusBefore,
        LocalDate positionHoldingStartDateBefore,
        LocalDate submittedDate,
        SubmittedPeriod submittedPeriod,
        LocalDate effectiveTradeDate,
        LocalDate confirmedDate,
        LocalDate navDate,
        BigDecimal unitNav,
        String navSource,
        BigDecimal feeRate,
        String feeSource,
        PendingReason pendingReason,
        String note,
        String cancellationReason,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt) {

    public FundTransaction {
        Objects.requireNonNull(publicId);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(requestFingerprint);
        Objects.requireNonNull(type);
        Objects.requireNonNull(status);
        Objects.requireNonNull(grossAmount);
        Objects.requireNonNull(submittedDate);
        Objects.requireNonNull(submittedPeriod);
        Objects.requireNonNull(effectiveTradeDate);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static FundTransaction createBuy(
            long userId,
            long accountId,
            long fundId,
            String requestId,
            String requestFingerprint,
            TransactionStatus status,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            BigDecimal shares,
            FundPosition positionBefore,
            LocalDate submittedDate,
            SubmittedPeriod submittedPeriod,
            LocalDate effectiveTradeDate,
            LocalDate confirmedDate,
            LocalDate navDate,
            BigDecimal unitNav,
            String navSource,
            BigDecimal feeRate,
            String feeSource,
            PendingReason pendingReason,
            String note,
            Instant now) {
        PositionSnapshot snapshot =
                status == TransactionStatus.ESTIMATED
                                || status == TransactionStatus.CONFIRMED
                        ? positionSnapshot(positionBefore)
                        : PositionSnapshot.empty();
        return new FundTransaction(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                TransactionType.BUY,
                null,
                status,
                grossAmount,
                feeAmount,
                netAmount,
                null,
                null,
                null,
                null,
                shares,
                snapshot.shares(),
                snapshot.cost(),
                snapshot.status(),
                snapshot.holdingStartDate(),
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                pendingReason,
                note,
                null,
                null,
                now,
                now);
    }

    public static FundTransaction createSell(
            long userId,
            long accountId,
            long fundId,
            String requestId,
            String requestFingerprint,
            SellMode sellMode,
            TransactionStatus status,
            BigDecimal amount,
            BigDecimal expectedAmount,
            BigDecimal actualReceivedAmount,
            BigDecimal removedCost,
            BigDecimal realizedProfit,
            BigDecimal shares,
            FundPosition positionBefore,
            LocalDate submittedDate,
            SubmittedPeriod submittedPeriod,
            LocalDate effectiveTradeDate,
            LocalDate confirmedDate,
            LocalDate navDate,
            BigDecimal unitNav,
            String navSource,
            PendingReason pendingReason,
            String note,
            Instant now) {
        return new FundTransaction(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                TransactionType.SELL,
                sellMode,
                status,
                amount,
                null,
                actualReceivedAmount,
                expectedAmount,
                actualReceivedAmount,
                removedCost,
                realizedProfit,
                shares,
                positionBefore.shares(),
                positionBefore.remainingCost(),
                positionBefore.status(),
                positionBefore.holdingStartDate(),
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                null,
                null,
                pendingReason,
                note,
                null,
                null,
                now,
                now);
    }

    public static FundTransaction createPositionAdjustment(
            long userId,
            long accountId,
            long fundId,
            String requestId,
            String requestFingerprint,
            TransactionStatus status,
            BigDecimal costAmount,
            BigDecimal currentAmount,
            BigDecimal shares,
            LocalDate snapshotDate,
            SubmittedPeriod submittedPeriod,
            LocalDate confirmedDate,
            LocalDate navDate,
            BigDecimal unitNav,
            String navSource,
            String note,
            Instant now) {
        return new FundTransaction(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                TransactionType.POSITION_ADJUSTMENT,
                null,
                status,
                costAmount,
                null,
                currentAmount,
                null,
                null,
                null,
                null,
                shares,
                null,
                null,
                null,
                null,
                snapshotDate,
                submittedPeriod,
                snapshotDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                null,
                null,
                null,
                note,
                null,
                null,
                now,
                now);
    }

    public boolean hasPositionSnapshot() {
        return positionSharesBefore != null
                && positionCostBefore != null
                && positionStatusBefore != null;
    }

    public FundTransaction confirmSell(
            BigDecimal finalAmount,
            BigDecimal finalShares,
            BigDecimal finalRemovedCost,
            BigDecimal finalRealizedProfit,
            LocalDate finalConfirmedDate,
            Instant now) {
        return new FundTransaction(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                type,
                sellMode,
                TransactionStatus.CONFIRMED,
                finalAmount,
                feeAmount,
                finalAmount,
                expectedAmount,
                finalAmount,
                finalRemovedCost,
                finalRealizedProfit,
                finalShares,
                positionSharesBefore,
                positionCostBefore,
                positionStatusBefore,
                positionHoldingStartDateBefore,
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                finalConfirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                null,
                note,
                null,
                null,
                createdAt,
                now);
    }

    public FundTransaction cancelSell(
            String reason,
            Instant now) {
        return new FundTransaction(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                type,
                sellMode,
                TransactionStatus.CANCELLED,
                grossAmount,
                feeAmount,
                netAmount,
                expectedAmount,
                actualReceivedAmount,
                removedCost,
                realizedProfit,
                shares,
                positionSharesBefore,
                positionCostBefore,
                positionStatusBefore,
                positionHoldingStartDateBefore,
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                null,
                note,
                reason,
                now,
                createdAt,
                now);
    }

    public FundTransaction confirmBuy(
            BigDecimal finalShares,
            LocalDate finalConfirmedDate,
            FundPosition positionBefore,
            Instant now) {
        PositionSnapshot snapshot = hasPositionSnapshot()
                ? new PositionSnapshot(
                        positionSharesBefore,
                        positionCostBefore,
                        positionStatusBefore,
                        positionHoldingStartDateBefore)
                : positionSnapshot(positionBefore);
        return new FundTransaction(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                type,
                sellMode,
                TransactionStatus.CONFIRMED,
                grossAmount,
                feeAmount,
                netAmount,
                expectedAmount,
                actualReceivedAmount,
                removedCost,
                realizedProfit,
                finalShares,
                snapshot.shares(),
                snapshot.cost(),
                snapshot.status(),
                snapshot.holdingStartDate(),
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                finalConfirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                null,
                note,
                null,
                null,
                createdAt,
                now);
    }

    public FundTransaction cancelBuy(
            String reason,
            Instant now) {
        return new FundTransaction(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                type,
                sellMode,
                TransactionStatus.CANCELLED,
                grossAmount,
                feeAmount,
                netAmount,
                expectedAmount,
                actualReceivedAmount,
                removedCost,
                realizedProfit,
                shares,
                positionSharesBefore,
                positionCostBefore,
                positionStatusBefore,
                positionHoldingStartDateBefore,
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                null,
                note,
                reason,
                now,
                createdAt,
                now);
    }

    public boolean appliesToPosition() {
        return status == TransactionStatus.ESTIMATED
                || status == TransactionStatus.CONFIRMED;
    }

    public boolean positionWasEmptyBefore() {
        return hasPositionSnapshot()
                && positionSharesBefore.signum() == 0
                && positionCostBefore.signum() == 0;
    }

    private static PositionSnapshot positionSnapshot(
            FundPosition position) {
        if (position == null) {
            return new PositionSnapshot(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    PositionStatus.CONFIRMED,
                    null);
        }
        return new PositionSnapshot(
                position.shares(),
                position.remainingCost(),
                position.status(),
                position.holdingStartDate());
    }

    private record PositionSnapshot(
            BigDecimal shares,
            BigDecimal cost,
            PositionStatus status,
            LocalDate holdingStartDate) {

        private static PositionSnapshot empty() {
            return new PositionSnapshot(
                    null,
                    null,
                    null,
                    null);
        }
    }
}
