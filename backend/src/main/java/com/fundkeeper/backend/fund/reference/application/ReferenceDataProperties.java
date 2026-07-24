package com.fundkeeper.backend.fund.reference.application;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fundkeeper.reference-data")
public record ReferenceDataProperties(
        String provider,
        boolean syncOnStartup,
        List<String> fundCodes,
        int navLookbackDays,
        int calendarHistoryDays,
        int calendarFutureDays,
        String tushareBaseUrl,
        String tushareToken,
        String eastmoneyBaseUrl,
        String sseClosedDaysUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public ReferenceDataProperties {
        if (!"none".equals(provider)
                && !"tushare".equals(provider)
                && !"eastmoney-public".equals(provider)) {
            throw new IllegalArgumentException(
                    "fundkeeper.reference-data.provider must be 'none', "
                            + "'tushare' or 'eastmoney-public'");
        }
        fundCodes = fundCodes == null ? List.of() : fundCodes.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (navLookbackDays < 1
                || calendarHistoryDays < 0
                || calendarFutureDays < 1) {
            throw new IllegalArgumentException(
                    "Reference-data date ranges are invalid");
        }
        requireText(tushareBaseUrl, "tushare-base-url");
        requireText(eastmoneyBaseUrl, "eastmoney-base-url");
        requireText(sseClosedDaysUrl, "sse-closed-days-url");
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readTimeout, "read-timeout");
        if ("tushare".equals(provider)
                && (tushareToken == null || tushareToken.isBlank())) {
            throw new IllegalArgumentException(
                    "TUSHARE_TOKEN is required when the Tushare provider is enabled");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "fundkeeper.reference-data." + name + " must be configured");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "fundkeeper.reference-data." + name + " must be positive");
        }
    }
}
