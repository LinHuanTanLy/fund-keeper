package com.fundkeeper.backend.portfolio.importing.application;

import java.time.Instant;
import java.util.List;

import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;
import com.fundkeeper.backend.portfolio.importing.domain.SnapshotAction;

public record SnapshotCommitResult(
        String batchId,
        ImportBatchStatus status,
        String accountId,
        boolean accountCreated,
        int appliedCount,
        int clearedCount,
        List<CommittedSnapshotRow> rows,
        Instant committedAt) {

    public record CommittedSnapshotRow(
            int row,
            String fundCode,
            SnapshotAction action,
            String transactionId,
            String positionId) {
    }
}
