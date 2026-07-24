package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;

public record BuyTransactionPlan(
        BuyTransactionCommand command,
        FundDefinition fund,
        TransactionStatus status,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        BigDecimal shares,
        LocalDate effectiveDate,
        LocalDate holdingStartDate,
        LocalDate navDate,
        BigDecimal unitNav,
        String navSource,
        BigDecimal feeRate,
        String feeSource,
        PendingReason pendingReason) {
}
