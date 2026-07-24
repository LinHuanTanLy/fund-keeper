package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.portfolio.application.TransactionDetails;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;

public record TransactionView(
        String id,
        String requestId,
        String accountId,
        String accountName,
        String fundCode,
        String fundName,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
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
        Instant createdAt) {

    static TransactionView from(TransactionDetails details) {
        var transaction = details.transaction();
        return new TransactionView(
                transaction.publicId(),
                transaction.requestId(),
                details.account().publicId(),
                details.account().name(),
                details.fund().code(),
                details.fund().name(),
                transaction.type(),
                transaction.status(),
                transaction.grossAmount(),
                transaction.feeAmount(),
                transaction.netAmount(),
                transaction.shares(),
                transaction.submittedDate(),
                transaction.submittedPeriod(),
                transaction.effectiveTradeDate(),
                transaction.confirmedDate(),
                transaction.navDate(),
                transaction.unitNav(),
                transaction.navSource(),
                transaction.feeRate(),
                transaction.feeSource(),
                transaction.pendingReason(),
                transaction.note(),
                transaction.createdAt());
    }
}
