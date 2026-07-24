package com.fundkeeper.backend.fund.valuation.infrastructure;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationProvider;
import com.fundkeeper.backend.fund.valuation.domain.ValuationPageIndexStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "provider",
        havingValue = "eastmoney-public")
public class EastmoneyPublicIntradayValuationProvider
        implements IntradayValuationProvider {

    private static final String SOURCE =
            "eastmoney-public:fund-guzhi-list";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ValuationPageIndexStore pageIndexStore;
    private final ValuationProperties properties;
    private final Clock clock;
    private long lastRequestStartedNanos;

    public EastmoneyPublicIntradayValuationProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ValuationPageIndexStore pageIndexStore,
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
                .defaultHeader(
                        HttpHeaders.REFERER,
                        properties.eastmoneyReferer())
                .build();
        this.objectMapper = objectMapper;
        this.pageIndexStore = pageIndexStore;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String providerName() {
        return "eastmoney-public";
    }

    @Override
    public List<IntradayValuation> fetchLatest(
            Set<String> requestedCodes) {
        Set<String> fundCodes = normalize(requestedCodes);
        if (fundCodes.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> pages =
                pageIndexStore.findPages(fundCodes);
        if (pages.size() != fundCodes.size()) {
            PagePayload full = fetchPage(
                    1,
                    properties.fullPageSize());
            if (full.totalCount() > full.rows().size()) {
                throw new IllegalStateException(
                        "Valuation full page was truncated: expected "
                                + full.totalCount()
                                + " rows but received "
                                + full.rows().size());
            }
            Map<String, Integer> rebuiltIndex =
                    buildPageIndex(full.rows());
            for (String fundCode : fundCodes) {
                rebuiltIndex.putIfAbsent(fundCode, 0);
            }
            pageIndexStore.replace(
                    rebuiltIndex,
                    properties.indexTtl());
            return select(full.rows(), fundCodes);
        }

        Set<Integer> distinctPages = pages.values().stream()
                .filter(page -> page > 0)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
        if (distinctPages.size()
                > properties.maxPagesPerRefresh()) {
            throw new IllegalStateException(
                    "Valuation refresh needs "
                            + distinctPages.size()
                            + " pages, exceeding configured limit "
                            + properties.maxPagesPerRefresh());
        }

        var rows = new ArrayList<RawValuation>();
        for (Integer page : distinctPages) {
            rows.addAll(fetchPage(
                    page,
                    properties.targetPageSize()).rows());
        }
        List<IntradayValuation> selected = select(rows, fundCodes);
        Set<String> expectedIndexedCodes = pages.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> returnedCodes = rows.stream()
                .map(RawValuation::fundCode)
                .collect(java.util.stream.Collectors.toSet());
        if (!returnedCodes.containsAll(expectedIndexedCodes)) {
            pageIndexStore.clear();
        }
        return selected;
    }

    private Map<String, Integer> buildPageIndex(
            List<RawValuation> rows) {
        var result = new HashMap<String, Integer>();
        for (int index = 0; index < rows.size(); index++) {
            int page = index / properties.targetPageSize() + 1;
            result.put(rows.get(index).fundCode(), page);
        }
        return result;
    }

    private List<IntradayValuation> select(
            List<RawValuation> rows,
            Set<String> fundCodes) {
        var result = new ArrayList<IntradayValuation>();
        var seen = new LinkedHashSet<String>();
        for (RawValuation row : rows) {
            if (fundCodes.contains(row.fundCode())
                    && seen.add(row.fundCode())
                    && row.complete()) {
                result.add(new IntradayValuation(
                        row.fundCode(),
                        row.valuationDate(),
                        row.estimatedNav(),
                        row.estimatedChangePercent(),
                        row.baseNavDate(),
                        row.baseNav(),
                        clock.instant(),
                        SOURCE));
            }
        }
        return result;
    }

    private synchronized PagePayload fetchPage(
            int pageIndex,
            int pageSize) {
        waitForRequestSlot();
        lastRequestStartedNanos = System.nanoTime();
        String url = withoutTrailingSlash(
                properties.eastmoneyApiBaseUrl())
                + "/FundGuZhi/GetFundGZList"
                + "?type=1"
                + "&sort=1"
                + "&orderType=asc"
                + "&canbuy=0"
                + "&pageIndex="
                + pageIndex
                + "&pageSize="
                + pageSize;
        String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Valuation provider returned an empty response");
        }
        return parse(response);
    }

    private void waitForRequestSlot() {
        long delayNanos = properties.requestDelay().toNanos();
        long elapsed = System.nanoTime() - lastRequestStartedNanos;
        long remaining = delayNanos - elapsed;
        if (lastRequestStartedNanos == 0 || remaining <= 0) {
            return;
        }
        try {
            long millis = remaining / 1_000_000;
            int nanos = (int) (remaining % 1_000_000);
            Thread.sleep(millis, nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Valuation request was interrupted",
                    exception);
        }
    }

    private PagePayload parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null
                    || root.path("ErrCode").asInt(-1) != 0
                    || root.path("Data").isMissingNode()) {
                throw new IllegalStateException(
                        "Valuation provider returned an error: "
                                + root.path("ErrMsg").asText("unknown"));
            }
            JsonNode list = root.path("Data").path("list");
            if (!list.isArray()) {
                throw new IllegalStateException(
                        "Valuation provider response has no list");
            }
            LocalDate responseValuationDate = date(
                    root.path("Data").path("gxrq").asText());
            LocalDate responseBaseDate = date(
                    root.path("Data").path("gzrq").asText());
            var rows = new ArrayList<RawValuation>();
            for (JsonNode row : list) {
                String fundCode = row.path("bzdm").asText("").trim();
                if (!fundCode.matches("\\d{6}")) {
                    continue;
                }
                rows.add(new RawValuation(
                        fundCode,
                        dateOrDefault(
                                row.path("gxrq").asText(),
                                responseValuationDate),
                        decimal(row.path("gsz").asText()),
                        percent(row.path("gszzl").asText()),
                        dateOrDefault(
                                row.path("gzrq").asText(),
                                responseBaseDate),
                        decimal(row.path("dwjz").asText())));
            }
            return new PagePayload(
                    rows,
                    root.path("TotalCount").asInt(rows.size()));
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException(
                    "Failed to parse valuation provider response",
                    exception);
        }
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        for (String value : values) {
            String fundCode = value == null ? "" : value.trim();
            if (!fundCode.matches("\\d{6}")) {
                throw new IllegalArgumentException(
                        "Valuation fund code must contain six digits");
            }
            result.add(fundCode);
        }
        return result;
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Valuation response is missing a date");
        }
        return LocalDate.parse(value.trim());
    }

    private LocalDate dateOrDefault(
            String value,
            LocalDate fallback) {
        return value == null || value.isBlank()
                ? fallback
                : LocalDate.parse(value.trim());
    }

    private BigDecimal decimal(String value) {
        if (value == null
                || value.isBlank()
                || "---".equals(value.trim())) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private BigDecimal percent(String value) {
        if (value == null) {
            return null;
        }
        return decimal(value.replace("%", ""));
    }

    private String withoutTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record PagePayload(
            List<RawValuation> rows,
            int totalCount) {
    }

    private record RawValuation(
            String fundCode,
            LocalDate valuationDate,
            BigDecimal estimatedNav,
            BigDecimal estimatedChangePercent,
            LocalDate baseNavDate,
            BigDecimal baseNav) {

        private boolean complete() {
            return estimatedNav != null
                    && estimatedChangePercent != null
                    && baseNav != null;
        }
    }
}
