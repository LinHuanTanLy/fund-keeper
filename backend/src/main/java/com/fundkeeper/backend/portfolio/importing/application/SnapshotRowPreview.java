package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.importing.domain.SnapshotAction;

public record SnapshotRowPreview(
        int row,
        String fundCode,
        String fundName,
        SnapshotAction action,
        PositionStatus positionStatus,
        BigDecimal costAmount,
        BigDecimal currentAmount,
        BigDecimal calculatedShares,
        LocalDate holdingStartDate,
        LocalDate navDate,
        BigDecimal unitNav,
        String navSource,
        List<ImportIssue> issues,
        SnapshotReviewStatus reviewStatus,
        SnapshotPositionPreview currentPosition,
        SnapshotPositionPreview targetPosition,
        SnapshotDifferencePreview difference) {

    public SnapshotRowPreview {
        issues = issues == null ? List.of() : List.copyOf(issues);
        reviewStatus = reviewStatus == null
                ? SnapshotReviewStatus.NONE
                : reviewStatus;
    }
}
