package com.fundkeeper.backend.portfolio.importing.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PositionSnapshotImportIntegrationTests {

    private static final String EMAIL = "snapshot-import@example.com";
    private static final String PASSWORD = "Snapshot-Password-2026";

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

        insertFund("000001", "测试一号基金");
        insertFund("000002", "测试二号基金");
        insertNav("000001", "2026-07-23", "2.00000000");
        insertNav("000002", "2026-07-23", "4.00000000");
        for (String date : new String[]{
                "2026-07-23",
                "2026-07-24",
                "2026-07-25"}) {
            jdbcTemplate.update(
                    """
                    INSERT INTO fund_trading_days
                        (market, trade_date, is_open, data_source, updated_at)
                    VALUES ('CN_FUND', ?, TRUE, 'integration-test',
                            CURRENT_TIMESTAMP)
                    """,
                    LocalDate.parse(date));
        }
        long fundId = fundId("000001");
        jdbcTemplate.update(
                """
                INSERT INTO fund_purchase_fee_rules
                    (fund_id, minimum_amount, maximum_amount, fee_rate,
                     calculation_method, effective_from, effective_to,
                     data_source, created_at)
                VALUES (?, 0, NULL, 0.01500000, 'GROSS_INCLUDES_FEE',
                        '2020-01-01', NULL, 'integration-test',
                        CURRENT_TIMESTAMP)
                """,
                fundId);
    }

    @Test
    void preflightDoesNotCreateAccountAndCommitIsIdempotent()
            throws Exception {
        String token = registerAndLogin();
        String body = snapshot(
                "snapshot-new-account-001",
                "FULL_ACCOUNT",
                "我的支付宝",
                "ALIPAY",
                "2026-07-24T14:00:00+08:00",
                """
                {
                  "fundCode": "000001",
                  "costAmount": 90.00,
                  "currentAmount": 100.00,
                  "holdingStartDate": "2026-07-01"
                }
                """);

        preflight(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")))
                .andExpect(jsonPath(
                        "$.data.account.willCreate",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.rows[0].action",
                        is("ADD")))
                .andExpect(jsonPath(
                        "$.data.rows[0].positionStatus",
                        is("ESTIMATED")))
                .andExpect(jsonPath(
                        "$.data.rows[0].calculatedShares",
                        is(50.0)))
                .andExpect(jsonPath("$.data.warningCount", is(1)));

        assertCount("fund_accounts", 1);
        assertCount("fund_positions", 0);
        assertCount("portfolio_import_batches", 1);

        MvcResult committed = commit(
                token,
                "snapshot-new-account-001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")))
                .andExpect(jsonPath(
                        "$.data.accountCreated",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.appliedCount",
                        is(1)))
                .andReturn();
        String accountId = JsonPath.read(
                committed.getResponse().getContentAsString(),
                "$.data.accountId");

        assertCount("fund_accounts", 2);
        assertCount("fund_positions", 1);
        assertCount("fund_transactions", 1);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT shares
                                  FROM fund_positions
                                 WHERE account_id = (
                                     SELECT id FROM fund_accounts
                                      WHERE public_id = ?)
                                """,
                                java.math.BigDecimal.class,
                                accountId))
                .isEqualByComparingTo("50.00000000");

        commit(token, "snapshot-new-account-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")));
        assertCount("fund_positions", 1);
        assertCount("fund_transactions", 1);

        preflight(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")));
    }

    @Test
    void invalidFundFailsPreflightWithoutChangingBusinessData()
            throws Exception {
        String token = registerAndLogin();
        String body = snapshot(
                "snapshot-invalid-fund-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T14:00:00+08:00",
                """
                {
                  "fundCode": "999999",
                  "costAmount": 90.00,
                  "currentAmount": 100.00,
                  "holdingStartDate": "2026-07-01"
                }
                """);

        preflight(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.errorCount",
                        is(1)))
                .andExpect(jsonPath(
                        "$.data.rows[0].action",
                        is("REJECT")))
                .andExpect(jsonPath(
                        "$.data.rows[0].issues[0].code",
                        is("FUND_NOT_FOUND")));

        commit(token, "snapshot-invalid-fund-001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("IMPORT_NOT_READY")));
        assertCount("fund_positions", 0);
        assertCount("fund_transactions", 0);
        assertCount("fund_accounts", 1);
    }

    @Test
    void syntaxAndUnknownFieldsAreReportedWithoutBusinessWrites()
            throws Exception {
        String token = registerAndLogin();

        preflight(token, "{\"schemaVersion\":")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[0].code",
                        is("JSON_SYNTAX_ERROR")));

        String unknownField = snapshot(
                "snapshot-unknown-field-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T14:00:00+08:00",
                """
                {
                  "fundCode": "000001",
                  "costAmount": 90.00,
                  "currentAmount": 100.00,
                  "holdingStartDate": "2026-07-01",
                  "status": "CONFIRMED"
                }
                """);
        preflight(token, unknownField)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[0].code",
                        is("UNKNOWN_FIELD")));

        assertCount("portfolio_import_batches", 1);
        assertCount("fund_positions", 0);
        assertCount("fund_transactions", 0);
    }

    @Test
    void fullAccountPreflightShowsAndCommitsClearing()
            throws Exception {
        String token = registerAndLogin();
        String first = snapshot(
                "snapshot-seed-position-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T13:00:00+08:00",
                confirmedPosition("000001", "90", "100", "50"));
        preflight(token, first)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")));
        commit(token, "snapshot-seed-position-001")
                .andExpect(status().isCreated());

        String clearing = snapshot(
                "snapshot-clear-position-001",
                "FULL_ACCOUNT",
                "默认账户",
                "OTHER",
                "2026-07-24T14:00:00+08:00",
                "");
        preflight(token, clearing)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")))
                .andExpect(jsonPath("$.data.rows", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data.rows[0].action",
                        is("CLEAR")))
                .andExpect(jsonPath(
                        "$.data.rows[0].issues[0].code",
                        is("POSITION_WILL_BE_CLEARED")));

        commit(token, "snapshot-clear-position-001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.clearedCount",
                        is(1)));
        assertCount("fund_positions", 0);
        assertCount("fund_transactions", 2);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                  FROM fund_transactions
                                 WHERE transaction_type =
                                       'POSITION_ADJUSTMENT'
                                """,
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void partialSnapshotKeepsUnlistedPosition()
            throws Exception {
        String token = registerAndLogin();
        String first = snapshot(
                "snapshot-partial-seed-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T12:00:00+08:00",
                confirmedPosition("000001", "90", "100", "50"));
        preflight(token, first);
        commit(token, "snapshot-partial-seed-001");

        String second = snapshot(
                "snapshot-partial-add-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T13:00:00+08:00",
                confirmedPosition("000002", "180", "200", "50"));
        preflight(token, second)
                .andExpect(jsonPath("$.data.rows", hasSize(1)))
                .andExpect(jsonPath(
                        "$.data.rows[0].action",
                        is("ADD")));
        commit(token, "snapshot-partial-add-001")
                .andExpect(status().isCreated());

        assertCount("fund_positions", 2);
    }

    @Test
    void committedBatchRejectsDifferentContentAndBlocksOlderBuy()
            throws Exception {
        String token = registerAndLogin();
        String accountId = firstAccountId(token);
        String body = snapshot(
                "snapshot-boundary-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T14:00:00+08:00",
                confirmedPosition("000001", "90", "100", "50"));
        preflight(token, body);
        commit(token, "snapshot-boundary-001");

        String changed = body.replace(
                "\"costAmount\": 90",
                "\"costAmount\": 91");
        preflight(token, changed)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[0].code",
                        is("BATCH_ID_CONFLICT")));

        mockMvc.perform(post("/api/v1/transactions/buys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "buy-before-snapshot-001",
                                  "accountId": "%s",
                                  "fundCode": "000001",
                                  "amount": 1015.00,
                                  "submittedDate": "2026-07-24",
                                  "submittedPeriod": "BEFORE_15",
                                  "confirmedShares": null,
                                  "confirmedDate": null,
                                  "note": "边界测试"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.code",
                        is("TRANSACTION_BEFORE_SNAPSHOT")));
        assertCount("fund_transactions", 1);
    }

    @Test
    void commitFailureRollsBackTheWholeBatch() throws Exception {
        String token = registerAndLogin();
        String body = snapshot(
                "snapshot-rollback-001",
                "PARTIAL",
                "默认账户",
                "OTHER",
                "2026-07-24T14:00:00+08:00",
                confirmedPosition("000001", "90", "100", "50")
                        + ","
                        + confirmedPosition(
                                "000002",
                                "180",
                                "200",
                                "50"));
        preflight(token, body)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")));

        String batchPublicId = jdbcTemplate.queryForObject(
                """
                SELECT public_id
                  FROM portfolio_import_batches
                 WHERE batch_id = 'snapshot-rollback-001'
                """,
                String.class);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email_normalized = ?",
                Long.class,
                EMAIL);
        Long accountId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM fund_accounts
                 WHERE user_id = ? AND name_normalized = '默认账户'
                """,
                Long.class,
                userId);
        jdbcTemplate.update(
                """
                INSERT INTO fund_transactions
                    (public_id, user_id, account_id, fund_id,
                     request_id, request_fingerprint, transaction_type,
                     status, gross_amount, fee_amount, net_amount, shares,
                     submitted_date, submitted_period,
                     effective_trade_date, confirmed_date, nav_date,
                     unit_nav, nav_source, fee_rate, fee_source,
                     pending_reason, note, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'BUY', 'CONFIRMED',
                        1, NULL, NULL, 1, '2026-07-24', 'BEFORE_15',
                        '2026-07-24', '2026-07-24', NULL, NULL, NULL,
                        NULL, NULL, NULL, '制造第二行唯一键冲突',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                java.util.UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId("000002"),
                "snapshot:" + batchPublicId + ":2",
                "a".repeat(64));

        commit(token, "snapshot-rollback-001")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath(
                        "$.code",
                        is("INTERNAL_ERROR")));

        assertCount("fund_positions", 0);
        assertCount("fund_transactions", 1);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT status
                                  FROM portfolio_import_batches
                                 WHERE batch_id = 'snapshot-rollback-001'
                """,
                                String.class))
                .isEqualTo("COMMIT_FAILED");
    }

    private org.springframework.test.web.servlet.ResultActions preflight(
            String token,
            String body) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/preflight")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions commit(
            String token,
            String batchId) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/{batchId}/commit",
                                batchId)
                        .header("Authorization", bearer(token)));
    }

    private String snapshot(
            String batchId,
            String mode,
            String accountName,
            String platform,
            String snapshotAt,
            String positionJson) {
        String positions = positionJson.isBlank()
                ? ""
                : positionJson;
        return """
                {
                  "schemaVersion": "1.0",
                  "importType": "POSITION_SNAPSHOT",
                  "batchId": "%s",
                  "snapshotMode": "%s",
                  "account": {
                    "name": "%s",
                    "platform": "%s"
                  },
                  "snapshotAt": "%s",
                  "positions": [%s]
                }
                """.formatted(
                batchId,
                mode,
                accountName,
                platform,
                snapshotAt,
                positions);
    }

    private String confirmedPosition(
            String fundCode,
            String costAmount,
            String currentAmount,
            String shares) {
        return """
                {
                  "fundCode": "%s",
                  "costAmount": %s,
                  "currentAmount": %s,
                  "holdingStartDate": "2026-07-01",
                  "confirmedShares": %s
                }
                """.formatted(
                fundCode,
                costAmount,
                currentAmount,
                shares);
    }

    private void insertFund(String code, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO funds
                    (code, name, category, currency, supported,
                     confirmation_delay_trading_days, data_source,
                     created_at, updated_at)
                VALUES (?, ?, 'MIXED', 'CNY', TRUE, 1,
                        'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                code,
                name);
    }

    private void insertNav(
            String code,
            String date,
            String unitNav) {
        jdbcTemplate.update(
                """
                INSERT INTO fund_navs
                    (fund_id, nav_date, unit_nav, data_source,
                     published_at, created_at)
                VALUES (?, ?, ?, 'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                fundId(code),
                LocalDate.parse(date),
                new java.math.BigDecimal(unitNav));
    }

    private long fundId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM funds WHERE code = ?",
                Long.class,
                code);
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
                                """.formatted(EMAIL, PASSWORD, code)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, PASSWORD)))
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
}
