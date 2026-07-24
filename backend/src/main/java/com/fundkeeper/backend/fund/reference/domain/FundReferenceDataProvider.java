package com.fundkeeper.backend.fund.reference.domain;

import java.time.LocalDate;
import java.util.List;

public interface FundReferenceDataProvider {

    String providerName();

    String sourceLabel();

    default String fundSourceLabel() {
        return sourceLabel();
    }

    default String calendarSourceLabel() {
        return sourceLabel();
    }

    default String navSourceLabel() {
        return sourceLabel();
    }

    List<FundReferenceRecord> fetchFunds();

    List<TradingDayReferenceRecord> fetchTradingDays(
            LocalDate startDate,
            LocalDate endDate);

    List<NavReferenceRecord> fetchNavs(
            String providerFundCode,
            LocalDate startDate,
            LocalDate endDate);
}
