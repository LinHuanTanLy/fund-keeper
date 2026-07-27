package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.math.BigDecimal;

interface AccountSellSummaryProjection {

    Long getAccountId();

    long getConfirmedSellCount();

    BigDecimal getTotalActualReceivedAmount();

    BigDecimal getTotalRemovedCost();

    BigDecimal getTotalRealizedProfit();

    long getOpenSellCount();
}
