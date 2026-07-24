package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

public record PositionValuationDetails(
        PositionDetails positionDetails,
        ValuationStatus valuationStatus,
        ValuationPriceType priceType,
        BigDecimal unitNav,
        BigDecimal estimatedChangePercent,
        BigDecimal marketValue,
        BigDecimal profit,
        BigDecimal returnPercent,
        LocalDate dataDate,
        Instant observedAt,
        String dataSource) {
}
