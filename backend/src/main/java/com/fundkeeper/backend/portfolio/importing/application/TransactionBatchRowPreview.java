package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.importing.domain.TransactionImportAction;

public record TransactionBatchRowPreview(
        int row,
        String rowId,
        String fundCode,
        String fundName,
        String type,
        TransactionImportAction action,
        TransactionStatus transactionStatus,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        BigDecimal calculatedShares,
        LocalDate effectiveTradeDate,
        LocalDate holdingStartDate,
        LocalDate navDate,
        BigDecimal unitNav,
        String navSource,
        BigDecimal feeRate,
        String feeSource,
        PendingReason pendingReason,
        List<ImportIssue> issues) {
}
