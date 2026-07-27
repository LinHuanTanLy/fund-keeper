package com.fundkeeper.backend.portfolio.domain;

import java.math.BigDecimal;

public record SellTransactionSummary(
        long confirmedSellCount,
        BigDecimal totalActualReceivedAmount,
        BigDecimal totalRemovedCost,
        BigDecimal totalRealizedProfit,
        long openSellCount) {
}
