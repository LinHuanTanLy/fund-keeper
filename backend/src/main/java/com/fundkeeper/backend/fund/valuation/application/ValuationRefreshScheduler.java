package com.fundkeeper.backend.fund.valuation.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.reference.domain.ReferenceDataSyncLease;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "refresh-enabled",
        havingValue = "true")
public class ValuationRefreshScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ValuationRefreshScheduler.class);
    private static final String JOB_NAME =
            "intraday-valuation-refresh";

    private final ValuationRefreshService refreshService;
    private final ReferenceDataSyncLease lease;
    private final ValuationProperties properties;
    private final String ownerId = UUID.randomUUID().toString();

    public ValuationRefreshScheduler(
            ValuationRefreshService refreshService,
            ReferenceDataSyncLease lease,
            ValuationProperties properties) {
        this.refreshService = refreshService;
        this.lease = lease;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${fundkeeper.valuation.refresh-delay-ms}",
            initialDelayString = "${fundkeeper.valuation.initial-delay-ms}")
    public void refresh() {
        if (!lease.tryAcquire(
                JOB_NAME,
                ownerId,
                "ACTIVE_FUNDS:schedule",
                properties.lockAtMostFor())) {
            LOGGER.info(
                    "Intraday valuation refresh skipped: lease is owned by another instance");
            return;
        }
        try {
            ValuationRefreshReport report =
                    refreshService.refreshActiveFunds();
            String status = report.skipped()
                    ? "SKIPPED"
                    : report.complete() ? "SUCCESS" : "PARTIAL";
            String summary = summary(report);
            lease.complete(
                    JOB_NAME,
                    ownerId,
                    status,
                    summary);
            if (!report.complete() && !report.skipped()) {
                LOGGER.warn(
                        "Intraday valuation refresh partially completed: {}",
                        summary);
            } else {
                LOGGER.info(
                        "Intraday valuation refresh completed: {}",
                        summary);
            }
        } catch (RuntimeException exception) {
            lease.complete(
                    JOB_NAME,
                    ownerId,
                    "FAILED",
                    safeMessage(exception));
            LOGGER.warn(
                    "Intraday valuation refresh failed; cached data is retained",
                    exception);
        }
    }

    private String summary(ValuationRefreshReport report) {
        return "provider="
                + report.provider()
                + ", requested="
                + report.requested()
                + ", fetched="
                + report.fetched()
                + ", missing="
                + report.missingFundCodes()
                + ", skipped="
                + report.skippedReason();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
