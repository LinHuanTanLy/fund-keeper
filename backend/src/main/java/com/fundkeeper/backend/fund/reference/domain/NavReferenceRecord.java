package com.fundkeeper.backend.fund.reference.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record NavReferenceRecord(
        String providerFundCode,
        LocalDate navDate,
        BigDecimal unitNav,
        Instant publishedAt) {

    public NavReferenceRecord {
        Objects.requireNonNull(providerFundCode);
        Objects.requireNonNull(navDate);
        Objects.requireNonNull(unitNav);
    }
}

