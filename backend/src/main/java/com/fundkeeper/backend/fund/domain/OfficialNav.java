package com.fundkeeper.backend.fund.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record OfficialNav(
        LocalDate navDate,
        BigDecimal unitNav,
        String dataSource) {

    public OfficialNav {
        Objects.requireNonNull(navDate);
        Objects.requireNonNull(unitNav);
        Objects.requireNonNull(dataSource);
    }
}
