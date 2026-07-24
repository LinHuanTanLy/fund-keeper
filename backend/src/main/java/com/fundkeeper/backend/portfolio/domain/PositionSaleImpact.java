package com.fundkeeper.backend.portfolio.domain;

import java.math.BigDecimal;

public record PositionSaleImpact(
        BigDecimal soldShares,
        BigDecimal removedCost,
        BigDecimal realizedProfit,
        BigDecimal remainingShares,
        BigDecimal remainingCost) {

    public boolean clearsPosition() {
        return remainingShares.signum() == 0;
    }
}
