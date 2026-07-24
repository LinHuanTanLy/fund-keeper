package com.fundkeeper.backend.fund.reference.application;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fundkeeper.reference-data.schedule")
public record ReferenceDataScheduleProperties(
        boolean enabled,
        String dailyNavCron,
        String fullRefreshCron,
        String zone,
        Duration lockAtMostFor) {

    public ReferenceDataScheduleProperties {
        requireText(dailyNavCron, "daily-nav-cron");
        requireText(fullRefreshCron, "full-refresh-cron");
        requireText(zone, "zone");
        ZoneId.of(zone);
        if (lockAtMostFor == null
                || lockAtMostFor.isZero()
                || lockAtMostFor.isNegative()) {
            throw new IllegalArgumentException(
                    "fundkeeper.reference-data.schedule.lock-at-most-for "
                            + "must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "fundkeeper.reference-data.schedule."
                            + name
                            + " must be configured");
        }
    }
}
