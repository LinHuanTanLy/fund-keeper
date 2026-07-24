package com.fundkeeper.backend.fund.reference.domain;

import java.util.Objects;

import com.fundkeeper.backend.fund.domain.FundCategory;

public record FundReferenceRecord(
        String providerCode,
        String code,
        String name,
        FundCategory category,
        String currency,
        boolean supported,
        Integer confirmationDelayTradingDays) {

    public FundReferenceRecord {
        Objects.requireNonNull(providerCode);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(category);
        Objects.requireNonNull(currency);
    }
}

