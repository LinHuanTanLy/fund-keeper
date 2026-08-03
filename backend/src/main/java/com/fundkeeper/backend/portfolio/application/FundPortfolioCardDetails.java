package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;

import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.FundPrimaryTheme;

public record FundPortfolioCardDetails(
        FundDefinition fund,
        FundPrimaryTheme primaryTheme,
        boolean hasCurrentPosition,
        int accountCount,
        BigDecimal totalShares,
        BigDecimal pendingBuyAmount,
        long openTransactionCount,
        PortfolioOverviewDetails metrics) {
}
