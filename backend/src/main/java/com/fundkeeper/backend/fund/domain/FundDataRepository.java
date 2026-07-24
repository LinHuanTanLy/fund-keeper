package com.fundkeeper.backend.fund.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FundDataRepository {

    Optional<FundDefinition> findFundByCode(String code);

    Optional<FundDefinition> findFundById(long id);

    Optional<Boolean> findTradingDayOpenFlag(LocalDate date);

    Optional<OfficialNav> findOfficialNav(long fundId, LocalDate navDate);

    Optional<OfficialNav> findLatestOfficialNav(long fundId);

    Optional<OfficialNav> findLatestOfficialNavOnOrBefore(
            long fundId,
            LocalDate navDate);

    Optional<PurchaseFeeRule> findPurchaseFeeRule(
            long fundId,
            BigDecimal amount,
            LocalDate effectiveDate);
}
