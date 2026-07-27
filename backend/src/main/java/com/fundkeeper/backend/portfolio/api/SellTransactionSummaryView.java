package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;

import com.fundkeeper.backend.portfolio.domain.SellTransactionSummary;

public record SellTransactionSummaryView(
        long confirmedSellCount,
        BigDecimal totalActualReceivedAmount,
        BigDecimal totalRemovedCost,
        BigDecimal totalRealizedProfit,
        long openSellCount) {

    static SellTransactionSummaryView from(
            SellTransactionSummary summary) {
        return new SellTransactionSummaryView(
                summary.confirmedSellCount(),
                summary.totalActualReceivedAmount(),
                summary.totalRemovedCost(),
                summary.totalRealizedProfit(),
                summary.openSellCount());
    }
}
