package com.fundkeeper.backend.portfolio.importing.domain;

import java.time.Instant;
import java.util.Optional;

public interface ImportBatchRepository {

    Optional<ImportBatch> findByUserIdAndBatchId(
            long userId,
            String batchId);

    Optional<ImportBatch> findByUserIdAndBatchIdForUpdate(
            long userId,
            String batchId);

    ImportBatch savePreflight(ImportBatch batch);

    ImportBatch markCommitted(
            long id,
            long accountId,
            String commitResultJson,
            Instant committedAt);

    void markCommitFailed(
            long userId,
            String batchId,
            Instant failedAt);
}
