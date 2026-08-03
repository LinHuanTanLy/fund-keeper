package com.fundkeeper.backend.fund.domain;

import java.time.Instant;
import java.util.Objects;

public record FundDefinition(
        Long id,
        String code,
        String name,
        FundCategory category,
        FundTradingMode tradingMode,
        String currency,
        boolean supported,
        Integer confirmationDelayTradingDays,
        String dataSource,
        Instant createdAt,
        Instant updatedAt) {

    public FundDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(category);
        Objects.requireNonNull(tradingMode);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(dataSource);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public FundDefinition(
            Long id,
            String code,
            String name,
            FundCategory category,
            String currency,
            boolean supported,
            Integer confirmationDelayTradingDays,
            String dataSource,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                code,
                name,
                category,
                FundTradingMode.OFF_EXCHANGE,
                currency,
                supported,
                confirmationDelayTradingDays,
                dataSource,
                createdAt,
                updatedAt);
    }
}
