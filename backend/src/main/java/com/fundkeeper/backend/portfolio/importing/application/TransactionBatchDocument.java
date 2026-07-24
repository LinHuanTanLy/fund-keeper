package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionBatchDocument(
        String schemaVersion,
        String importType,
        String batchId,
        TransactionAccountDocument account,
        List<TransactionRowDocument> transactions) {

    public record TransactionAccountDocument(
            String name,
            String platform) {
    }

    public record TransactionRowDocument(
            String rowId,
            String fundCode,
            String type,
            BigDecimal amount,
            String sellMode,
            BigDecimal expectedAmount,
            BigDecimal actualReceivedAmount,
            LocalDate submittedDate,
            String submittedPeriod,
            BigDecimal confirmedShares,
            LocalDate confirmedDate,
            String note) {
    }
}
