package com.fundkeeper.backend.fund.quote.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record MarketQuote(
        String fundCode,
        LocalDate tradeDate,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal changePercent,
        Instant observedAt,
        Instant fetchedAt,
        String dataSource) {

    public MarketQuote {
        Objects.requireNonNull(fundCode);
        Objects.requireNonNull(tradeDate);
        Objects.requireNonNull(price);
        Objects.requireNonNull(previousClose);
        Objects.requireNonNull(changePercent);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(fetchedAt);
        Objects.requireNonNull(dataSource);
        if (!fundCode.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "Fund code must contain six digits");
        }
        if (price.signum() <= 0 || previousClose.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Market quote prices must be positive");
        }
    }
}
