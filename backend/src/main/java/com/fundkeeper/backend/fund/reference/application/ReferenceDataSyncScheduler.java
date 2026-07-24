package com.fundkeeper.backend.fund.reference.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.reference-data.schedule",
        name = "enabled",
        havingValue = "true")
public class ReferenceDataSyncScheduler {

    private final ReferenceDataSyncCoordinator coordinator;

    public ReferenceDataSyncScheduler(
            ReferenceDataSyncCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(
            cron = "${fundkeeper.reference-data.schedule.daily-nav-cron}",
            zone = "${fundkeeper.reference-data.schedule.zone}")
    public void syncDailyNavs() {
        coordinator.runDailyNavSync("schedule");
    }

    @Scheduled(
            cron = "${fundkeeper.reference-data.schedule.full-refresh-cron}",
            zone = "${fundkeeper.reference-data.schedule.zone}")
    public void syncFullReferenceData() {
        coordinator.runFullSync("schedule");
    }
}
