package com.fundkeeper.backend.portfolio.importing.application;

public record SnapshotCommitOutcome(
        SnapshotCommitResult result,
        boolean idempotentReplay) {
}
