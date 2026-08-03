package com.fundkeeper.backend.fund.quote.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class EastmoneyPublicMarketQuoteProviderTests {

    private HttpServer server;
    private EastmoneyPublicMarketQuoteProvider provider;
    private String receivedQuery;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/qt/ulist.np/get", exchange -> {
            receivedQuery = exchange.getRequestURI().getRawQuery();
            byte[] body = """
                    {
                      "rc": 0,
                      "data": {
                        "diff": [
                          {
                            "f2": 4627,
                            "f3": -265,
                            "f12": "510300",
                            "f14": "沪深300ETF华泰柏瑞",
                            "f18": 4753,
                            "f124": 1785224984
                          },
                          {
                            "f2": 3355,
                            "f3": -712,
                            "f12": "159915",
                            "f14": "创业板ETF易方达",
                            "f18": 3612,
                            "f124": 1785224058
                          }
                        ]
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl =
                "http://localhost:" + server.getAddress().getPort();
        provider = new EastmoneyPublicMarketQuoteProvider(
                RestClient.builder(),
                new ObjectMapper(),
                properties(baseUrl),
                Clock.fixed(
                        Instant.parse("2026-07-28T07:00:00Z"),
                        ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsShanghaiAndShenzhenEtfQuotesWithCorrectScale() {
        var quotes = provider.fetchLatest(
                Set.of("510300", "159915"));

        assertThat(quotes).hasSize(2);
        assertThat(quotes)
                .filteredOn(quote ->
                        quote.fundCode().equals("510300"))
                .singleElement()
                .satisfies(quote -> {
                    assertThat(quote.price())
                            .isEqualByComparingTo("4.627");
                    assertThat(quote.previousClose())
                            .isEqualByComparingTo("4.753");
                    assertThat(quote.changePercent())
                            .isEqualByComparingTo("-2.65");
                });
        assertThat(receivedQuery)
                .contains("1.510300")
                .contains("0.159915");
    }

    @Test
    void rejectsOffExchangeFundCodes() {
        assertThatThrownBy(() ->
                provider.fetchLatest(Set.of("005827")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exchange ETF code");
    }

    private ValuationProperties properties(String baseUrl) {
        return new ValuationProperties(
                "eastmoney-market",
                true,
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
                "https://unused.invalid",
                baseUrl,
                "https://unused.invalid/referer",
                "Asia/Shanghai",
                "memory",
                "memory");
    }
}
