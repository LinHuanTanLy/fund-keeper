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
        TransactionStatus status,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        BigDecimal shares,
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
        return new FundTransaction(
                null,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                TransactionType.BUY,
                status,
                grossAmount,
                feeAmount,
                netAmount,
                shares,
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
                status,
                costAmount,
                null,
                currentAmount,
                shares,
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
                now,
                now);
    }

    public boolean appliesToPosition() {
        return status == TransactionStatus.ESTIMATED
                || status == TransactionStatus.CONFIRMED;
    }
}
