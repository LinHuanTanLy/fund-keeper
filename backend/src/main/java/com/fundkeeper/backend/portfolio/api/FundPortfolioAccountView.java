package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.application.FundPortfolioAccountDetails;
import com.fundkeeper.backend.portfolio.application.ValuationPriceType;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;

public record FundPortfolioAccountView(
        String positionId,
        String accountId,
        String accountName,
        AccountPlatform accountPlatform,
        BigDecimal shares,
        BigDecimal holdingCost,
        BigDecimal currentMarketValue,
        BigDecimal currentHoldingProfit,
        BigDecimal currentHoldingReturnPercent,
        BigDecimal realizedProfit,
        BigDecimal cumulativeProfit,
        BigDecimal todayEstimatedProfit,
        long openSellCount,
        PositionStatus positionStatus,
        ValuationStatus valuationStatus,
        ValuationPriceType priceType,
        BigDecimal unitNav,
        BigDecimal estimatedChangePercent,
        LocalDate baseNavDate,
        BigDecimal baseNav,
        LocalDate dataDate,
        Instant observedAt,
        String dataSource,
        LocalDate holdingStartDate,
        Long holdingDays) {

    static FundPortfolioAccountView from(
            FundPortfolioAccountDetails details) {
        var valuation = details.valuation();
        var position = valuation.positionDetails().position();
        var account = valuation.positionDetails().account();
        var metrics = details.metrics();
        return new FundPortfolioAccountView(
                position.publicId(),
                account.publicId(),
                account.name(),
                account.platform(),
                position.shares(),
                metrics.totalHoldingCost(),
                metrics.currentMarketValue(),
                metrics.currentHoldingProfit(),
                metrics.currentHoldingReturnPercent(),
                metrics.realizedProfit(),
                metrics.cumulativeProfit(),
                metrics.todayEstimatedProfit(),
                metrics.openSellCount(),
                position.status(),
                valuation.valuationStatus(),
                valuation.priceType(),
                valuation.unitNav(),
                valuation.estimatedChangePercent(),
                valuation.baseNavDate(),
                valuation.baseNav(),
                valuation.dataDate(),
                valuation.observedAt(),
                valuation.dataSource(),
                metrics.holdingStartDate(),
                metrics.holdingDays());
    }
}
