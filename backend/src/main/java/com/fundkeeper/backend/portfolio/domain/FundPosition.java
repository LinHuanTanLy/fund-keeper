package com.fundkeeper.backend.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FundPosition(
        Long id,
        String publicId,
        long userId,
        long accountId,
        long fundId,
        BigDecimal shares,
        BigDecimal remainingCost,
        BigDecimal averageUnitCost,
        PositionStatus status,
        LocalDate holdingStartDate,
        Instant createdAt,
        Instant updatedAt) {

    private static final int SHARE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final int UNIT_COST_SCALE = 8;

    public FundPosition {
        Objects.requireNonNull(publicId);
        Objects.requireNonNull(shares);
        Objects.requireNonNull(remainingCost);
        Objects.requireNonNull(averageUnitCost);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static FundPosition fromBuy(
            long userId,
            long accountId,
            long fundId,
            BigDecimal shares,
            BigDecimal grossAmount,
            TransactionStatus transactionStatus,
            LocalDate holdingStartDate,
            Instant now) {
        BigDecimal normalizedShares = shares.setScale(
                SHARE_SCALE,
                RoundingMode.HALF_UP);
        BigDecimal normalizedCost = grossAmount.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP);
        return new FundPosition(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                normalizedShares,
                normalizedCost,
                averageCost(normalizedCost, normalizedShares),
                positionStatus(transactionStatus),
                holdingStartDate,
                now,
                now);
    }

    public static FundPosition fromSnapshot(
            long userId,
            long accountId,
            long fundId,
            BigDecimal shares,
            BigDecimal remainingCost,
            PositionStatus status,
            LocalDate holdingStartDate,
            Instant now) {
        BigDecimal normalizedShares = shares.setScale(
                SHARE_SCALE,
                RoundingMode.HALF_UP);
        BigDecimal normalizedCost = remainingCost.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP);
        return new FundPosition(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                normalizedShares,
                normalizedCost,
                averageCost(normalizedCost, normalizedShares),
                status,
                holdingStartDate,
                now,
                now);
    }

    public FundPosition applyBuy(
            BigDecimal boughtShares,
            BigDecimal grossAmount,
            TransactionStatus transactionStatus,
            LocalDate boughtHoldingStartDate,
            Instant now) {
        BigDecimal newShares = shares.add(boughtShares)
                .setScale(SHARE_SCALE, RoundingMode.HALF_UP);
        BigDecimal newCost = remainingCost.add(grossAmount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        PositionStatus newStatus =
                status == PositionStatus.CONFIRMED
                                && transactionStatus == TransactionStatus.CONFIRMED
                        ? PositionStatus.CONFIRMED
                        : PositionStatus.ESTIMATED;
        return new FundPosition(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                newShares,
                newCost,
                averageCost(newCost, newShares),
                newStatus,
                earliest(holdingStartDate, boughtHoldingStartDate),
                createdAt,
                now);
    }

    public FundPosition applySnapshot(
            BigDecimal snapshotShares,
            BigDecimal snapshotCost,
            PositionStatus snapshotStatus,
            LocalDate snapshotHoldingStartDate,
            Instant now) {
        BigDecimal normalizedShares = snapshotShares.setScale(
                SHARE_SCALE,
                RoundingMode.HALF_UP);
        BigDecimal normalizedCost = snapshotCost.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP);
        return new FundPosition(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                normalizedShares,
                normalizedCost,
                averageCost(normalizedCost, normalizedShares),
                snapshotStatus,
                snapshotHoldingStartDate,
                createdAt,
                now);
    }

    public PositionSaleImpact saleImpact(
            BigDecimal rawSoldShares,
            BigDecimal proceeds) {
        BigDecimal soldShares = rawSoldShares.setScale(
                SHARE_SCALE,
                RoundingMode.HALF_UP);
        if (soldShares.signum() <= 0
                || soldShares.compareTo(shares) > 0) {
            throw new IllegalArgumentException(
                    "Sold shares must be within the current position");
        }
        BigDecimal removedCost = soldShares.compareTo(shares) == 0
                ? remainingCost
                : soldShares.multiply(averageUnitCost)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                        .min(remainingCost);
        BigDecimal remainingShares = shares.subtract(soldShares)
                .setScale(SHARE_SCALE, RoundingMode.HALF_UP);
        BigDecimal newRemainingCost = remainingCost
                .subtract(removedCost)
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedProfit = proceeds
                .subtract(removedCost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new PositionSaleImpact(
                soldShares,
                removedCost,
                realizedProfit,
                remainingShares,
                newRemainingCost);
    }

    public FundPosition applySell(
            PositionSaleImpact impact,
            TransactionStatus transactionStatus,
            Instant now) {
        if (impact.clearsPosition()) {
            throw new IllegalArgumentException(
                    "A cleared position must be deleted");
        }
        PositionStatus newStatus =
                status == PositionStatus.CONFIRMED
                                && transactionStatus
                                        == TransactionStatus.CONFIRMED
                        ? PositionStatus.CONFIRMED
                        : PositionStatus.ESTIMATED;
        return new FundPosition(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                impact.remainingShares(),
                impact.remainingCost(),
                averageCost(
                        impact.remainingCost(),
                        impact.remainingShares()),
                newStatus,
                holdingStartDate,
                createdAt,
                now);
    }

    private static PositionStatus positionStatus(
            TransactionStatus transactionStatus) {
        return transactionStatus == TransactionStatus.CONFIRMED
                ? PositionStatus.CONFIRMED
                : PositionStatus.ESTIMATED;
    }

    private static BigDecimal averageCost(
            BigDecimal cost,
            BigDecimal shares) {
        if (shares.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Position shares must be positive");
        }
        return cost.divide(
                shares,
                UNIT_COST_SCALE,
                RoundingMode.HALF_UP);
    }

    private static LocalDate earliest(
            LocalDate current,
            LocalDate candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return current.isBefore(candidate) ? current : candidate;
    }
}
