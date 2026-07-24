package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.portfolio.application.PositionDetails;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;

public record PositionView(
        String id,
        String accountId,
        String accountName,
        String fundCode,
        String fundName,
        BigDecimal shares,
        BigDecimal remainingCost,
        BigDecimal averageUnitCost,
        PositionStatus status,
        LocalDate holdingStartDate,
        Instant updatedAt) {

    static PositionView from(PositionDetails details) {
        var position = details.position();
        return new PositionView(
                position.publicId(),
                details.account().publicId(),
                details.account().name(),
                details.fund().code(),
                details.fund().name(),
                position.shares(),
                position.remainingCost(),
                position.averageUnitCost(),
                position.status(),
                position.holdingStartDate(),
                position.updatedAt());
    }
}
