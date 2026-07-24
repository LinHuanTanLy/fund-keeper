package com.fundkeeper.backend.fund.reference.application;

import java.util.List;

public record ReferenceDataSyncReport(
        String provider,
        int fundsWritten,
        int tradingDaysWritten,
        int navsWritten,
        List<String> failures) {

    public ReferenceDataSyncReport {
        failures = List.copyOf(failures);
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}

