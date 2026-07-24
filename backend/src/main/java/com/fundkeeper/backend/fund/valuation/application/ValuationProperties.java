package com.fundkeeper.backend.fund.valuation.application;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fundkeeper.valuation")
public record ValuationProperties(
        String provider,
        boolean refreshEnabled,
        long refreshDelayMs,
        long initialDelayMs,
        List<String> fundCodes,
        Duration delayedAfter,
        Duration staleAfter,
        Duration cacheTtl,
        Duration indexTtl,
        Duration requestDelay,
        Duration connectTimeout,
        Duration readTimeout,
        Duration lockAtMostFor,
        int targetPageSize,
        int fullPageSize,
        int maxPagesPerRefresh,
        String eastmoneyApiBaseUrl,
        String eastmoneyReferer,
        String zone,
        String cacheStore,
        String pageIndexStore) {

    public ValuationProperties {
        if (!"none".equals(provider)
                && !"eastmoney-public".equals(provider)) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation.provider must be 'none' "
                            + "or 'eastmoney-public'");
        }
        if (refreshDelayMs < 30_000) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation.refresh-delay-ms "
                            + "must be at least 30000");
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation.initial-delay-ms "
                            + "must not be negative");
        }
        fundCodes = fundCodes == null ? List.of() : fundCodes.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .peek(value -> {
                    if (!value.matches("\\d{6}")) {
                        throw new IllegalArgumentException(
                                "Valuation fund codes must contain six digits");
                    }
                })
                .distinct()
                .toList();
        requirePositive(delayedAfter, "delayed-after");
        requirePositive(staleAfter, "stale-after");
        if (delayedAfter.compareTo(staleAfter) >= 0) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation.delayed-after "
                            + "must be less than stale-after");
        }
        requirePositive(cacheTtl, "cache-ttl");
        requirePositive(indexTtl, "index-ttl");
        requireNonNegative(requestDelay, "request-delay");
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readTimeout, "read-timeout");
        requirePositive(lockAtMostFor, "lock-at-most-for");
        if (targetPageSize < 100
                || fullPageSize < targetPageSize
                || maxPagesPerRefresh < 1) {
            throw new IllegalArgumentException(
                    "Valuation paging configuration is invalid");
        }
        requireText(eastmoneyApiBaseUrl, "eastmoney-api-base-url");
        requireText(eastmoneyReferer, "eastmoney-referer");
        requireText(zone, "zone");
        ZoneId.of(zone);
        requireStore(cacheStore, "cache-store");
        requireStore(pageIndexStore, "page-index-store");
    }

    private static void requireStore(String value, String name) {
        if (!"redis".equals(value) && !"memory".equals(value)) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation."
                            + name
                            + " must be 'redis' or 'memory'");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation." + name + " must be configured");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation." + name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(
                    "fundkeeper.valuation."
                            + name
                            + " must not be negative");
        }
    }
}
