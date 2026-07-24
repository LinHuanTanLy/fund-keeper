package com.fundkeeper.backend.portfolio.importing.application;

import java.time.Instant;
import java.util.List;

import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;

public record TransactionBatchCommitResult(
        String batchId,
        ImportBatchStatus status,
        String accountId,
        boolean accountCreated,
        int importedCount,
        List<CommittedTransactionRow> rows,
        Instant committedAt) {

    public record CommittedTransactionRow(
            int row,
            String rowId,
            String fundCode,
            String type,
            String transactionId,
            TransactionStatus transactionStatus) {
    }
}
