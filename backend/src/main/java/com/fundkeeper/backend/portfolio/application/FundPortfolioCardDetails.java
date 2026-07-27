package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;

import com.fundkeeper.backend.fund.domain.FundDefinition;

public record FundPortfolioCardDetails(
        FundDefinition fund,
        boolean hasCurrentPosition,
        int accountCount,
        BigDecimal totalShares,
        BigDecimal pendingBuyAmount,
        long openTransactionCount,
        PortfolioOverviewDetails metrics) {
}
