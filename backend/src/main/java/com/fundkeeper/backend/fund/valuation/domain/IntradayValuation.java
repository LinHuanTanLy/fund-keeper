package com.fundkeeper.backend.fund.valuation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record IntradayValuation(
        String fundCode,
        LocalDate valuationDate,
        BigDecimal estimatedNav,
        BigDecimal estimatedChangePercent,
        LocalDate baseNavDate,
        BigDecimal baseNav,
        Instant fetchedAt,
        String dataSource) {

    public IntradayValuation {
        Objects.requireNonNull(fundCode);
        Objects.requireNonNull(valuationDate);
        Objects.requireNonNull(estimatedNav);
        Objects.requireNonNull(estimatedChangePercent);
        Objects.requireNonNull(baseNavDate);
        Objects.requireNonNull(baseNav);
        Objects.requireNonNull(fetchedAt);
        Objects.requireNonNull(dataSource);
        if (!fundCode.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "Fund code must contain six digits");
        }
        if (estimatedNav.signum() <= 0 || baseNav.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Valuation NAV values must be positive");
        }
    }
}
