package com.fundkeeper.backend.portfolio.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundPortfolioDetailService {

    private final PortfolioOverviewService overviewService;
    private final PortfolioService portfolioService;

    public FundPortfolioDetailService(
            PortfolioOverviewService overviewService,
            PortfolioService portfolioService) {
        this.overviewService = overviewService;
        this.portfolioService = portfolioService;
    }

    @Transactional(readOnly = true)
    public FundPortfolioDetailDetails get(
            String userPublicId,
            String fundCode,
            int page,
            int size) {
        FundPortfolioHoldingDetails holding =
                overviewService.getFund(userPublicId, fundCode);
        return new FundPortfolioDetailDetails(
                holding,
                portfolioService.listOpenTransactions(
                        userPublicId,
                        fundCode),
                portfolioService.listTransactions(
                        userPublicId,
                        null,
                        fundCode,
                        null,
                        null,
                        page,
                        size));
    }
}
