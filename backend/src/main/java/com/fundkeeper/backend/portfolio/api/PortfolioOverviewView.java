package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.application.PortfolioOverviewDetails;
import com.fundkeeper.backend.portfolio.application.ValuationPriceType;

public record PortfolioOverviewView(
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

    static PortfolioOverviewView from(
            PortfolioOverviewDetails details) {
        return new PortfolioOverviewView(
                details.positionCount(),
                details.valuedPositionCount(),
                details.missingValuationCount(),
                details.totalHoldingCost(),
                details.valuedHoldingCost(),
                details.currentMarketValue(),
                details.currentHoldingProfit(),
                details.realizedProfit(),
                details.cumulativeProfit(),
                details.returnCostBasis(),
                details.cumulativeReturnPercent(),
                details.todayEstimatedProfit(),
                details.todayEstimateComplete(),
                details.confirmedSellCount(),
                details.openSellCount(),
                details.containsEstimatedData(),
                details.valuationComplete(),
                details.valuationStatus(),
                details.priceType(),
                details.dataDate(),
                details.observedAt(),
                details.holdingStartDate(),
                details.holdingDays());
    }
}
