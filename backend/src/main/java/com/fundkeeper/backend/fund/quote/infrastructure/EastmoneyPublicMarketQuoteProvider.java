package com.fundkeeper.backend.fund.quote.infrastructure;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.quote.domain.MarketQuoteProvider;
import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "provider",
        havingValue = "eastmoney-market")
public class EastmoneyPublicMarketQuoteProvider
        implements MarketQuoteProvider {

    private static final int BATCH_SIZE = 100;
    private static final BigDecimal PRICE_SCALE =
            new BigDecimal("1000");
    private static final BigDecimal PERCENT_SCALE =
            new BigDecimal("100");
    private static final String SOURCE =
            "eastmoney-public:push2-market-quote";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ValuationProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public EastmoneyPublicMarketQuoteProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ValuationProperties properties,
            Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "FundKeeper/0.0.1 (personal development)")
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.zone());
    }

    @Override
    public String providerName() {
        return "eastmoney-market";
    }

    @Override
    public List<MarketQuote> fetchLatest(Set<String> requestedCodes) {
        List<String> fundCodes = normalize(requestedCodes);
        var result = new ArrayList<MarketQuote>();
        for (int start = 0;
                start < fundCodes.size();
                start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, fundCodes.size());
            result.addAll(fetchBatch(
                    fundCodes.subList(start, end)));
        }
        return result;
    }

    private List<MarketQuote> fetchBatch(List<String> fundCodes) {
        if (fundCodes.isEmpty()) {
            return List.of();
        }
        String secids = fundCodes.stream()
                .map(this::securityId)
                .collect(java.util.stream.Collectors.joining(","));
        String url = withoutTrailingSlash(
                properties.eastmoneyQuoteBaseUrl())
                + "/api/qt/ulist.np/get"
                + "?secids="
                + secids
                + "&fields=f12,f14,f2,f18,f3,f124";
        String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Market quote provider returned an empty response");
        }
        return parse(response);
    }

    private List<MarketQuote> parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.path("rc").asInt(-1) != 0) {
                throw new IllegalStateException(
                        "Market quote provider returned an error");
            }
            JsonNode rows = root.path("data").path("diff");
            if (!rows.isArray()) {
                throw new IllegalStateException(
                        "Market quote response has no quote list");
            }
            Instant fetchedAt = clock.instant();
            var result = new ArrayList<MarketQuote>();
            for (JsonNode row : rows) {
                String fundCode = row.path("f12").asText("").trim();
                long rawPrice = row.path("f2").asLong(0);
                long rawPreviousClose = row.path("f18").asLong(0);
                long rawPercent = row.path("f3").asLong(Long.MIN_VALUE);
                long quoteEpochSecond = row.path("f124").asLong(0);
                if (!fundCode.matches("\\d{6}")
                        || rawPrice <= 0
                        || rawPreviousClose <= 0
                        || rawPercent == Long.MIN_VALUE
                        || quoteEpochSecond <= 0) {
                    continue;
                }
                Instant observedAt =
                        Instant.ofEpochSecond(quoteEpochSecond);
                result.add(new MarketQuote(
                        fundCode,
                        observedAt.atZone(zone).toLocalDate(),
                        BigDecimal.valueOf(rawPrice)
                                .divide(PRICE_SCALE),
                        BigDecimal.valueOf(rawPreviousClose)
                                .divide(PRICE_SCALE),
                        BigDecimal.valueOf(rawPercent)
                                .divide(PERCENT_SCALE),
                        observedAt,
                        fetchedAt,
                        SOURCE));
            }
            return result;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException(
                    "Failed to parse market quote response",
                    exception);
        }
    }

    private List<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (String value : values) {
            String code = value == null ? "" : value.trim();
            if (!code.matches("(15\\d{4}|5\\d{5})")) {
                throw new IllegalArgumentException(
                        "Exchange ETF code must start with 15 or 5");
            }
            result.add(code);
        }
        return List.copyOf(result);
    }

    private String securityId(String fundCode) {
        return fundCode.startsWith("15")
                ? "0." + fundCode
                : "1." + fundCode;
    }

    private String withoutTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
