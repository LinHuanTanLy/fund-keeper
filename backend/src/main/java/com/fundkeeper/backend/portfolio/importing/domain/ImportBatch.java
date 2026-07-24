package com.fundkeeper.backend.portfolio.importing.domain;

import java.time.Instant;

public record ImportBatch(
        Long id,
        String publicId,
        long userId,
        Long accountId,
        String batchId,
        String schemaVersion,
        String importType,
        String snapshotMode,
        Instant snapshotAt,
        String contentHash,
        String planHash,
        String requestJson,
        ImportBatchStatus status,
        int totalCount,
        int importableCount,
        int warningCount,
        int errorCount,
        String preflightJson,
        String commitResultJson,
        Instant committedAt,
        Instant createdAt,
        Instant updatedAt) {
}
