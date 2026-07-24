package com.fundkeeper.backend.fund.reference.domain;

import java.time.LocalDate;
import java.util.Objects;

public record TradingDayReferenceRecord(
        LocalDate tradeDate,
        boolean open) {

    public TradingDayReferenceRecord {
        Objects.requireNonNull(tradeDate);
    }
}

