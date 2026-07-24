package com.fundkeeper.backend.fund.reference.domain;

import java.util.List;
import java.util.Optional;

public interface FundReferenceDataStore {

    int upsertFunds(
            String provider,
            String sourceLabel,
            List<FundReferenceRecord> funds);

    int upsertTradingDays(
            String sourceLabel,
            List<TradingDayReferenceRecord> tradingDays);

    Optional<String> findProviderFundCode(
            String provider,
            String fundCode);

    List<String> findActiveFundCodes();

    int upsertNavs(
            String provider,
            String sourceLabel,
            List<NavReferenceRecord> navs);
}
