package com.fundkeeper.backend.fund.valuation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;

import tools.jackson.databind.ObjectMapper;

class EastmoneyPublicIntradayValuationProviderTests {

    private static final Instant FETCHED_AT =
            Instant.parse("2026-07-24T06:30:00Z");

    private HttpServer server;
    private AtomicInteger requests;
    private EastmoneyPublicIntradayValuationProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();
        Clock clock = Clock.fixed(FETCHED_AT, ZoneOffset.UTC);
        ValuationProperties properties = new ValuationProperties(
                "eastmoney-public",
                false,
                60_000,
                10_000,
                List.of(),
                Duration.ofSeconds(90),
                Duration.ofMinutes(3),
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                Duration.ZERO,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(55),
                100,
                30_000,
                10,
                baseUrl,
                baseUrl + "/referer",
                "Asia/Shanghai",
                "memory",
                "memory");
        provider = new EastmoneyPublicIntradayValuationProvider(
                RestClient.builder(),
                new ObjectMapper(),
                new InMemoryValuationPageIndexStore(clock),
                properties,
                clock);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void buildsFullIndexOnceThenOnlyFetchesTargetPages() {
        Set<String> requested = Set.of("000001", "000101");

        var first = provider.fetchLatest(requested);
        var second = provider.fetchLatest(requested);

        assertThat(first)
                .extracting(
                        valuation -> valuation.fundCode(),
                        valuation -> valuation.estimatedNav(),
                        valuation -> valuation.estimatedChangePercent(),
                        valuation -> valuation.fetchedAt())
                .containsExactlyInAnyOrder(
                        tuple(
                                "000001",
                                new java.math.BigDecimal("1.0001"),
                                new java.math.BigDecimal("0.01"),
                                FETCHED_AT),
                        tuple(
                                "000101",
                                new java.math.BigDecimal("1.0101"),
                                new java.math.BigDecimal("1.01"),
                                FETCHED_AT));
        assertThat(second)
                .extracting(valuation -> valuation.fundCode())
                .containsExactlyInAnyOrder("000001", "000101");
        assertThat(requests).hasValue(3);
    }

    @Test
    void missingFundCodeIsNegativelyCachedUntilIndexExpires() {
        assertThat(provider.fetchLatest(Set.of("999999"))).isEmpty();
        assertThat(provider.fetchLatest(Set.of("999999"))).isEmpty();

        assertThat(requests).hasValue(1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        Map<String, String> query = query(
                exchange.getRequestURI().getRawQuery());
        int pageIndex = Integer.parseInt(query.get("pageIndex"));
        int pageSize = Integer.parseInt(query.get("pageSize"));
        List<String> rows = rows();
        int start = Math.min((pageIndex - 1) * pageSize, rows.size());
        int end = Math.min(start + pageSize, rows.size());
        String response = """
                {
                  "Data": {
                    "gxrq": "2026-07-24",
                    "gzrq": "2026-07-23",
                    "list": [%s]
                  },
                  "ErrCode": 0,
                  "ErrMsg": null,
                  "TotalCount": %d,
                  "PageSize": %d,
                  "PageIndex": %d
                }
                """.formatted(
                String.join(",", rows.subList(start, end)),
                rows.size(),
                pageSize,
                pageIndex);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private List<String> rows() {
        var result = new ArrayList<String>();
        for (int value = 1; value <= 101; value++) {
            String code = "%06d".formatted(value);
            String nav = "1.%04d".formatted(value);
            String percent = "%d.%02d%%".formatted(
                    value / 100,
                    value % 100);
            result.add("""
                    {
                      "bzdm": "%s",
                      "gxrq": "2026-07-24",
                      "gzrq": "2026-07-23",
                      "gsz": "%s",
                      "gszzl": "%s",
                      "dwjz": "1.0000"
                    }
                    """.formatted(code, nav, percent));
        }
        return result;
    }

    private Map<String, String> query(String value) {
        var result = new HashMap<String, String>();
        for (String pair : value.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
