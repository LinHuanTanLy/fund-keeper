package com.fundkeeper.backend.account.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class FundAccountIsolationIntegrationTests {

    private static final String USER_A = "account-a@example.com";
    private static final String USER_B = "account-b@example.com";
    private static final String PASSWORD = "Account-Password-2026";

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
    void cleanState() {
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
    }

    @Test
    void accountsAreScopedToAuthenticatedUserAndRespectLifecycleRules()
            throws Exception {
        String tokenA = registerAndLogin(USER_A);
        String tokenB = registerAndLogin(USER_B);

        String defaultAccountA = firstAccountId(tokenA);
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("默认账户")));

        String alipayAccountA = createAccount(
                tokenA,
                "  我的支付宝  ",
                "ALIPAY",
                "我的支付宝");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("我的支付宝", "BANK")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NAME_CONFLICT")));

        createAccount(
                tokenB,
                "我的支付宝",
                "ALIPAY",
                "我的支付宝");

        mockMvc.perform(put("/api/v1/accounts/{id}", alipayAccountA)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("天天基金账户", "TIANTIAN_FUND")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("天天基金账户")))
                .andExpect(jsonPath("$.data.platform", is("TIANTIAN_FUND")));

        assertOtherUserCannotAccess(tokenB, alipayAccountA);

        mockMvc.perform(post("/api/v1/accounts/{id}/archive", defaultAccountA)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ARCHIVED")));

        mockMvc.perform(put("/api/v1/accounts/{id}", defaultAccountA)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("不能修改", "OTHER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ACCOUNT_ARCHIVED")));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(alipayAccountA)));

        mockMvc.perform(post("/api/v1/accounts/{id}/archive", alipayAccountA)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("LAST_ACTIVE_ACCOUNT")));

        createAccount(
                tokenA,
                "默认账户",
                "BANK",
                "默认账户");

        mockMvc.perform(get("/api/v1/accounts")
                        .queryParam("includeArchived", "true")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                SELECT COUNT(*)
                  FROM fund_accounts a
                  JOIN users u ON u.id = a.user_id
                 WHERE u.email_normalized = ?
                                """,
                                Integer.class,
                                USER_A))
                .isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                """
                SELECT COUNT(*)
                  FROM fund_accounts a
                  JOIN users u ON u.id = a.user_id
                 WHERE u.email_normalized = ?
                                """,
                                Integer.class,
                                USER_B))
                .isEqualTo(2);
    }

    private void assertOtherUserCannotAccess(
            String otherUserToken,
            String accountId) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(put("/api/v1/accounts/{id}", accountId)
                        .header("Authorization", bearer(otherUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("越权修改", "BANK")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")));

        mockMvc.perform(post("/api/v1/accounts/{id}/archive", accountId)
                        .header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")));
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

    private String firstAccountId(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data[0].id");
    }

    private String createAccount(
            String accessToken,
            String name,
            String platform,
            String expectedDisplayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(name, platform)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is(expectedDisplayName)))
                .andExpect(jsonPath("$.data.platform", is(platform)))
                .andReturn();
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id");
    }

    private String accountBody(String name, String platform) {
        return """
                {
                  "name": "%s",
                  "platform": "%s"
                }
                """.formatted(name, platform);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
