package com.fundkeeper.backend.portfolio.importing.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
class TransactionBatchImportIntegrationTests {

    private static final String EMAIL =
            "transaction-import@example.com";
    private static final String PASSWORD =
            "Transaction-Password-2026";

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
        insertNavAndFee("000001", "2.00000000");
        insertNavAndFee("000002", "4.00000000");
    }

    @Test
    void preflightDoesNotWriteAndCommitIsAtomicAndIdempotent()
            throws Exception {
        String token = registerAndLogin();
        String body = batch(
                "transaction-batch-success-001",
                "我的支付宝",
                "ALIPAY",
                buy(
                        "row-001",
                        "000001",
                        "1015.00",
                        null,
                        null)
                        + ","
                        + buy(
                                "row-002",
                                "000002",
                                "800.00",
                                "200.00000000",
                                "2026-07-24"));

        preflight(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")))
                .andExpect(jsonPath(
                        "$.data.account.willCreate",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.rows",
                        hasSize(2)))
                .andExpect(jsonPath(
                        "$.data.rows[0].transactionStatus",
                        is("ESTIMATED")))
                .andExpect(jsonPath(
                        "$.data.rows[0].feeAmount",
                        is(15.0)))
                .andExpect(jsonPath(
                        "$.data.rows[0].calculatedShares",
                        is(500.0)))
                .andExpect(jsonPath(
                        "$.data.rows[1].transactionStatus",
                        is("CONFIRMED")));

        assertCount("fund_accounts", 1);
        assertCount("fund_transactions", 0);
        assertCount("fund_positions", 0);

        commit(token, "transaction-batch-success-001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")))
                .andExpect(jsonPath(
                        "$.data.accountCreated",
                        is(true)))
                .andExpect(jsonPath(
                        "$.data.importedCount",
                        is(2)))
                .andExpect(jsonPath(
                        "$.data.rows",
                        hasSize(2)));

        assertCount("fund_accounts", 2);
        assertCount("fund_transactions", 2);
        assertCount("fund_positions", 2);

        commit(token, "transaction-batch-success-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")));
        assertCount("fund_transactions", 2);
        assertCount("fund_positions", 2);

        preflight(token, body)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("COMMITTED")));
    }

    @Test
    void sellDuplicateRowsAndUnknownFieldsAreRejected()
            throws Exception {
        String token = registerAndLogin();
        String sell = """
                {
                  "rowId": "row-001",
                  "fundCode": "000001",
                  "type": "SELL",
                  "sellMode": "FULL",
                  "submittedDate": "2026-07-24",
                  "submittedPeriod": "BEFORE_15"
                }
                """;
        preflight(
                token,
                batch(
                        "transaction-batch-sell-001",
                        "默认账户",
                        "OTHER",
                        sell))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.rows[0].action",
                        is("REJECT")))
                .andExpect(jsonPath(
                        "$.data.rows[0].issues[0].code",
                        is("SELL_NOT_SUPPORTED_YET")));

        String duplicate = batch(
                "transaction-batch-duplicate-001",
                "默认账户",
                "OTHER",
                buy("same-row", "000001", "100", null, null)
                        + ","
                        + buy("same-row", "000002", "100", null, null));
        preflight(token, duplicate)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.rows[1].issues[0].code",
                        is("DUPLICATE_ROW_ID")));

        String unknown = batch(
                "transaction-batch-unknown-001",
                "默认账户",
                "OTHER",
                buy("row-001", "000001", "100", null, null))
                .replace(
                        "\"amount\": 100",
                        "\"amount\": 100, \"status\": \"CONFIRMED\"");
        preflight(token, unknown)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[0].code",
                        is("UNKNOWN_FIELD")));

        assertCount("fund_transactions", 0);
        assertCount("fund_positions", 0);
    }

    @Test
    void transactionAtSnapshotBoundaryRequiresHistoryRebuild()
            throws Exception {
        String token = registerAndLogin();
        String snapshot = """
                {
                  "schemaVersion": "1.0",
                  "importType": "POSITION_SNAPSHOT",
                  "batchId": "boundary-snapshot-001",
                  "snapshotMode": "PARTIAL",
                  "account": {
                    "name": "默认账户",
                    "platform": "OTHER"
                  },
                  "snapshotAt": "2026-07-24T14:00:00+08:00",
                  "positions": [{
                    "fundCode": "000001",
                    "costAmount": 100,
                    "currentAmount": 100,
                    "holdingStartDate": "2026-07-01",
                    "confirmedShares": 50
                  }]
                }
                """;
        mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/preflight")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snapshot))
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")));
        mockMvc.perform(post(
                                "/api/v1/imports/position-snapshots/{batchId}/commit",
                                "boundary-snapshot-001")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        preflight(
                token,
                batch(
                        "transaction-before-snapshot-001",
                        "默认账户",
                        "OTHER",
                        buy(
                                "row-001",
                                "000002",
                                "100",
                                null,
                                null)))
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.rows[0].issues[0].code",
                        is("HISTORY_REBUILD_REQUIRED")));

        assertCount("fund_transactions", 1);
        assertCount("fund_positions", 1);
    }

    @Test
    void secondRowFailureRollsBackWholeBatch()
            throws Exception {
        String token = registerAndLogin();
        String body = batch(
                "transaction-batch-rollback-001",
                "默认账户",
                "OTHER",
                buy("row-001", "000001", "1015", null, null)
                        + ","
                        + buy("row-002", "000002", "406", null, null));
        preflight(token, body)
                .andExpect(jsonPath(
                        "$.data.status",
                        is("READY_TO_COMMIT")));

        String publicId = jdbcTemplate.queryForObject(
                """
                SELECT public_id
                  FROM portfolio_import_batches
                 WHERE batch_id = 'transaction-batch-rollback-001'
                """,
                String.class);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email_normalized = ?",
                Long.class,
                EMAIL);
        Long accountId = jdbcTemplate.queryForObject(
                """
                SELECT id
                  FROM fund_accounts
                 WHERE user_id = ?
                   AND name_normalized = '默认账户'
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
                        NULL, NULL, NULL, '制造第二行幂等冲突',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                UUID.randomUUID().toString(),
                userId,
                accountId,
                fundId("000002"),
                "import:" + publicId + ":2",
                "a".repeat(64));

        commit(token, "transaction-batch-rollback-001")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath(
                        "$.code",
                        is("INTERNAL_ERROR")));

        assertCount("fund_transactions", 1);
        assertCount("fund_positions", 0);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT status
                                  FROM portfolio_import_batches
                                 WHERE batch_id =
                                       'transaction-batch-rollback-001'
                                """,
                                String.class))
                .isEqualTo("COMMIT_FAILED");
    }

    @Test
    void sameBatchIdWithDifferentCommittedContentIsRejected()
            throws Exception {
        String token = registerAndLogin();
        String body = batch(
                "transaction-batch-conflict-001",
                "默认账户",
                "OTHER",
                buy("row-001", "000001", "1015", null, null));
        preflight(token, body);
        commit(token, "transaction-batch-conflict-001")
                .andExpect(status().isCreated());

        preflight(token, body.replace("1015", "2030"))
                .andExpect(jsonPath(
                        "$.data.status",
                        is("PREFLIGHT_FAILED")))
                .andExpect(jsonPath(
                        "$.data.issues[0].code",
                        is("BATCH_ID_CONFLICT")));
        assertCount("fund_transactions", 1);
    }

    private org.springframework.test.web.servlet.ResultActions preflight(
            String token,
            String body) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/imports/transaction-batches/preflight")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions commit(
            String token,
            String batchId) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/imports/transaction-batches/{batchId}/commit",
                                batchId)
                        .header("Authorization", bearer(token)));
    }

    private String batch(
            String batchId,
            String accountName,
            String platform,
            String transactions) {
        return """
                {
                  "schemaVersion": "1.0",
                  "importType": "TRANSACTION_BATCH",
                  "batchId": "%s",
                  "account": {
                    "name": "%s",
                    "platform": "%s"
                  },
                  "transactions": [%s]
                }
                """.formatted(
                batchId,
                accountName,
                platform,
                transactions);
    }

    private String buy(
            String rowId,
            String fundCode,
            String amount,
            String confirmedShares,
            String confirmedDate) {
        String confirmation = confirmedShares == null
                ? ""
                : """
                        ,
                        "confirmedShares": %s,
                        "confirmedDate": "%s"
                        """.formatted(
                        confirmedShares,
                        confirmedDate);
        return """
                {
                  "rowId": "%s",
                  "fundCode": "%s",
                  "type": "BUY",
                  "amount": %s,
                  "submittedDate": "2026-07-24",
                  "submittedPeriod": "BEFORE_15"%s
                }
                """.formatted(
                rowId,
                fundCode,
                amount,
                confirmation);
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

    private void insertNavAndFee(
            String code,
            String nav) {
        long fundId = fundId(code);
        jdbcTemplate.update(
                """
                INSERT INTO fund_navs
                    (fund_id, nav_date, unit_nav, data_source,
                     published_at, created_at)
                VALUES (?, '2026-07-24', ?, 'integration-test',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                fundId,
                new BigDecimal(nav));
        jdbcTemplate.update(
                """
                INSERT INTO fund_purchase_fee_rules
                    (fund_id, minimum_amount, maximum_amount, fee_rate,
                     calculation_method, effective_from, effective_to,
                     data_source, created_at)
                VALUES (?, 0, NULL, 0.01500000,
                        'GROSS_INCLUDES_FEE',
                        '2020-01-01', NULL, 'integration-test',
                        CURRENT_TIMESTAMP)
                """,
                fundId);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
