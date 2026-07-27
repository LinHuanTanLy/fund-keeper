package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.math.BigDecimal;

interface SellSummaryProjection {

    long getConfirmedSellCount();

    BigDecimal getTotalActualReceivedAmount();

    BigDecimal getTotalRemovedCost();

    BigDecimal getTotalRealizedProfit();

    long getOpenSellCount();
}
