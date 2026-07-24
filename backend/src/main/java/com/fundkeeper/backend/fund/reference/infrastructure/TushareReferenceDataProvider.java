package com.fundkeeper.backend.fund.reference.infrastructure;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.reference.application.ReferenceDataProperties;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataProvider;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.NavReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.TradingDayReferenceRecord;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.reference-data",
        name = "provider",
        havingValue = "tushare")
public class TushareReferenceDataProvider
        implements FundReferenceDataProvider {

    private static final DateTimeFormatter API_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final RestClient restClient;
    private final String token;

    public TushareReferenceDataProvider(
            RestClient.Builder restClientBuilder,
            ReferenceDataProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.tushareBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.token = properties.tushareToken();
    }

    @Override
    public String providerName() {
        return "tushare";
    }

    @Override
    public String sourceLabel() {
        return "tushare-personal";
    }

    @Override
    public List<FundReferenceRecord> fetchFunds() {
        List<Row> rows = query(
                "fund_basic",
                Map.of("market", "O", "status", "L"),
                "ts_code,name,fund_type,invest_type,type,market");
        var result = new ArrayList<FundReferenceRecord>();
        for (Row row : rows) {
            mapFund(row).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public List<TradingDayReferenceRecord> fetchTradingDays(
            LocalDate startDate,
            LocalDate endDate) {
        return query(
                "trade_cal",
                Map.of(
                        "exchange", "SSE",
                        "start_date", API_DATE.format(startDate),
                        "end_date", API_DATE.format(endDate)),
                "cal_date,is_open").stream()
                .map(row -> new TradingDayReferenceRecord(
                        parseDate(row.required("cal_date")),
                        "1".equals(row.required("is_open"))))
                .toList();
    }

    @Override
    public List<NavReferenceRecord> fetchNavs(
            String providerFundCode,
            LocalDate startDate,
            LocalDate endDate) {
        return query(
                "fund_nav",
                Map.of(
                        "ts_code", providerFundCode,
                        "start_date", API_DATE.format(startDate),
                        "end_date", API_DATE.format(endDate)),
                "ts_code,ann_date,nav_date,unit_nav").stream()
                .filter(row -> row.optional("unit_nav").isPresent())
                .map(row -> new NavReferenceRecord(
                        row.required("ts_code"),
                        parseDate(row.required("nav_date")),
                        new BigDecimal(row.required("unit_nav")),
                        row.optional("ann_date")
                                .map(this::parsePublishedAt)
                                .orElse(null)))
                .toList();
    }

    private Optional<FundReferenceRecord> mapFund(Row row) {
        String providerCode = row.required("ts_code").trim();
        String code = providerCode.split("\\.", 2)[0];
        if (!code.matches("\\d{6}")) {
            return Optional.empty();
        }
        String name = row.required("name").trim();
        String fundType = row.optional("fund_type").orElse("");
        String investType = row.optional("invest_type").orElse("");
        String type = row.optional("type").orElse("");
        String classification = String.join(
                " ",
                name,
                fundType,
                investType,
                type).toUpperCase();
        if (containsAny(
                classification,
                "QDII",
                "ETF",
                "LOF",
                "货币",
                "债券",
                "FOF",
                "REIT")) {
            return Optional.empty();
        }

        FundCategory category;
        if (classification.contains("指数")) {
            category = FundCategory.INDEX;
        } else if (fundType.contains("股票")) {
            category = FundCategory.STOCK;
        } else if (fundType.contains("混合")) {
            category = FundCategory.MIXED;
        } else {
            return Optional.empty();
        }

        return Optional.of(new FundReferenceRecord(
                providerCode,
                code,
                name,
                category,
                "CNY",
                true,
                null));
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Instant parsePublishedAt(String value) {
        return parseDate(value)
                .atStartOfDay(CHINA_ZONE)
                .toInstant();
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value, API_DATE);
    }

    private List<Row> query(
            String apiName,
            Map<String, String> params,
            String fields) {
        TushareResponse response = restClient.post()
                .body(new TushareRequest(apiName, token, params, fields))
                .retrieve()
                .body(TushareResponse.class);
        if (response == null) {
            throw new IllegalStateException(
                    "Tushare returned an empty response for " + apiName);
        }
        if (response.code() != 0) {
            throw new IllegalStateException(
                    "Tushare " + apiName + " failed: code="
                            + response.code() + ", message=" + response.msg());
        }
        if (response.data() == null
                || response.data().fields() == null
                || response.data().items() == null) {
            return List.of();
        }
        var rows = new ArrayList<Row>();
        for (List<Object> item : response.data().items()) {
            var values = new LinkedHashMap<String, String>();
            for (int index = 0;
                    index < response.data().fields().size()
                            && index < item.size();
                    index++) {
                Object value = item.get(index);
                if (value != null) {
                    values.put(
                            response.data().fields().get(index),
                            String.valueOf(value));
                }
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    private record TushareRequest(
            @JsonProperty("api_name")
            String apiName,
            String token,
            Map<String, String> params,
            String fields) {
    }

    private record TushareResponse(
            int code,
            String msg,
            TushareData data) {
    }

    private record TushareData(
            List<String> fields,
            List<List<Object>> items) {
    }

    private record Row(Map<String, String> values) {

        String required(String field) {
            return optional(field)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tushare response is missing field: " + field));
        }

        Optional<String> optional(String field) {
            return Optional.ofNullable(values.get(field))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty());
        }
    }
}
