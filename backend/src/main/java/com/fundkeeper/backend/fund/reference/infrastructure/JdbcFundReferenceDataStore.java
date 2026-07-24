package com.fundkeeper.backend.fund.reference.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataStore;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.NavReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.TradingDayReferenceRecord;

@Repository
public class JdbcFundReferenceDataStore implements FundReferenceDataStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcFundReferenceDataStore(
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int upsertFunds(
            String provider,
            String sourceLabel,
            List<FundReferenceRecord> funds) {
        Instant now = clock.instant();
        for (FundReferenceRecord fund : funds) {
            upsertFund(provider, sourceLabel, fund, now);
        }
        return funds.size();
    }

    @Override
    @Transactional
    public int upsertTradingDays(
            String sourceLabel,
            List<TradingDayReferenceRecord> tradingDays) {
        Instant now = clock.instant();
        for (TradingDayReferenceRecord tradingDay : tradingDays) {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE fund_trading_days
                       SET is_open = ?,
                           data_source = ?,
                           updated_at = ?
                     WHERE market = 'CN_FUND'
                       AND trade_date = ?
                    """,
                    tradingDay.open(),
                    sourceLabel,
                    Timestamp.from(now),
                    tradingDay.tradeDate());
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                        INSERT INTO fund_trading_days
                            (market, trade_date, is_open, data_source, updated_at)
                        VALUES ('CN_FUND', ?, ?, ?, ?)
                        """,
                        tradingDay.tradeDate(),
                        tradingDay.open(),
                        sourceLabel,
                        Timestamp.from(now));
            }
        }
        return tradingDays.size();
    }

    @Override
    public Optional<String> findProviderFundCode(
            String provider,
            String fundCode) {
        return jdbcTemplate.query(
                        """
                        SELECT identifier.provider_code
                          FROM fund_provider_identifiers identifier
                          JOIN funds fund ON fund.id = identifier.fund_id
                         WHERE identifier.provider = ?
                           AND fund.code = ?
                        """,
                        (resultSet, rowNumber) ->
                                resultSet.getString("provider_code"),
                        provider,
                        fundCode)
                .stream()
                .findFirst();
    }

    @Override
    public List<String> findActiveFundCodes() {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT fund.code
                  FROM funds fund
                 WHERE EXISTS (
                           SELECT 1
                             FROM fund_positions position
                            WHERE position.fund_id = fund.id
                       )
                    OR EXISTS (
                           SELECT 1
                             FROM fund_transactions transaction_record
                            WHERE transaction_record.fund_id = fund.id
                              AND transaction_record.status = 'PENDING'
                       )
                 ORDER BY fund.code
                """,
                String.class);
    }

    @Override
    @Transactional
    public int upsertNavs(
            String provider,
            String sourceLabel,
            List<NavReferenceRecord> navs) {
        Instant now = clock.instant();
        for (NavReferenceRecord nav : navs) {
            long fundId = findFundId(provider, nav.providerFundCode());
            int updated = jdbcTemplate.update(
                    """
                    UPDATE fund_navs
                       SET unit_nav = ?,
                           data_source = ?,
                           published_at = ?
                     WHERE fund_id = ?
                       AND nav_date = ?
                    """,
                    nav.unitNav(),
                    sourceLabel,
                    timestamp(nav.publishedAt()),
                    fundId,
                    nav.navDate());
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                        INSERT INTO fund_navs
                            (fund_id, nav_date, unit_nav, data_source,
                             published_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        fundId,
                        nav.navDate(),
                        nav.unitNav(),
                        sourceLabel,
                        timestamp(nav.publishedAt()),
                        Timestamp.from(now));
            }
        }
        return navs.size();
    }

    private void upsertFund(
            String provider,
            String sourceLabel,
            FundReferenceRecord fund,
            Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE funds
                   SET name = ?,
                       category = ?,
                       currency = ?,
                       supported = ?,
                       confirmation_delay_trading_days = ?,
                       data_source = ?,
                       updated_at = ?
                 WHERE code = ?
                """,
                fund.name(),
                fund.category().name(),
                fund.currency(),
                fund.supported(),
                fund.confirmationDelayTradingDays(),
                sourceLabel,
                Timestamp.from(now),
                fund.code());
        if (updated == 0) {
            try {
                jdbcTemplate.update(
                        """
                        INSERT INTO funds
                            (code, name, category, currency, supported,
                             confirmation_delay_trading_days, data_source,
                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        fund.code(),
                        fund.name(),
                        fund.category().name(),
                        fund.currency(),
                        fund.supported(),
                        fund.confirmationDelayTradingDays(),
                        sourceLabel,
                        Timestamp.from(now),
                        Timestamp.from(now));
            } catch (DuplicateKeyException exception) {
                upsertFund(provider, sourceLabel, fund, now);
                return;
            }
        }
        long fundId = jdbcTemplate.queryForObject(
                "SELECT id FROM funds WHERE code = ?",
                Long.class,
                fund.code());
        upsertIdentifier(
                fundId,
                provider,
                fund.providerCode(),
                now);
    }

    private void upsertIdentifier(
            long fundId,
            String provider,
            String providerCode,
            Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE fund_provider_identifiers
                   SET provider_code = ?,
                       updated_at = ?
                 WHERE fund_id = ?
                   AND provider = ?
                """,
                providerCode,
                Timestamp.from(now),
                fundId,
                provider);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO fund_provider_identifiers
                        (fund_id, provider, provider_code, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    fundId,
                    provider,
                    providerCode,
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
    }

    private long findFundId(String provider, String providerCode) {
        List<Long> fundIds = jdbcTemplate.query(
                """
                SELECT fund_id
                  FROM fund_provider_identifiers
                 WHERE provider = ?
                   AND provider_code = ?
                """,
                (resultSet, rowNumber) ->
                        resultSet.getLong("fund_id"),
                provider,
                providerCode);
        return fundIds.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No local fund mapping for provider code: "
                                + providerCode));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
