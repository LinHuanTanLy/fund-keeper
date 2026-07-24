package com.fundkeeper.backend.fund.reference.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataProvider;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataStore;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.NavReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.TradingDayReferenceRecord;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReferenceDataSyncIntegrationTests {

    private static final Instant NOW =
            Instant.parse("2031-01-10T08:00:00Z");

    @Autowired
    private FundReferenceDataStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void repeatedSyncIsIdempotentAndOneNavFailureKeepsOtherData() {
        ReferenceDataProperties properties = new ReferenceDataProperties(
                "none",
                false,
                List.of("990001", "990002"),
                10,
                30,
                400,
                "https://unused.invalid",
                "",
                "https://unused.invalid",
                "https://unused.invalid/sse",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        ReferenceDataSyncService service = new ReferenceDataSyncService(
                new FakeProvider(),
                store,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        ReferenceDataSyncReport first = service.syncConfiguredData();
        ReferenceDataSyncReport second = service.syncConfiguredData();

        assertThat(first.fundsWritten()).isEqualTo(2);
        assertThat(first.tradingDaysWritten()).isEqualTo(2);
        assertThat(first.navsWritten()).isEqualTo(1);
        assertThat(first.failures()).singleElement()
                .asString()
                .contains("nav[990002]")
                .contains("simulated upstream failure");
        assertThat(second.failures()).hasSize(1);

        assertThat(count(
                "SELECT COUNT(*) FROM funds WHERE code IN ('990001', '990002')"))
                .isEqualTo(2);
        assertThat(count(
                """
                SELECT COUNT(*)
                  FROM fund_provider_identifiers identifier
                  JOIN funds fund ON fund.id = identifier.fund_id
                 WHERE fund.code IN ('990001', '990002')
                """))
                .isEqualTo(2);
        assertThat(count(
                """
                SELECT COUNT(*)
                  FROM fund_trading_days
                 WHERE trade_date IN ('2031-01-09', '2031-01-10')
                """))
                .isEqualTo(2);
        assertThat(count(
                """
                SELECT COUNT(*)
                  FROM fund_navs nav
                  JOIN funds fund ON fund.id = nav.fund_id
                 WHERE fund.code = '990001'
                   AND nav.nav_date = '2031-01-09'
                """))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT nav.data_source
                  FROM fund_navs nav
                  JOIN funds fund ON fund.id = nav.fund_id
                 WHERE fund.code = '990001'
                   AND nav.nav_date = '2031-01-09'
                """,
                String.class)).isEqualTo("fake-test-source");
    }

    @Test
    void dailySyncOnlyRefreshesConfiguredNavs() {
        ReferenceDataProperties properties = properties(List.of("990001"));
        CountingProvider provider = new CountingProvider();
        ReferenceDataSyncService service = new ReferenceDataSyncService(
                provider,
                store,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        store.upsertFunds(
                provider.providerName(),
                provider.sourceLabel(),
                provider.fetchFunds());
        provider.resetCounts();

        ReferenceDataSyncReport report = service.syncDailyNavs();

        assertThat(report.fundsWritten()).isZero();
        assertThat(report.tradingDaysWritten()).isZero();
        assertThat(report.navsWritten()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
        assertThat(provider.fundFetches).isZero();
        assertThat(provider.calendarFetches).isZero();
        assertThat(provider.navFetches).isEqualTo(1);
    }

    @Test
    void activeFundCodesComeFromPositionsAndPendingTransactions() {
        CountingProvider provider = new CountingProvider();
        store.upsertFunds(
                provider.providerName(),
                provider.sourceLabel(),
                provider.fetchFunds());
        long userId = insertUser();
        long accountId = insertAccount(userId);
        insertPosition(userId, accountId, fundId("990001"));
        insertPendingTransaction(userId, accountId, fundId("990002"));

        assertThat(store.findActiveFundCodes())
                .containsExactly("990001", "990002");

        ReferenceDataSyncService service = new ReferenceDataSyncService(
                provider,
                store,
                properties(List.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        provider.resetCounts();

        ReferenceDataSyncReport report = service.syncDailyNavs();

        assertThat(report.navsWritten()).isEqualTo(2);
        assertThat(provider.navFetches).isEqualTo(2);
        assertThat(provider.fundFetches).isZero();
        assertThat(provider.calendarFetches).isZero();
    }

    private ReferenceDataProperties properties(List<String> fundCodes) {
        return new ReferenceDataProperties(
                "none",
                false,
                fundCodes,
                10,
                30,
                400,
                "https://unused.invalid",
                "",
                "https://unused.invalid",
                "https://unused.invalid/sse",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private long insertUser() {
        jdbcTemplate.update(
                """
                INSERT INTO users
                    (public_id, email_normalized, password_hash, status,
                     token_version, created_at, updated_at, version)
                VALUES ('sync-user', 'sync-user@example.com', 'unused',
                        'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE public_id = 'sync-user'",
                Long.class);
    }

    private long insertAccount(long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO fund_accounts
                    (public_id, user_id, name, name_normalized,
                     active_name_normalized, platform, status, created_at,
                     updated_at, version)
                VALUES ('sync-account', ?, '同步账户', '同步账户', '同步账户',
                        'OTHER', 'ACTIVE', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0)
                """,
                userId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM fund_accounts WHERE public_id = 'sync-account'",
                Long.class);
    }

    private void insertPosition(
            long userId,
            long accountId,
            long fundId) {
        jdbcTemplate.update(
                """
                INSERT INTO fund_positions
                    (public_id, user_id, account_id, fund_id, shares,
                     remaining_cost, average_unit_cost, status,
                     holding_start_date, created_at, updated_at, version)
                VALUES ('sync-position', ?, ?, ?, 100.00000000, 123.4000,
                        1.23400000, 'CONFIRMED', '2031-01-01',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId,
                accountId,
                fundId);
    }

    private void insertPendingTransaction(
            long userId,
            long accountId,
            long fundId) {
        jdbcTemplate.update(
                """
                INSERT INTO fund_transactions
                    (public_id, user_id, account_id, fund_id, request_id,
                     request_fingerprint, transaction_type, status,
                     gross_amount, submitted_date, submitted_period,
                     effective_trade_date, pending_reason, created_at,
                     updated_at, version)
                VALUES ('sync-transaction', ?, ?, ?, 'sync-request',
                        'sync-fingerprint', 'BUY', 'PENDING', 100.0000,
                        '2031-01-09', 'BEFORE_15', '2031-01-09',
                        'OFFICIAL_NAV_UNAVAILABLE', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0)
                """,
                userId,
                accountId,
                fundId);
    }

    private long fundId(String fundCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM funds WHERE code = ?",
                Long.class,
                fundCode);
    }

    private static final class FakeProvider
            implements FundReferenceDataProvider {

        @Override
        public String providerName() {
            return "fake";
        }

        @Override
        public String sourceLabel() {
            return "fake-test-source";
        }

        @Override
        public List<FundReferenceRecord> fetchFunds() {
            return List.of(
                    new FundReferenceRecord(
                            "990001.OF",
                            "990001",
                            "同步测试混合基金",
                            FundCategory.MIXED,
                            "CNY",
                            true,
                            1),
                    new FundReferenceRecord(
                            "990002.OF",
                            "990002",
                            "同步测试指数基金",
                            FundCategory.INDEX,
                            "CNY",
                            true,
                            1));
        }

        @Override
        public List<TradingDayReferenceRecord> fetchTradingDays(
                LocalDate startDate,
                LocalDate endDate) {
            return List.of(
                    new TradingDayReferenceRecord(
                            LocalDate.of(2031, 1, 9),
                            true),
                    new TradingDayReferenceRecord(
                            LocalDate.of(2031, 1, 10),
                            true));
        }

        @Override
        public List<NavReferenceRecord> fetchNavs(
                String providerFundCode,
                LocalDate startDate,
                LocalDate endDate) {
            if ("990002.OF".equals(providerFundCode)) {
                throw new IllegalStateException(
                        "simulated upstream failure");
            }
            return List.of(new NavReferenceRecord(
                    providerFundCode,
                    LocalDate.of(2031, 1, 9),
                    new BigDecimal("1.23456789"),
                    NOW));
        }
    }

    private static final class CountingProvider
            implements FundReferenceDataProvider {

        private int fundFetches;
        private int calendarFetches;
        private int navFetches;

        @Override
        public String providerName() {
            return "counting-fake";
        }

        @Override
        public String sourceLabel() {
            return "counting-fake-source";
        }

        @Override
        public List<FundReferenceRecord> fetchFunds() {
            fundFetches++;
            return List.of(
                    new FundReferenceRecord(
                            "990001.OF",
                            "990001",
                            "增量同步测试基金一",
                            FundCategory.MIXED,
                            "CNY",
                            true,
                            1),
                    new FundReferenceRecord(
                            "990002.OF",
                            "990002",
                            "增量同步测试基金二",
                            FundCategory.INDEX,
                            "CNY",
                            true,
                            1));
        }

        @Override
        public List<TradingDayReferenceRecord> fetchTradingDays(
                LocalDate startDate,
                LocalDate endDate) {
            calendarFetches++;
            return List.of();
        }

        @Override
        public List<NavReferenceRecord> fetchNavs(
                String providerFundCode,
                LocalDate startDate,
                LocalDate endDate) {
            navFetches++;
            return List.of(new NavReferenceRecord(
                    providerFundCode,
                    LocalDate.of(2031, 1, 9),
                    new BigDecimal("1.23456789"),
                    NOW));
        }

        private void resetCounts() {
            fundFetches = 0;
            calendarFetches = 0;
            navFetches = 0;
        }
    }
}
