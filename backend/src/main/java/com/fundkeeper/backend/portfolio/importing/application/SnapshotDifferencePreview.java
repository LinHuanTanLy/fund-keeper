package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;

public record SnapshotDifferencePreview(
        BigDecimal sharesDelta,
        BigDecimal costAmountDelta,
        boolean statusChanged,
        boolean holdingStartDateChanged) {
}
