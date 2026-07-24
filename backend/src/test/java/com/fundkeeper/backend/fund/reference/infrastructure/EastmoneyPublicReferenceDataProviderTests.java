package com.fundkeeper.backend.fund.reference.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.reference.application.ReferenceDataProperties;

import tools.jackson.databind.ObjectMapper;

class EastmoneyPublicReferenceDataProviderTests {

    private HttpServer server;
    private EastmoneyPublicReferenceDataProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();
        ReferenceDataProperties properties = new ReferenceDataProperties(
                "eastmoney-public",
                false,
                List.of("005827"),
                10,
                30,
                400,
                "https://unused.invalid",
                "",
                baseUrl,
                baseUrl + "/sse-closed",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        provider = new EastmoneyPublicReferenceDataProvider(
                RestClient.builder(),
                new ObjectMapper(),
                properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsOnlyV1FundTypesAndKeepsSourceTrace() {
        var funds = provider.fetchFunds();

        assertThat(funds)
                .extracting(
                        fund -> fund.code(),
                        fund -> fund.category())
                .containsExactly(
                        tuple("005827", FundCategory.MIXED),
                        tuple("100001", FundCategory.STOCK),
                        tuple("200001", FundCategory.INDEX));
        assertThat(funds)
                .allSatisfy(fund -> {
                    assertThat(fund.providerCode()).isEqualTo(fund.code());
                    assertThat(fund.confirmationDelayTradingDays()).isNull();
                });
        assertThat(provider.fundSourceLabel())
                .isEqualTo("eastmoney-public:fundcode_search");
        assertThat(provider.navSourceLabel())
                .isEqualTo("eastmoney-public:pingzhongdata");
        assertThat(provider.calendarSourceLabel())
                .isEqualTo("sse-public:closed-days");
    }

    @Test
    void parsesOfficialNavAndFiltersRequestedRange() {
        var navs = provider.fetchNavs(
                "005827",
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 24));

        assertThat(navs)
                .extracting(
                        nav -> nav.navDate(),
                        nav -> nav.unitNav())
                .containsExactly(
                        tuple(
                                LocalDate.of(2026, 7, 22),
                                new java.math.BigDecimal("2.1012")),
                        tuple(
                                LocalDate.of(2026, 7, 23),
                                new java.math.BigDecimal("2.1234")));
    }

    @Test
    void buildsCurrentYearCalendarFromSseClosuresAndWeekends() {
        var calendar = provider.fetchTradingDays(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 6));

        assertThat(calendar)
                .extracting(
                        day -> day.tradeDate(),
                        day -> day.open())
                .containsExactly(
                        tuple(LocalDate.of(2026, 1, 1), false),
                        tuple(LocalDate.of(2026, 1, 2), false),
                        tuple(LocalDate.of(2026, 1, 3), false),
                        tuple(LocalDate.of(2026, 1, 4), false),
                        tuple(LocalDate.of(2026, 1, 5), true),
                        tuple(LocalDate.of(2026, 1, 6), true));
        assertThat(provider.fetchTradingDays(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 3))).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String response;
        if ("/js/fundcode_search.js".equals(path)) {
            response = """
                    \uFEFFvar r = [
                      ["005827", "YFDLCJXHH", "易方达蓝筹精选混合", "混合型-偏股", "PINYIN"],
                      ["100001", "GPG", "测试股票", "股票型", "PINYIN"],
                      ["200001", "ZS", "测试指数", "指数型-股票", "PINYIN"],
                      ["300001", "GSHZS", "固收指数", "指数型-固收", "PINYIN"],
                      ["400001", "HWZS", "海外指数", "指数型-海外股票", "PINYIN"],
                      ["500001", "ZQ", "测试债券", "债券型-长债", "PINYIN"],
                      ["600001", "QDII", "测试海外", "QDII-普通股票", "PINYIN"]
                    ];
                    """;
        } else if ("/pingzhongdata/005827.js".equals(path)) {
            long july21 = epochMillis(LocalDate.of(2026, 7, 21));
            long july22 = epochMillis(LocalDate.of(2026, 7, 22));
            long july23 = epochMillis(LocalDate.of(2026, 7, 23));
            response = """
                    var unrelated = [1, 2, 3];
                    var Data_netWorthTrend = [
                      {"x":%d,"y":2.0000,"equityReturn":0,"unitMoney":""},
                      {"x":%d,"y":2.1012,"equityReturn":1.2,"unitMoney":""},
                      {"x":%d,"y":2.1234,"equityReturn":1.05,"unitMoney":""}
                    ];
                    var Data_ACWorthTrend = [];
                    """.formatted(july21, july22, july23);
        } else if ("/sse-closed".equals(path)) {
            response = """
                    <html><body>
                    <strong>2026年休市安排</strong>
                    <table><tbody>
                      <tr><td>元旦：</td><td>1月1日（星期四）至1月3日（星期六）休市，1月5日起照常开市。</td></tr>
                      <tr><td>清明节：</td><td>4月6日（星期一）休市，4月7日起照常开市。</td></tr>
                    </tbody></table>
                    </body></html>
                    """;
        } else {
            response = "not found";
            exchange.sendResponseHeaders(404, response.length());
            exchange.getResponseBody().write(
                    response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
            return;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private long epochMillis(LocalDate date) {
        return date.atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }
}
