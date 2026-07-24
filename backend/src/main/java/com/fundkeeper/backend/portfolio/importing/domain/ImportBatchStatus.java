package com.fundkeeper.backend.portfolio.importing.domain;

public enum ImportBatchStatus {
    PREFLIGHT_FAILED,
    READY_TO_COMMIT,
    COMMITTED,
    COMMIT_FAILED
}
