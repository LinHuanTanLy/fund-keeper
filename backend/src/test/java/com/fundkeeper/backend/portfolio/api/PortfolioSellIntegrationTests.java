package com.fundkeeper.backend.portfolio.api;

import static org.hamcrest.Matchers.hasSize;
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
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "purpose": "REGISTER"
                                }
                                """.formatted(EMAIL)))
                .andExpect(status().isAccepted());
        String code = emailSender
                .latestCode(EMAIL, EmailCodePurpose.REGISTER)
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
                                EMAIL,
                                PASSWORD,
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
                                EMAIL,
                                PASSWORD)))
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
