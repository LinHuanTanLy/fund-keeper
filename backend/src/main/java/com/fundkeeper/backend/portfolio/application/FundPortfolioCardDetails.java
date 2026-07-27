package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;

import com.fundkeeper.backend.fund.domain.FundDefinition;

public record FundPortfolioCardDetails(
        FundDefinition fund,
        int accountCount,
        BigDecimal totalShares,
        PortfolioOverviewDetails metrics) {
}
