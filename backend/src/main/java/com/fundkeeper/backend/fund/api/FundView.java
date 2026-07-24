package com.fundkeeper.backend.fund.api;

import java.time.Instant;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundDefinition;

public record FundView(
        String code,
        String name,
        FundCategory category,
        String currency,
        Integer confirmationDelayTradingDays,
        String dataSource,
        Instant updatedAt) {

    static FundView from(FundDefinition fund) {
        return new FundView(
                fund.code(),
                fund.name(),
                fund.category(),
                fund.currency(),
                fund.confirmationDelayTradingDays(),
                fund.dataSource(),
                fund.updatedAt());
    }
}
