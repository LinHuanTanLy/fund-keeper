package com.fundkeeper.backend.portfolio.application;

import java.util.List;

public record FundPortfolioDetailDetails(
        FundPortfolioHoldingDetails holding,
        List<TransactionDetails> openTransactions,
        TransactionPageDetails transactions) {
}
