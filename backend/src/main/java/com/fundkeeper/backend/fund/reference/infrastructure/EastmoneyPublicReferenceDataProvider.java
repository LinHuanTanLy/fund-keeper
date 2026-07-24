package com.fundkeeper.backend.fund.reference.infrastructure;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.reference.application.ReferenceDataProperties;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataProvider;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.NavReferenceRecord;
import com.fundkeeper.backend.fund.reference.domain.TradingDayReferenceRecord;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.reference-data",
        name = "provider",
        havingValue = "eastmoney-public")
public class EastmoneyPublicReferenceDataProvider
        implements FundReferenceDataProvider {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SSE_YEAR = Pattern.compile(
            "<strong>\\s*(\\d{4})年休市安排\\s*</strong>");
    private static final Pattern TABLE_ROW = Pattern.compile(
            "<tr[^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CLOSED_RANGE = Pattern.compile(
            "(\\d{1,2})月(\\d{1,2})日.*?至\\s*"
                    + "(\\d{1,2})月(\\d{1,2})日.*?休市");
    private static final Pattern CLOSED_SINGLE = Pattern.compile(
            "(\\d{1,2})月(\\d{1,2})日.*?休市");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String eastmoneyBaseUrl;
    private final String sseClosedDaysUrl;

    public EastmoneyPublicReferenceDataProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ReferenceDataProperties properties) {
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
        this.eastmoneyBaseUrl = withoutTrailingSlash(
                properties.eastmoneyBaseUrl());
        this.sseClosedDaysUrl = properties.sseClosedDaysUrl();
    }

    @Override
    public String providerName() {
        return "eastmoney-public";
    }

    @Override
    public String sourceLabel() {
        return fundSourceLabel();
    }

    @Override
    public String fundSourceLabel() {
        return "eastmoney-public:fundcode_search";
    }

    @Override
    public String calendarSourceLabel() {
        return "sse-public:closed-days";
    }

    @Override
    public String navSourceLabel() {
        return "eastmoney-public:pingzhongdata";
    }

    @Override
    public List<FundReferenceRecord> fetchFunds() {
        String javascript = get(
                eastmoneyBaseUrl + "/js/fundcode_search.js");
        JsonNode rows = readJsonArray(javascript);
        var funds = new ArrayList<FundReferenceRecord>();
        for (JsonNode row : rows) {
            mapFund(row).ifPresent(funds::add);
        }
        return funds;
    }

    @Override
    public List<TradingDayReferenceRecord> fetchTradingDays(
            LocalDate startDate,
            LocalDate endDate) {
        requireDateRange(startDate, endDate);
        String html = get(sseClosedDaysUrl);
        Matcher yearMatcher = SSE_YEAR.matcher(html);
        if (!yearMatcher.find()) {
            throw new IllegalStateException(
                    "SSE closed-days page does not contain an annual schedule");
        }
        int scheduleYear = Integer.parseInt(yearMatcher.group(1));
        int tableEnd = html.indexOf("</table>", yearMatcher.end());
        if (tableEnd < 0) {
            throw new IllegalStateException(
                    "SSE closed-days page has no schedule table");
        }
        String scheduleTable = html.substring(
                yearMatcher.end(),
                tableEnd);
        Set<LocalDate> closedDates = parseClosedDates(
                scheduleYear,
                scheduleTable);
        if (closedDates.isEmpty()) {
            throw new IllegalStateException(
                    "SSE annual schedule contains no closed dates");
        }

        LocalDate first = startDate.isAfter(
                LocalDate.of(scheduleYear, 1, 1))
                ? startDate
                : LocalDate.of(scheduleYear, 1, 1);
        LocalDate last = endDate.isBefore(
                LocalDate.of(scheduleYear, 12, 31))
                ? endDate
                : LocalDate.of(scheduleYear, 12, 31);
        if (first.isAfter(last)) {
            return List.of();
        }

        var result = new ArrayList<TradingDayReferenceRecord>();
        for (LocalDate date = first;
                !date.isAfter(last);
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            result.add(new TradingDayReferenceRecord(
                    date,
                    !weekend && !closedDates.contains(date)));
        }
        return result;
    }

    @Override
    public List<NavReferenceRecord> fetchNavs(
            String providerFundCode,
            LocalDate startDate,
            LocalDate endDate) {
        requireDateRange(startDate, endDate);
        if (providerFundCode == null
                || !providerFundCode.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "Eastmoney fund code must contain six digits");
        }
        String javascript = get(
                eastmoneyBaseUrl
                        + "/pingzhongdata/"
                        + providerFundCode
                        + ".js");
        String json = extractAssignedJsonArray(
                javascript,
                "Data_netWorthTrend");
        JsonNode rows = readJson(json);
        var navs = new ArrayList<NavReferenceRecord>();
        for (JsonNode row : rows) {
            JsonNode timestamp = row.get("x");
            JsonNode unitNav = row.get("y");
            if (timestamp == null
                    || unitNav == null
                    || !unitNav.isNumber()) {
                continue;
            }
            LocalDate navDate = Instant
                    .ofEpochMilli(timestamp.asLong())
                    .atZone(CHINA_ZONE)
                    .toLocalDate();
            if (navDate.isBefore(startDate)
                    || navDate.isAfter(endDate)) {
                continue;
            }
            navs.add(new NavReferenceRecord(
                    providerFundCode,
                    navDate,
                    new BigDecimal(unitNav.asText()),
                    null));
        }
        return navs;
    }

    private Optional<FundReferenceRecord> mapFund(JsonNode row) {
        if (!row.isArray() || row.size() < 4) {
            return Optional.empty();
        }
        String code = text(row.get(0));
        String name = text(row.get(2));
        String type = text(row.get(3));
        if (!code.matches("\\d{6}") || name.isEmpty()) {
            return Optional.empty();
        }

        FundCategory category;
        if ("股票型".equals(type)) {
            category = FundCategory.STOCK;
        } else if (type.startsWith("混合型-")) {
            category = FundCategory.MIXED;
        } else if ("指数型-股票".equals(type)) {
            category = FundCategory.INDEX;
        } else {
            return Optional.empty();
        }
        return Optional.of(new FundReferenceRecord(
                code,
                code,
                name,
                category,
                "CNY",
                true,
                null));
    }

    private Set<LocalDate> parseClosedDates(
            int year,
            String scheduleTable) {
        var closedDates = new HashSet<LocalDate>();
        Matcher rowMatcher = TABLE_ROW.matcher(scheduleTable);
        while (rowMatcher.find()) {
            String text = stripHtml(rowMatcher.group(1));
            Matcher rangeMatcher = CLOSED_RANGE.matcher(text);
            if (rangeMatcher.find()) {
                LocalDate first = date(
                        year,
                        rangeMatcher.group(1),
                        rangeMatcher.group(2));
                LocalDate last = date(
                        year,
                        rangeMatcher.group(3),
                        rangeMatcher.group(4));
                for (LocalDate value = first;
                        !value.isAfter(last);
                        value = value.plusDays(1)) {
                    closedDates.add(value);
                }
                continue;
            }
            Matcher singleMatcher = CLOSED_SINGLE.matcher(text);
            if (singleMatcher.find()) {
                closedDates.add(date(
                        year,
                        singleMatcher.group(1),
                        singleMatcher.group(2)));
            }
        }
        return closedDates;
    }

    private LocalDate date(
            int year,
            String month,
            String day) {
        return LocalDate.of(
                year,
                Integer.parseInt(month),
                Integer.parseInt(day));
    }

    private String stripHtml(String html) {
        return html
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private JsonNode readJsonArray(String javascript) {
        int start = javascript.indexOf('[');
        if (start < 0) {
            throw new IllegalStateException(
                    "Eastmoney response does not contain a JSON array");
        }
        return readJson(extractJsonArray(javascript, start));
    }

    private String extractAssignedJsonArray(
            String javascript,
            String variableName) {
        int variable = javascript.indexOf("var " + variableName);
        if (variable < 0) {
            throw new IllegalStateException(
                    "Eastmoney response is missing variable: "
                            + variableName);
        }
        int start = javascript.indexOf('[', variable);
        if (start < 0) {
            throw new IllegalStateException(
                    "Eastmoney variable has no JSON array: "
                            + variableName);
        }
        return extractJsonArray(javascript, start);
    }

    private String extractJsonArray(String value, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
            } else if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
                if (depth == 0) {
                    return value.substring(start, index + 1);
                }
            }
        }
        throw new IllegalStateException(
                "External response contains an unterminated JSON array");
    }

    private JsonNode readJson(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (value == null || !value.isArray()) {
                throw new IllegalStateException(
                        "External response is not a JSON array");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Failed to parse external fund data",
                    exception);
        }
    }

    private String get(String url) {
        byte[] response = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
        if (response == null || response.length == 0) {
            throw new IllegalStateException(
                    "External data source returned an empty response");
        }
        return new String(response, StandardCharsets.UTF_8);
    }

    private String text(JsonNode value) {
        return value == null ? "" : value.asText().trim();
    }

    private void requireDateRange(
            LocalDate startDate,
            LocalDate endDate) {
        if (startDate == null
                || endDate == null
                || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Reference-data date range is invalid");
        }
    }

    private String withoutTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
