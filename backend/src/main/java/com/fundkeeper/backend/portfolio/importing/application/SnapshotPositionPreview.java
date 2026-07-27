package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fundkeeper.backend.portfolio.domain.PositionStatus;

public record SnapshotPositionPreview(
        BigDecimal shares,
        BigDecimal costAmount,
        PositionStatus status,
        LocalDate holdingStartDate) {
}
