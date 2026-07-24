package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;

public record SellTransactionCommand(
        String requestId,
        String accountPublicId,
        String fundCode,
        SellMode sellMode,
        BigDecimal expectedAmount,
        BigDecimal actualReceivedAmount,
        LocalDate submittedDate,
        SubmittedPeriod submittedPeriod,
        BigDecimal confirmedShares,
        LocalDate confirmedDate,
        String note) {
}
