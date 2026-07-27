package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

public record PortfolioOverviewDetails(
        int positionCount,
        int valuedPositionCount,
        int missingValuationCount,
        BigDecimal totalHoldingCost,
        BigDecimal valuedHoldingCost,
        BigDecimal currentMarketValue,
        BigDecimal currentHoldingProfit,
        BigDecimal realizedProfit,
        BigDecimal cumulativeProfit,
        BigDecimal returnCostBasis,
        BigDecimal cumulativeReturnPercent,
        BigDecimal todayEstimatedProfit,
        boolean todayEstimateComplete,
        long confirmedSellCount,
        long openSellCount,
        boolean containsEstimatedData,
        boolean valuationComplete,
        ValuationStatus valuationStatus,
        ValuationPriceType priceType,
        LocalDate dataDate,
        Instant observedAt,
        LocalDate holdingStartDate,
        Long holdingDays) {
}
