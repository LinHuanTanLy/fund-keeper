package com.fundkeeper.backend.fund.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record PurchaseFeeRule(
        BigDecimal feeRate,
        FeeCalculationMethod calculationMethod,
        String dataSource) {

    public PurchaseFeeRule {
        Objects.requireNonNull(feeRate);
        Objects.requireNonNull(calculationMethod);
        Objects.requireNonNull(dataSource);
    }
}
