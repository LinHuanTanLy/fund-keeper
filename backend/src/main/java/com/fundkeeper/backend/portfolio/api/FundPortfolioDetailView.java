package com.fundkeeper.backend.portfolio.api;

import java.util.List;

import com.fundkeeper.backend.portfolio.application.FundPortfolioDetailDetails;

public record FundPortfolioDetailView(
        FundPortfolioCardView summary,
        List<FundPortfolioAccountView> accounts,
        List<TransactionView> openTransactions,
        TransactionPageView transactions) {

    static FundPortfolioDetailView from(
            FundPortfolioDetailDetails details) {
        return new FundPortfolioDetailView(
                FundPortfolioCardView.from(
                        details.holding().summary()),
                details.holding()
                        .accounts()
                        .stream()
                        .map(FundPortfolioAccountView::from)
                        .toList(),
                details.openTransactions()
                        .stream()
                        .map(TransactionView::from)
                        .toList(),
                TransactionPageView.from(details.transactions()));
    }
}
