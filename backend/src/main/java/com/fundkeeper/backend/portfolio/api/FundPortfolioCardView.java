package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.application.FundPortfolioCardDetails;
import com.fundkeeper.backend.portfolio.application.ValuationPriceType;

public record FundPortfolioCardView(
        String fundCode,
        String fundName,
        int accountCount,
        BigDecimal totalShares,
        BigDecimal holdingCost,
        BigDecimal currentMarketValue,
        BigDecimal currentHoldingProfit,
        BigDecimal currentHoldingReturnPercent,
        BigDecimal realizedProfit,
        BigDecimal cumulativeProfit,
        BigDecimal todayEstimatedProfit,
        boolean todayEstimateComplete,
        long openSellCount,
        boolean containsEstimatedData,
        boolean valuationComplete,
        ValuationStatus valuationStatus,
        ValuationPriceType priceType,
        LocalDate dataDate,
        Instant observedAt,
        LocalDate holdingStartDate,
        Long holdingDays) {

    static FundPortfolioCardView from(
            FundPortfolioCardDetails details) {
        var metrics = details.metrics();
        return new FundPortfolioCardView(
                details.fund().code(),
                details.fund().name(),
                details.accountCount(),
                details.totalShares(),
                metrics.totalHoldingCost(),
                metrics.currentMarketValue(),
                metrics.currentHoldingProfit(),
                metrics.currentHoldingReturnPercent(),
                metrics.realizedProfit(),
                metrics.cumulativeProfit(),
                metrics.todayEstimatedProfit(),
                metrics.todayEstimateComplete(),
                metrics.openSellCount(),
                metrics.containsEstimatedData(),
                metrics.valuationComplete(),
                metrics.valuationStatus(),
                metrics.priceType(),
                metrics.dataDate(),
                metrics.observedAt(),
                metrics.holdingStartDate(),
                metrics.holdingDays());
    }
}
