package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PositionSnapshotDocument(
        String schemaVersion,
        String importType,
        String batchId,
        String snapshotMode,
        SnapshotAccountDocument account,
        OffsetDateTime snapshotAt,
        List<PositionSnapshotRowDocument> positions) {

    public record SnapshotAccountDocument(
            String name,
            String platform) {
    }

    public record PositionSnapshotRowDocument(
            String fundCode,
            BigDecimal costAmount,
            BigDecimal currentAmount,
            LocalDate holdingStartDate,
            BigDecimal confirmedShares) {
    }
}
