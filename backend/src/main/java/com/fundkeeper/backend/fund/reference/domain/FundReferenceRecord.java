package com.fundkeeper.backend.fund.reference.domain;

import java.util.Objects;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundTradingMode;

public record FundReferenceRecord(
        String providerCode,
        String code,
        String name,
        FundCategory category,
        FundTradingMode tradingMode,
        String currency,
        boolean supported,
        Integer confirmationDelayTradingDays) {

    public FundReferenceRecord {
        Objects.requireNonNull(providerCode);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(category);
        Objects.requireNonNull(tradingMode);
        Objects.requireNonNull(currency);
    }

    public FundReferenceRecord(
            String providerCode,
            String code,
            String name,
            FundCategory category,
            String currency,
            boolean supported,
            Integer confirmationDelayTradingDays) {
        this(
                providerCode,
                code,
                name,
                category,
                FundTradingMode.OFF_EXCHANGE,
                currency,
                supported,
                confirmationDelayTradingDays);
    }
}
