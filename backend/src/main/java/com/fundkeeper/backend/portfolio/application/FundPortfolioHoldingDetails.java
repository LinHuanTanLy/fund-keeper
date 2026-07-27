package com.fundkeeper.backend.portfolio.application;

import java.util.List;

public record FundPortfolioHoldingDetails(
        FundPortfolioCardDetails summary,
        List<FundPortfolioAccountDetails> accounts) {
}
