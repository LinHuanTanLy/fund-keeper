package com.fundkeeper.backend.fund.reference.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.reference.application.ReferenceDataProperties;

class TushareReferenceDataProviderTests {

    private HttpServer server;
    private List<String> requestBodies;
    private TushareReferenceDataProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        requestBodies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        ReferenceDataProperties properties = new ReferenceDataProperties(
                "tushare",
                false,
                List.of("000001"),
                10,
                30,
                400,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-token-must-not-appear-in-errors",
                "https://unused.invalid",
                "https://unused.invalid/sse",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        provider = new TushareReferenceDataProvider(
                RestClient.builder(),
                properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsOnlySupportedDomesticOpenEndedFunds() {
        var funds = provider.fetchFunds();

        assertThat(funds)
                .extracting(
                        fund -> fund.code(),
                        fund -> fund.category())
                .containsExactly(
                        tuple("000001", FundCategory.MIXED),
                        tuple("000002", FundCategory.STOCK),
                        tuple("000003", FundCategory.INDEX));
        assertThat(funds)
                .allSatisfy(fund -> {
                    assertThat(fund.currency()).isEqualTo("CNY");
                    assertThat(fund.supported()).isTrue();
                    assertThat(fund.confirmationDelayTradingDays())
                            .isNull();
                });
        assertThat(requestBodies.getFirst())
                .contains("\"api_name\":\"fund_basic\"")
                .contains("\"market\":\"O\"")
                .contains("\"status\":\"L\"");
    }

    @Test
    void mapsTradingCalendarAndOfficialNav() {
        var calendar = provider.fetchTradingDays(
                LocalDate.of(2026, 7, 24),
                LocalDate.of(2026, 7, 25));
        var navs = provider.fetchNavs(
                "000001.OF",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 24));

        assertThat(calendar)
                .extracting(
                        day -> day.tradeDate(),
                        day -> day.open())
                .containsExactly(
                        tuple(LocalDate.of(2026, 7, 24), true),
                        tuple(LocalDate.of(2026, 7, 25), false));
        assertThat(navs).singleElement().satisfies(nav -> {
            assertThat(nav.providerFundCode()).isEqualTo("000001.OF");
            assertThat(nav.navDate()).isEqualTo(LocalDate.of(2026, 7, 23));
            assertThat(nav.unitNav()).isEqualByComparingTo("1.2345");
            assertThat(nav.publishedAt()).isNotNull();
        });
        assertThat(requestBodies)
                .anySatisfy(body -> assertThat(body)
                        .contains("\"api_name\":\"trade_cal\"")
                        .contains("\"exchange\":\"SSE\""))
                .anySatisfy(body -> assertThat(body)
                        .contains("\"api_name\":\"fund_nav\"")
                        .contains("\"ts_code\":\"000001.OF\""));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestBody = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        requestBodies.add(requestBody);
        String response;
        if (requestBody.contains("\"api_name\":\"fund_basic\"")) {
            response = """
                    {
                      "code": 0,
                      "msg": null,
                      "data": {
                        "fields": ["ts_code", "name", "fund_type", "invest_type", "type", "market"],
                        "items": [
                          ["000001.OF", "测试混合基金", "混合型", "成长型", "契约型开放式", "O"],
                          ["000002.OF", "测试股票基金", "股票型", "成长型", "契约型开放式", "O"],
                          ["000003.OF", "沪深300指数联接", "股票型", "被动指数型", "契约型开放式", "O"],
                          ["000004.OF", "海外QDII基金", "股票型", "QDII", "契约型开放式", "O"],
                          ["000005.OF", "测试债券基金", "债券型", "债券型", "契约型开放式", "O"],
                          ["000006.OF", "测试LOF", "股票型", "成长型", "LOF", "O"]
                        ]
                      }
                    }
                    """;
        } else if (requestBody.contains("\"api_name\":\"trade_cal\"")) {
            response = """
                    {
                      "code": 0,
                      "msg": null,
                      "data": {
                        "fields": ["cal_date", "is_open"],
                        "items": [
                          ["20260724", "1"],
                          ["20260725", "0"]
                        ]
                      }
                    }
                    """;
        } else {
            response = """
                    {
                      "code": 0,
                      "msg": null,
                      "data": {
                        "fields": ["ts_code", "ann_date", "nav_date", "unit_nav"],
                        "items": [
                          ["000001.OF", "20260724", "20260723", 1.2345]
                        ]
                      }
                    }
                    """;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
