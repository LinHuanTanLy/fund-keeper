package com.fundkeeper.backend.auth.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AuthFlowIntegrationTests {

    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "Correct-Horse-2026";
    private static final String NEW_PASSWORD = "New-Correct-Horse-2026";

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
    void cleanDatabase() {
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
    }

    @Test
    void completeAuthenticationLifecycle() throws Exception {
        requestCode("FLOW@EXAMPLE.COM", "REGISTER");
        String registerCode = latestCode(EmailCodePurpose.REGISTER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "FLOW@EXAMPLE.COM",
                                  "password": "%s",
                                  "code": "%s"
                                }
                                """.formatted(PASSWORD, registerCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email", is(EMAIL)));

        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM users",
                                Integer.class))
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM fund_accounts WHERE name = '默认账户'",
                                Integer.class))
                .isEqualTo(1);

        assertInvalidCredentials(PASSWORD + "-wrong");

        Tokens firstLogin = login(PASSWORD);
        assertCurrentUser(firstLogin.accessToken());

        Tokens refreshed = refresh(firstLogin.refreshToken());
        org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken())
                .isNotEqualTo(firstLogin.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(firstLogin.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_REFRESH_TOKEN")));
        assertUnauthorized(firstLogin.accessToken());
        assertCurrentUser(refreshed.accessToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshed.refreshToken())))
                .andExpect(status().isNoContent());
        assertUnauthorized(refreshed.accessToken());

        Tokens beforeReset = login(PASSWORD);
        requestCode(EMAIL, "RESET_PASSWORD");
        String resetCode = latestCode(EmailCodePurpose.RESET_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(EMAIL, resetCode, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        assertUnauthorized(beforeReset.accessToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(beforeReset.refreshToken())))
                .andExpect(status().isUnauthorized());
        assertInvalidCredentials(PASSWORD);
        assertCurrentUser(login(NEW_PASSWORD).accessToken());
    }

    @Test
    void verificationCodeIsOneTimeAndRateLimited() throws Exception {
        requestCode(EMAIL, "REGISTER");
        String code = latestCode(EmailCodePurpose.REGISTER);

        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailCodeBody(EMAIL, "REGISTER")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("EMAIL_CODE_RATE_LIMITED")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(code)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(code)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_EMAIL_CODE")));
    }

    @Test
    void repeatedLoginFailuresTemporarilyBlockCorrectPassword() throws Exception {
        requestCode(EMAIL, "REGISTER");
        String code = latestCode(EmailCodePurpose.REGISTER);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(code)))
                .andExpect(status().isCreated());

        for (int attempt = 0; attempt < 5; attempt++) {
            assertInvalidCredentials("wrong-password-" + attempt);
        }
        assertInvalidCredentials(PASSWORD);
    }

    private void requestCode(String email, String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailCodeBody(email, purpose)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code", is("OK")));
    }

    private String latestCode(EmailCodePurpose purpose) {
        return emailSender.latestCode(EMAIL, purpose)
                .orElseThrow(() -> new AssertionError("验证码邮件未发送"));
    }

    private Tokens login(String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, password)))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return new Tokens(
                JsonPath.read(content, "$.data.accessToken"),
                JsonPath.read(content, "$.data.refreshToken"));
    }

    private Tokens refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return new Tokens(
                JsonPath.read(content, "$.data.accessToken"),
                JsonPath.read(content, "$.data.refreshToken"));
    }

    private void assertCurrentUser(String accessToken) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", is(EMAIL)));
    }

    private void assertUnauthorized(String accessToken) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));
    }

    private void assertInvalidCredentials(String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", is("邮箱或密码错误")));
    }

    private String registerBody(String code) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "code": "%s"
                }
                """.formatted(EMAIL, PASSWORD, code);
    }

    private String emailCodeBody(String email, String purpose) {
        return """
                {
                  "email": "%s",
                  "purpose": "%s"
                }
                """.formatted(email, purpose);
    }

    private String tokenBody(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
