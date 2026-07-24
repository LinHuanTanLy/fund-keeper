package com.fundkeeper.backend.portfolio.importing.application;

import java.util.List;

import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;

public record TransactionBatchPreflightResult(
        String batchId,
        ImportBatchStatus status,
        String schemaVersion,
        String importType,
        SnapshotAccountPreview account,
        int totalCount,
        int importableCount,
        int warningCount,
        int errorCount,
        List<TransactionBatchRowPreview> rows,
        List<ImportIssue> issues) {

    public TransactionBatchPreflightResult withStatus(
            ImportBatchStatus newStatus) {
        return new TransactionBatchPreflightResult(
                batchId,
                newStatus,
                schemaVersion,
                importType,
                account,
                totalCount,
                importableCount,
                warningCount,
                errorCount,
                rows,
                issues);
    }
}
