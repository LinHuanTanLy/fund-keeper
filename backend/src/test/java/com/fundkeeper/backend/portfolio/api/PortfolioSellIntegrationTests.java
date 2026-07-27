package com.fundkeeper.backend.portfolio.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.infrastructure.mail.InMemoryVerificationEmailSender;
import com.fundkeeper.backend.auth.infrastructure.redis.InMemoryEmailCodeStore;
import com.fundkeeper.backend.auth.infrastructure.redis.InMemoryLoginAttemptStore;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioSellIntegrationTests {

    private static final String EMAIL = "sell@example.com";
    private static final String PASSWORD = "Sell-Password-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryVerificationEmailSender emailSender;

    @Autowired
    private InMemoryEmailCodeStore emailCodeStore;

    @Autowired
    private InMemoryLoginAttemptStore loginAttemptStore;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM portfolio_import_batches");
        jdbcTemplate.update("DELETE FROM fund_transactions");
        jdbcTemplate.update("DELETE FROM fund_positions");
        jdbcTemplate.update("DELETE FROM auth_sessions");
        jdbcTemplate.update("DELETE FROM fund_purchase_fee_rules");
        jdbcTemplate.update("DELETE FROM fund_navs");
        jdbcTemplate.update("DELETE FROM fund_trading_days");
        jdbcTemplate.update("DELETE FROM funds");
        jdbcTemplate.update("DELETE FROM fund_accounts");
        jdbcTemplate.update("DELETE FROM users");
        emailSender.clear();
        emailCodeStore.clear();
        loginAttemptStore.clearAll();

        jdbcTemplate.update(
                """
                INSERT INTO funds
                    (code, name, category, currency, supported,
                     confirmation_delay_trading_days, data_source,
                     created_at, updated_at)
                VALUES ('000001', '卖出测试基金', 'MIXED', 'CNY',
                        TRUE, 1, 'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO fund_trading_days
                    (market, trade_date, is_open, data_source, updated_at)
                VALUES ('CN_FUND', '2026-07-24', TRUE,
                        'integration-test', CURRENT_TIMESTAMP)
                """);
        long fundId = fundId();
        jdbcTemplate.update(
                """
                INSERT INTO fund_navs
                    (fund_id, nav_date, unit_nav, data_source,
                     published_at, created_at)
                VALUES (?, '2026-07-24', 2.00000000,
                        'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                fundId);
        jdbcTemplate.update(
                """
                INSERT INTO fund_purchase_fee_rules
                    (fund_id, minimum_amount, maximum_amount, fee_rate,
                     calculation_method, effective_from, effective_to,
                     data_source, created_at)
                VALUES (?, 0, NULL, 0, 'GROSS_INCLUDES_FEE',
                        '2020-01-01', NULL, 'integration-test',
                        CURRENT_TIMESTAMP)
                """,
                fundId);
    }

    @Test
    void estimatedPartialSellUsesMovingAverageAndIsIdempotent()
            throws Exception {
        Session session = preparedPosition();
        String body = sellBody(
                "sell-estimated-001",
                session.accountId(),
                "PARTIAL",
                "400.00",
                null,
                null,
                null);

        MvcResult created = sell(session.token(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type", is("SELL")))
                .andExpect(jsonPath(
                        "$.data.sellMode",
                        is("PARTIAL")))
                .andExpect(jsonPath(
                        "$.data.status",
                        is("ESTIMATED")))
                .andExpect(jsonPath(
                        "$.data.expectedAmount",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data.shares",
                        is(200.0)))
                .andExpect(jsonPath(
                        "$.data.removedCost",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(0.0)))
                .andReturn();
        String transactionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data[0].shares",
                        is(300.0)))
                .andExpect(jsonPath(
                        "$.data[0].remainingCost",
                        is(600.0)))
                .andExpect(jsonPath(
                        "$.data[0].averageUnitCost",
                        is(2.0)))
                .andExpect(jsonPath(
                        "$.data[0].status",
                        is("ESTIMATED")));

        sell(session.token(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.id",
                        is(transactionId)));
        assertCount("fund_transactions", 2);

        sell(
                session.token(),
                sellBody(
                        "sell-estimated-001",
                        session.accountId(),
                        "PARTIAL",
                        "300.00",
                        null,
                        null,
                        null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("IDEMPOTENCY_CONFLICT")));

        sell(
                session.token(),
                sellBody(
                        "sell-estimated-002",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        null,
                        null,
                        null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_ALREADY_OPEN")));
    }

    @Test
    void confirmedPartialSellPersistsRealizedLoss()
            throws Exception {
        Session session = preparedPosition();
        sell(
                session.token(),
                sellBody(
                        "sell-confirmed-partial-001",
                        session.accountId(),
                        "PARTIAL",
                        "420.00",
                        "390.00",
                        "200.00000000",
                        "2026-07-24"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data.actualReceivedAmount",
                        is(390.0)))
                .andExpect(jsonPath(
                        "$.data.removedCost",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(-10.0)));

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(jsonPath(
                        "$.data[0].shares",
                        is(300.0)))
                .andExpect(jsonPath(
                        "$.data[0].remainingCost",
                        is(600.0)))
                .andExpect(jsonPath(
                        "$.data[0].status",
                        is("CONFIRMED")));
    }

    @Test
    void transactionHistoryFiltersAndSummarizesConfirmedSellsOnly()
            throws Exception {
        Session session = preparedPosition();
        MvcResult confirmed = sell(
                session.token(),
                sellBody(
                        "sell-history-confirmed-001",
                        session.accountId(),
                        "PARTIAL",
                        "420.00",
                        "390.00",
                        "200.00000000",
                        "2026-07-24"))
                .andExpect(status().isCreated())
                .andReturn();
        String confirmedId = JsonPath.read(
                confirmed.getResponse().getContentAsString(),
                "$.data.id");
        MvcResult estimated = sell(
                session.token(),
                sellBody(
                        "sell-history-estimated-001",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andReturn();
        String estimatedId = JsonPath.read(
                estimated.getResponse().getContentAsString(),
                "$.data.id");

        String otherToken = registerAndLogin(
                "sell-other@example.com",
                "Other-Sell-Password-2026");
        String otherAccountId = firstAccountId(otherToken);
        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header(
                                "Authorization",
                                bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "other-user-buy-001",
                                  "accountId": "%s",
                                  "fundCode": "000001",
                                  "amount": 200.00,
                                  "submittedDate": "2026-07-24",
                                  "submittedPeriod": "BEFORE_15",
                                  "confirmedShares": 100.00000000,
                                  "confirmedDate": "2026-07-24"
                                }
                                """.formatted(otherAccountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/transactions")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.totalElements",
                        is(3)))
                .andExpect(jsonPath(
                        "$.data.totalPages",
                        is(2)))
                .andExpect(jsonPath(
                        "$.data.items",
                        hasSize(2)))
                .andExpect(jsonPath(
                        "$.data.items[0].id",
                        is(estimatedId)))
                .andExpect(jsonPath(
                        "$.data.items[1].id",
                        is(confirmedId)));

        mockMvc.perform(get("/api/v1/transactions")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", session.accountId())
                        .param("fundCode", "000001")
                        .param("type", "sell")
                        .param("status", "confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.totalElements",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.items[0].id",
                        is(confirmedId)));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", session.accountId())
                        .param("fundCode", "000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.confirmedSellCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.totalActualReceivedAmount",
                        is(390.0)))
                .andExpect(jsonPath(
                        "$.data.totalRemovedCost",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data.totalRealizedProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data.openSellCount",
                        is(1)));

        mockMvc.perform(get("/api/v1/transactions")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", otherAccountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("ACCOUNT_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/transactions")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("type", "withdrawal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(
                        "$.code",
                        is("INVALID_REQUEST")));
        mockMvc.perform(get("/api/v1/transactions")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(
                        "$.code",
                        is("INVALID_REQUEST")));

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/cancel",
                                estimatedId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "查询汇总测试撤销"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.confirmedSellCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.totalRealizedProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data.openSellCount",
                        is(0)));
    }

    @Test
    void portfolioOverviewCombinesHoldingAndRealizedProfit()
            throws Exception {
        Session session = preparedPosition();
        sell(
                session.token(),
                sellBody(
                        "sell-overview-confirmed-001",
                        session.accountId(),
                        "PARTIAL",
                        "420.00",
                        "390.00",
                        "200.00000000",
                        "2026-07-24"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portfolio/overview")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.positionCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.totalHoldingCost",
                        is(600.0)))
                .andExpect(jsonPath(
                        "$.data.currentMarketValue",
                        is(600.0)))
                .andExpect(jsonPath(
                        "$.data.currentHoldingProfit",
                        is(0.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data.cumulativeProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data.returnCostBasis",
                        is(1000.0)))
                .andExpect(jsonPath(
                        "$.data.cumulativeReturnPercent",
                        is(-1.0)))
                .andExpect(jsonPath(
                        "$.data.confirmedSellCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.openSellCount",
                        is(0)))
                .andExpect(jsonPath(
                        "$.data.valuationComplete",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.priceType",
                        is("OFFICIAL")))
                .andExpect(jsonPath(
                        "$.data.containsEstimatedData",
                        is(false)));

        sell(
                session.token(),
                sellBody(
                        "sell-overview-estimated-001",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portfolio/overview")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", session.accountId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.openSellCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.containsEstimatedData",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.totalHoldingCost",
                        is(500.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(-10.0)));

        String otherToken = registerAndLogin(
                "overview-other@example.com",
                "Other-Overview-Password-2026");
        String otherAccountId = firstAccountId(otherToken);
        mockMvc.perform(get("/api/v1/portfolio/overview")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", otherAccountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("ACCOUNT_NOT_FOUND")));
    }

    @Test
    void fundCardsAggregateSameFundAcrossAccounts()
            throws Exception {
        Session session = preparedPosition();
        sell(
                session.token(),
                sellBody(
                        "sell-card-confirmed-001",
                        session.accountId(),
                        "PARTIAL",
                        "420.00",
                        "390.00",
                        "200.00000000",
                        "2026-07-24"))
                .andExpect(status().isCreated());
        MvcResult createdAccount = mockMvc.perform(
                        post("/api/v1/accounts")
                                .header(
                                        "Authorization",
                                        bearer(session.token()))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "银行卡",
                                          "platform": "BANK"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String secondAccountId = JsonPath.read(
                createdAccount.getResponse().getContentAsString(),
                "$.data.id");
        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "buy-card-second-account-001",
                                  "accountId": "%s",
                                  "fundCode": "000001",
                                  "amount": 200.00,
                                  "submittedDate": "2026-07-24",
                                  "submittedPeriod": "BEFORE_15",
                                  "confirmedShares": 100.00000000,
                                  "confirmedDate": "2026-07-24"
                                }
                                """.formatted(secondAccountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portfolio/funds")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data[0].fundCode",
                        is("000001")))
                .andExpect(jsonPath(
                        "$.data[0].accountCount",
                        is(2)))
                .andExpect(jsonPath(
                        "$.data[0].totalShares",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data[0].holdingCost",
                        is(800.0)))
                .andExpect(jsonPath(
                        "$.data[0].currentMarketValue",
                        is(800.0)))
                .andExpect(jsonPath(
                        "$.data[0].currentHoldingProfit",
                        is(0.0)))
                .andExpect(jsonPath(
                        "$.data[0].currentHoldingReturnPercent",
                        is(0.0)))
                .andExpect(jsonPath(
                        "$.data[0].realizedProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data[0].cumulativeProfit",
                        is(-10.0)))
                .andExpect(jsonPath(
                        "$.data[0].priceType",
                        is("OFFICIAL")));

        mockMvc.perform(get("/api/v1/portfolio/funds")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .param("accountId", secondAccountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data[0].accountCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data[0].totalShares",
                        is(100.0)))
                .andExpect(jsonPath(
                        "$.data[0].holdingCost",
                        is(200.0)))
                .andExpect(jsonPath(
                        "$.data[0].realizedProfit",
                        is(0.0)));
    }

    @Test
    void pendingFullSellKeepsPositionAndBlocksAnotherSell()
            throws Exception {
        Session session = preparedPosition();
        sell(
                session.token(),
                sellBody(
                        "sell-pending-full-001",
                        session.accountId(),
                        "FULL",
                        null,
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PENDING")))
                .andExpect(jsonPath(
                        "$.data.pendingReason",
                        is("SELL_CONFIRMATION_REQUIRED")))
                .andExpect(jsonPath(
                        "$.data.shares",
                        is(500.0)));

        assertCount("fund_positions", 1);
        sell(
                session.token(),
                sellBody(
                        "sell-pending-full-002",
                        session.accountId(),
                        "FULL",
                        null,
                        "1100.00",
                        null,
                        "2026-07-24"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_ALREADY_OPEN")));
    }

    @Test
    void confirmedFullSellClearsPositionAndClosesHoldingCycle()
            throws Exception {
        Session session = preparedPosition();
        String body = sellBody(
                "sell-confirmed-full-001",
                session.accountId(),
                "FULL",
                null,
                "1100.00",
                null,
                "2026-07-24");
        sell(session.token(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data.shares",
                        is(500.0)))
                .andExpect(jsonPath(
                        "$.data.removedCost",
                        is(1000.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(100.0)));

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(jsonPath("$.data", hasSize(0)));
        assertCount("fund_transactions", 2);

        sell(session.token(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(100.0)));
        assertCount("fund_transactions", 2);
    }

    @Test
    void partialSellWithoutOfficialNavStaysPending()
            throws Exception {
        Session session = preparedPosition();
        jdbcTemplate.update("DELETE FROM fund_navs");

        sell(
                session.token(),
                sellBody(
                        "sell-no-nav-001",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PENDING")))
                .andExpect(jsonPath(
                        "$.data.pendingReason",
                        is("OFFICIAL_NAV_UNAVAILABLE")))
                .andExpect(jsonPath(
                        "$.data.shares").doesNotExist())
                .andExpect(jsonPath(
                        "$.data.removedCost").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT shares FROM fund_positions",
                                BigDecimal.class))
                .isEqualByComparingTo("500.00000000");
    }

    @Test
    void sellAtSnapshotBoundaryIsRejected()
            throws Exception {
        Session session = preparedPosition();
        String snapshot = """
                {
                  "schemaVersion": "1.0",
                  "importType": "POSITION_SNAPSHOT",
                  "batchId": "sell-boundary-snapshot-001",
                  "snapshotMode": "PARTIAL",
                  "account": {
                    "name": "默认账户",
                    "platform": "OTHER"
                  },
                  "snapshotAt": "2026-07-24T14:00:00+08:00",
                  "positions": [{
                    "fundCode": "000001",
                    "costAmount": 1000,
                    "currentAmount": 1000,
                    "holdingStartDate": "2026-07-24",
                    "confirmedShares": 500
                  }]
                }
                """;
        mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/preflight")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snapshot))
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")));
        mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/{batchId}/commit",
                                "sell-boundary-snapshot-001")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(status().isCreated());

        sell(
                session.token(),
                sellBody(
                        "sell-before-snapshot-001",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        null,
                        null,
                        null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("TRANSACTION_BEFORE_SNAPSHOT")));
        assertCount("fund_transactions", 1);
    }

    @Test
    void invalidOrExcessiveSellNeverChangesPosition()
            throws Exception {
        Session session = preparedPosition();

        sell(
                session.token(),
                sellBody(
                        "sell-excessive-amount-001",
                        session.accountId(),
                        "PARTIAL",
                        "1000.01",
                        null,
                        null,
                        null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_AMOUNT_EXCEEDS_POSITION")));

        sell(
                session.token(),
                sellBody(
                        "sell-excessive-shares-001",
                        session.accountId(),
                        "PARTIAL",
                        "100.00",
                        "100.00",
                        "500.00000000",
                        "2026-07-24"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_SHARES_EXCEED_POSITION")));

        sell(
                session.token(),
                sellBody(
                        "sell-missing-amount-001",
                        session.accountId(),
                        "PARTIAL",
                        null,
                        null,
                        null,
                        null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(
                        "$.code",
                        is("INVALID_REQUEST")));

        assertCount("fund_transactions", 1);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT shares FROM fund_positions",
                                BigDecimal.class))
                .isEqualByComparingTo("500.00000000");
    }

    @Test
    void estimatedPartialSellCanBeConfirmedAndRecalculated()
            throws Exception {
        Session session = preparedPosition();
        MvcResult created = sell(
                session.token(),
                sellBody(
                        "sell-confirm-later-001",
                        session.accountId(),
                        "PARTIAL",
                        "400.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.id");
        String confirmation = """
                {
                  "actualReceivedAmount": 370.00,
                  "confirmedShares": 180.00000000,
                  "confirmedDate": "2026-07-24"
                }
                """;

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/sell-confirmation",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data.expectedAmount",
                        is(400.0)))
                .andExpect(jsonPath(
                        "$.data.actualReceivedAmount",
                        is(370.0)))
                .andExpect(jsonPath(
                        "$.data.shares",
                        is(180.0)))
                .andExpect(jsonPath(
                        "$.data.removedCost",
                        is(360.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(10.0)));

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(jsonPath(
                        "$.data[0].shares",
                        is(320.0)))
                .andExpect(jsonPath(
                        "$.data[0].remainingCost",
                        is(640.0)))
                .andExpect(jsonPath(
                        "$.data[0].status",
                        is("CONFIRMED")));

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/sell-confirmation",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(10.0)));

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/sell-confirmation",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualReceivedAmount": 371.00,
                                  "confirmedShares": 180.00000000,
                                  "confirmedDate": "2026-07-24"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_STATE_CONFLICT")));
    }

    @Test
    void estimatedPartialSellCanBeCancelledAndRestoresPosition()
            throws Exception {
        Session session = preparedPosition();
        MvcResult created = sell(
                session.token(),
                sellBody(
                        "sell-cancel-001",
                        session.accountId(),
                        "PARTIAL",
                        "400.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "buy-blocked-by-sell-001",
                                  "accountId": "%s",
                                  "fundCode": "000001",
                                  "amount": 100.00,
                                  "submittedDate": "2026-07-24",
                                  "submittedPeriod": "BEFORE_15",
                                  "confirmedShares": 50.00000000,
                                  "confirmedDate": "2026-07-24"
                                }
                                """.formatted(session.accountId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_ALREADY_OPEN")));

        String cancellation = """
                {
                  "reason": "平台最终未成交"
                }
                """;
        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/cancel",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellation))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CANCELLED")))
                .andExpect(jsonPath(
                        "$.data.cancellationReason",
                        is("平台最终未成交")))
                .andExpect(jsonPath(
                        "$.data.cancelledAt").exists());

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(jsonPath(
                        "$.data[0].shares",
                        is(500.0)))
                .andExpect(jsonPath(
                        "$.data[0].remainingCost",
                        is(1000.0)))
                .andExpect(jsonPath(
                        "$.data[0].status",
                        is("CONFIRMED")));

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/cancel",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellation))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CANCELLED")));
    }

    @Test
    void pendingFullSellCanBeConfirmedAndClearsPosition()
            throws Exception {
        Session session = preparedPosition();
        MvcResult created = sell(
                session.token(),
                sellBody(
                        "sell-pending-confirm-001",
                        session.accountId(),
                        "FULL",
                        "1050.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/sell-confirmation",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualReceivedAmount": 1080.00,
                                  "confirmedDate": "2026-07-24"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data.shares",
                        is(500.0)))
                .andExpect(jsonPath(
                        "$.data.removedCost",
                        is(1000.0)))
                .andExpect(jsonPath(
                        "$.data.realizedProfit",
                        is(80.0)));

        mockMvc.perform(get("/api/v1/positions")
                        .header(
                                "Authorization",
                                bearer(session.token())))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void partialConfirmationRequiresSharesAndRejectsChangedState()
            throws Exception {
        Session session = preparedPosition();
        MvcResult created = sell(
                session.token(),
                sellBody(
                        "sell-state-check-001",
                        session.accountId(),
                        "PARTIAL",
                        "400.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/sell-confirmation",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualReceivedAmount": 390.00,
                                  "confirmedDate": "2026-07-24"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(
                        "$.code",
                        is("INVALID_REQUEST")));

        jdbcTemplate.update(
                """
                UPDATE fund_positions
                   SET shares = 301.00000000,
                       average_unit_cost =
                           remaining_cost / 301.00000000
                """);
        mockMvc.perform(post(
                                "/api/v1/transactions/{transactionId}/cancel",
                                transactionId)
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("SELL_STATE_CONFLICT")));
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT status
                                  FROM fund_transactions
                                 WHERE public_id = ?
                                """,
                                String.class,
                                transactionId))
                .isEqualTo("ESTIMATED");
    }

    @Test
    void openSellBlocksSnapshotAndBatchBuyPreflight()
            throws Exception {
        Session session = preparedPosition();
        sell(
                session.token(),
                sellBody(
                        "sell-block-imports-001",
                        session.accountId(),
                        "PARTIAL",
                        "400.00",
                        null,
                        null,
                        null))
                .andExpect(status().isCreated());

        mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/preflight")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": "1.0",
                                  "importType": "POSITION_SNAPSHOT",
                                  "batchId": "snapshot-open-sell-001",
                                  "snapshotMode": "PARTIAL",
                                  "account": {
                                    "name": "默认账户",
                                    "platform": "OTHER"
                                  },
                                  "snapshotAt": "2026-07-25T14:00:00+08:00",
                                  "positions": [{
                                    "fundCode": "000001",
                                    "costAmount": 1000.00,
                                    "currentAmount": 1000.00,
                                    "holdingStartDate": "2026-07-24",
                                    "confirmedShares": 500.00000000
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[*].code",
                        hasItem("OPEN_SELL_CONFLICT")));

        mockMvc.perform(post(
                                "/api/v1/imports/transaction-batches/preflight")
                        .header(
                                "Authorization",
                                bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": "1.0",
                                  "importType": "TRANSACTION_BATCH",
                                  "batchId": "batch-open-sell-001",
                                  "account": {
                                    "name": "默认账户",
                                    "platform": "OTHER"
                                  },
                                  "transactions": [{
                                    "rowId": "row-001",
                                    "fundCode": "000001",
                                    "type": "BUY",
                                    "amount": 100.00,
                                    "submittedDate": "2026-07-24",
                                    "submittedPeriod": "BEFORE_15",
                                    "confirmedShares": 50.00000000,
                                    "confirmedDate": "2026-07-24"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[*].code",
                        hasItem("OPEN_SELL_CONFLICT")));
    }

    private Session preparedPosition() throws Exception {
        String token = registerAndLogin();
        String accountId = firstAccountId(token);
        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "buy-position-001",
                                  "accountId": "%s",
                                  "fundCode": "000001",
                                  "amount": 1000.00,
                                  "submittedDate": "2026-07-24",
                                  "submittedPeriod": "BEFORE_15",
                                  "confirmedShares": 500.00000000,
                                  "confirmedDate": "2026-07-24",
                                  "note": "卖出测试建仓"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isCreated());
        return new Session(token, accountId);
    }

    private org.springframework.test.web.servlet.ResultActions sell(
            String token,
            String body) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/sells")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String sellBody(
            String requestId,
            String accountId,
            String mode,
            String expectedAmount,
            String actualReceivedAmount,
            String confirmedShares,
            String confirmedDate) {
        return """
                {
                  "requestId": "%s",
                  "accountId": "%s",
                  "fundCode": "000001",
                  "sellMode": "%s",
                  "expectedAmount": %s,
                  "actualReceivedAmount": %s,
                  "submittedDate": "2026-07-24",
                  "submittedPeriod": "BEFORE_15",
                  "confirmedShares": %s,
                  "confirmedDate": %s,
                  "note": "手动卖出测试"
                }
                """.formatted(
                requestId,
                accountId,
                mode,
                nullableNumber(expectedAmount),
                nullableNumber(actualReceivedAmount),
                nullableNumber(confirmedShares),
                confirmedDate == null
                        ? "null"
                        : "\"" + confirmedDate + "\"");
    }

    private String nullableNumber(String value) {
        return value == null ? "null" : value;
    }

    private long fundId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM funds WHERE code = '000001'",
                Long.class);
    }

    private void assertCount(String table, int expected) {
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM " + table,
                                Integer.class))
                .isEqualTo(expected);
    }

    private String registerAndLogin() throws Exception {
        return registerAndLogin(EMAIL, PASSWORD);
    }

    private String registerAndLogin(
            String email,
            String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "purpose": "REGISTER"
                                }
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        String code = emailSender
                .latestCode(email, EmailCodePurpose.REGISTER)
                .orElseThrow();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "code": "%s"
                                }
                                """.formatted(
                                email,
                                password,
                                code)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(
                                email,
                                password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(
                login.getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    private String firstAccountId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data[0].id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(
            String token,
            String accountId) {
    }
}
