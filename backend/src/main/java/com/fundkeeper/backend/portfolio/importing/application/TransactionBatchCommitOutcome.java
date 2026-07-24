package com.fundkeeper.backend.portfolio.importing.application;

public record TransactionBatchCommitOutcome(
        TransactionBatchCommitResult result,
        boolean idempotentReplay) {
}
