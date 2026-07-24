package com.fundkeeper.backend.portfolio.importing.application;

import java.time.OffsetDateTime;
import java.util.List;

import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;
import com.fundkeeper.backend.portfolio.importing.domain.SnapshotMode;

public record SnapshotPreflightResult(
        String batchId,
        ImportBatchStatus status,
        String schemaVersion,
        String importType,
        SnapshotMode snapshotMode,
        OffsetDateTime snapshotAt,
        SnapshotAccountPreview account,
        int totalCount,
        int importableCount,
        int warningCount,
        int errorCount,
        List<SnapshotRowPreview> rows,
        List<ImportIssue> issues) {

    public SnapshotPreflightResult withStatus(
            ImportBatchStatus newStatus) {
        return new SnapshotPreflightResult(
                batchId,
                newStatus,
                schemaVersion,
                importType,
                snapshotMode,
                snapshotAt,
                account,
                totalCount,
                importableCount,
                warningCount,
                errorCount,
                rows,
                issues);
    }
}
