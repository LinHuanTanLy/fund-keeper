package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.PositionSaleImpact;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;

public record SellTransactionPlan(
        SellTransactionCommand command,
        FundDefinition fund,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal soldShares,
        LocalDate effectiveDate,
        LocalDate navDate,
        BigDecimal unitNav,
        String navSource,
        PendingReason pendingReason,
        PositionSaleImpact impact) {

    public boolean appliesToPosition() {
        return impact != null;
    }
}
