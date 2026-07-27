package com.fundkeeper.backend.portfolio.application;

public record FundPortfolioAccountDetails(
        PositionValuationDetails valuation,
        PortfolioOverviewDetails metrics) {
}
