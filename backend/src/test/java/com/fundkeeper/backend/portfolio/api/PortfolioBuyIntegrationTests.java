package com.fundkeeper.backend.portfolio.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
class PortfolioBuyIntegrationTests {

    private static final String PASSWORD = "Portfolio-Password-2026";
    private static final String USER_A = "portfolio-a@example.com";
    private static final String USER_B = "portfolio-b@example.com";

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
    void cleanAndSeedReferenceData() {
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

        insertFund("000001", "测试指数基金", "INDEX", true, 1);
        insertFund("000002", "不支持基金", "STOCK", false, 1);
        insertFund("000003", "等待数据基金", "MIXED", true, 1);
        insertFund("000004", "平台确认基金", "STOCK", true, 1);

        for (String date : new String[]{
                "2026-07-17",
                "2026-07-18",
                "2026-07-19",
                "2026-07-20",
                "2026-07-21",
                "2026-07-22",
                "2026-07-23",
                "2026-07-24"}) {
            boolean open = !date.equals("2026-07-18")
                    && !date.equals("2026-07-19");
            jdbcTemplate.update(
                    """
                    INSERT INTO fund_trading_days
                        (market, trade_date, is_open, data_source, updated_at)
                    VALUES ('CN_FUND', ?, ?, 'integration-test', CURRENT_TIMESTAMP)
                    """,
                    LocalDate.parse(date),
                    open);
        }

        long fundId = fundId("000001");
        jdbcTemplate.update(
                """
                INSERT INTO fund_navs
                    (fund_id, nav_date, unit_nav, data_source, published_at, created_at)
                VALUES (?, '2026-07-20', 2.00000000, 'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                fundId);
        jdbcTemplate.update(
                """
                INSERT INTO fund_purchase_fee_rules
                    (fund_id, minimum_amount, maximum_amount, fee_rate,
                     calculation_method, effective_from, effective_to,
                     data_source, created_at)
                VALUES (?, 0, NULL, 0.01500000, 'GROSS_INCLUDES_FEE',
                        '2020-01-01', NULL, 'integration-test', CURRENT_TIMESTAMP)
                """,
                fundId);
    }

    @Test
    void estimatedBuysAreIdempotentAndUseMovingAverageCost()
            throws Exception {
        String token = registerAndLogin(USER_A);
        String accountId = firstAccountId(token);

        mockMvc.perform(get("/api/v1/funds/000001")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("测试指数基金")))
                .andExpect(jsonPath("$.data.dataSource", is("integration-test")));

        MvcResult first = mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-estimated-001",
                                accountId,
                                "000001",
                                "1015.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("ESTIMATED")))
                .andExpect(jsonPath(
                        "$.data.effectiveTradeDate",
                        is("2026-07-20")))
                .andExpect(jsonPath("$.data.feeAmount", is(15.0)))
                .andExpect(jsonPath("$.data.netAmount", is(1000.0)))
                .andExpect(jsonPath("$.data.shares", is(500.0)))
                .andExpect(jsonPath("$.data.navSource", is("integration-test")))
                .andReturn();
        String firstId = JsonPath.read(
                first.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-estimated-001",
                                accountId,
                                "000001",
                                "1015.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(firstId)));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-estimated-001",
                                accountId,
                                "000001",
                                "2030.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-estimated-002",
                                accountId,
                                "000001",
                                "2030.00",
                                "2026-07-17",
                                "AFTER_15",
                                null,
                                null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.effectiveTradeDate",
                        is("2026-07-20")));

        mockMvc.perform(get("/api/v1/positions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shares", is(1500.0)))
                .andExpect(jsonPath("$.data[0].remainingCost", is(3045.0)))
                .andExpect(jsonPath("$.data[0].averageUnitCost", is(2.03)))
                .andExpect(jsonPath("$.data[0].status", is("ESTIMATED")))
                .andExpect(jsonPath(
                        "$.data[0].holdingStartDate",
                        is("2026-07-21")));

        mockMvc.perform(get("/api/v1/positions/valuations")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data[0].priceType",
                        is("OFFICIAL")))
                .andExpect(jsonPath(
                        "$.data[0].unitNav",
                        is(2.0)))
                .andExpect(jsonPath(
                        "$.data[0].marketValue",
                        is(3000.0)))
                .andExpect(jsonPath(
                        "$.data[0].profit",
                        is(-45.0)));

        org.assertj.core.api.Assertions.assertThat(count("fund_transactions"))
                .isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(count("fund_positions"))
                .isEqualTo(1);
    }

    @Test
    void weekendConfirmedBuyUsesNextTradingDayAndConfirmedShares()
            throws Exception {
        String token = registerAndLogin(USER_A);
        String accountId = firstAccountId(token);

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-confirmed-001",
                                accountId,
                                "000004",
                                "1000.00",
                                "2026-07-18",
                                "BEFORE_15",
                                "321.12345678",
                                "2026-07-21")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data.effectiveTradeDate",
                        is("2026-07-20")))
                .andExpect(jsonPath("$.data.shares", is(321.12345678)))
                .andExpect(jsonPath("$.data.unitNav").doesNotExist())
                .andExpect(jsonPath("$.data.feeRate").doesNotExist());

        mockMvc.perform(get("/api/v1/positions")
                        .queryParam("accountId", accountId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("CONFIRMED")))
                .andExpect(jsonPath(
                        "$.data[0].holdingStartDate",
                        is("2026-07-21")));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-future-001",
                                accountId,
                                "000004",
                                "1000.00",
                                LocalDate.now().plusDays(1).toString(),
                                "BEFORE_15",
                                "10",
                                null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.code",
                        is("INVALID_TRANSACTION_DATE")));

        jdbcTemplate.update(
                "DELETE FROM fund_trading_days WHERE trade_date = '2026-07-18'");
        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-calendar-gap-001",
                                accountId,
                                "000004",
                                "1000.00",
                                "2026-07-17",
                                "AFTER_15",
                                "10",
                                null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath(
                        "$.code",
                        is("TRADING_CALENDAR_UNAVAILABLE")));
    }

    @Test
    void pendingBuyDoesNotCreatePositionAndAssetsRemainUserScoped()
            throws Exception {
        String tokenA = registerAndLogin(USER_A);
        String tokenB = registerAndLogin(USER_B);
        String accountA = firstAccountId(tokenA);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "备用账户",
                                  "platform": "BANK"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult pending = mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-pending-001",
                                accountA,
                                "000003",
                                "1000.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath(
                        "$.data.pendingReason",
                        is("NAV_AND_FEE_UNAVAILABLE")))
                .andReturn();
        String transactionId = JsonPath.read(
                pending.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(get("/api/v1/positions")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/v1/accounts/{id}/archive", accountA)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("ACCOUNT_HAS_OPEN_ACTIVITY")));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-cross-user-001",
                                accountA,
                                "000001",
                                "1015.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("TRANSACTION_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/transactions/requests/buy-pending-001")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(transactionId)));

        mockMvc.perform(get("/api/v1/funds/000002")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("FUND_NOT_SUPPORTED")));
    }

    @Test
    void concurrentSameRequestCreatesOnlyOneTransaction() throws Exception {
        String token = registerAndLogin(USER_A);
        String accountId = firstAccountId(token);
        String body = buyBody(
                "buy-concurrent-001",
                accountId,
                "000001",
                "1015.00",
                "2026-07-20",
                "BEFORE_15",
                null,
                null);

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return performBuy(token, body);
            });
            var second = executor.submit(() -> {
                start.await();
                return performBuy(token, body);
            });
            start.countDown();

            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);
            var statuses = new HashSet<Integer>();
            statuses.add(firstResult.getResponse().getStatus());
            statuses.add(secondResult.getResponse().getStatus());
            org.assertj.core.api.Assertions.assertThat(statuses)
                    .containsExactlyInAnyOrder(200, 201);
            org.assertj.core.api.Assertions.assertThat(
                            transactionId(firstResult))
                    .isEqualTo(transactionId(secondResult));
        } finally {
            executor.shutdownNow();
        }

        org.assertj.core.api.Assertions.assertThat(count("fund_transactions"))
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("fund_positions"))
                .isEqualTo(1);
    }

    @Test
    void archivedAccountsRejectBuysAndPositionsPreventArchival()
            throws Exception {
        String token = registerAndLogin(USER_A);
        String defaultAccount = firstAccountId(token);
        createAccount(token, "保持活跃");
        String archivedAccount = createAccount(token, "准备归档");

        mockMvc.perform(post(
                                "/api/v1/accounts/{id}/archive",
                                archivedAccount)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-archived-001",
                                archivedAccount,
                                "000001",
                                "1015.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ACCOUNT_ARCHIVED")));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody(
                                "buy-position-001",
                                defaultAccount,
                                "000001",
                                "1015.00",
                                "2026-07-20",
                                "BEFORE_15",
                                null,
                                null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(
                                "/api/v1/accounts/{id}/archive",
                                defaultAccount)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("ACCOUNT_HAS_OPEN_ACTIVITY")));
    }

    private void insertFund(
            String code,
            String name,
            String category,
            boolean supported,
            int confirmationDelay) {
        jdbcTemplate.update(
                """
                INSERT INTO funds
                    (code, name, category, currency, supported,
                     confirmation_delay_trading_days, data_source,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'CNY', ?, ?, 'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                code,
                name,
                category,
                supported,
                confirmationDelay);
    }

    private long fundId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM funds WHERE code = ?",
                Long.class,
                code);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class);
    }

    private MvcResult performBuy(String token, String body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String transactionId(MvcResult result) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id");
    }

    private String createAccount(String token, String name)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "platform": "OTHER"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id");
    }

    private String registerAndLogin(String email) throws Exception {
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
                                """.formatted(email, PASSWORD, code)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
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

    private String buyBody(
            String requestId,
            String accountId,
            String fundCode,
            String amount,
            String submittedDate,
            String submittedPeriod,
            String confirmedShares,
            String confirmedDate) {
        return """
                {
                  "requestId": "%s",
                  "accountId": "%s",
                  "fundCode": "%s",
                  "amount": %s,
                  "submittedDate": "%s",
                  "submittedPeriod": "%s",
                  "confirmedShares": %s,
                  "confirmedDate": %s,
                  "note": "集成测试"
                }
                """.formatted(
                requestId,
                accountId,
                fundCode,
                amount,
                submittedDate,
                submittedPeriod,
                confirmedShares == null ? "null" : confirmedShares,
                confirmedDate == null
                        ? "null"
                        : "\"" + confirmedDate + "\"");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
