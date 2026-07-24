package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;

public record BuyTransactionCommand(
        String requestId,
        String accountPublicId,
        String fundCode,
        BigDecimal amount,
        LocalDate submittedDate,
        SubmittedPeriod submittedPeriod,
        BigDecimal confirmedShares,
        LocalDate confirmedDate,
        String note) {
}
