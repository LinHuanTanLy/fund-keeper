package com.fundkeeper.backend.fund.reference.application;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.reference.domain.ReferenceDataSyncLease;

@Service
public class ReferenceDataSyncCoordinator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ReferenceDataSyncCoordinator.class);
    private static final String JOB_NAME = "reference-data-sync";

    private final ReferenceDataSyncService syncService;
    private final ReferenceDataSyncLease lease;
    private final ReferenceDataScheduleProperties scheduleProperties;
    private final String ownerId = UUID.randomUUID().toString();

    public ReferenceDataSyncCoordinator(
            ReferenceDataSyncService syncService,
            ReferenceDataSyncLease lease,
            ReferenceDataScheduleProperties scheduleProperties) {
        this.syncService = syncService;
        this.lease = lease;
        this.scheduleProperties = scheduleProperties;
    }

    public Optional<ReferenceDataSyncReport> runFullSync(String trigger) {
        return run("FULL", trigger, syncService::syncConfiguredData);
    }

    public Optional<ReferenceDataSyncReport> runDailyNavSync(String trigger) {
        return run("DAILY_NAV", trigger, syncService::syncDailyNavs);
    }

    private Optional<ReferenceDataSyncReport> run(
            String scope,
            String trigger,
            Supplier<ReferenceDataSyncReport> action) {
        if (!syncService.available()) {
            LOGGER.warn(
                    "Reference-data {} sync skipped: no provider is enabled",
                    scope);
            return Optional.empty();
        }
        String runDescription = scope + ":" + trigger;
        if (!lease.tryAcquire(
                JOB_NAME,
                ownerId,
                runDescription,
                scheduleProperties.lockAtMostFor())) {
            LOGGER.info(
                    "Reference-data {} sync skipped: another instance owns the lease",
                    scope);
            return Optional.empty();
        }

        try {
            ReferenceDataSyncReport report = action.get();
            lease.complete(
                    JOB_NAME,
                    ownerId,
                    report.successful() ? "SUCCESS" : "PARTIAL",
                    summary(report));
            logReport(scope, report);
            return Optional.of(report);
        } catch (RuntimeException exception) {
            lease.complete(
                    JOB_NAME,
                    ownerId,
                    "FAILED",
                    safeMessage(exception));
            throw exception;
        }
    }

    private void logReport(
            String scope,
            ReferenceDataSyncReport report) {
        if (report.successful()) {
            LOGGER.info(
                    "Reference-data {} sync completed: provider={}, funds={}, calendarDays={}, navs={}",
                    scope,
                    report.provider(),
                    report.fundsWritten(),
                    report.tradingDaysWritten(),
                    report.navsWritten());
            return;
        }
        LOGGER.warn(
                "Reference-data {} sync completed with stale-data fallback: provider={}, funds={}, calendarDays={}, navs={}, failures={}",
                scope,
                report.provider(),
                report.fundsWritten(),
                report.tradingDaysWritten(),
                report.navsWritten(),
                report.failures());
    }

    private String summary(ReferenceDataSyncReport report) {
        return "provider="
                + report.provider()
                + ", funds="
                + report.fundsWritten()
                + ", calendarDays="
                + report.tradingDaysWritten()
                + ", navs="
                + report.navsWritten()
                + ", failures="
                + report.failures();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
