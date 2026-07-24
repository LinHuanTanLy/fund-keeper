package com.fundkeeper.backend.fund.reference.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataStartupSync implements ApplicationRunner {

    private final ReferenceDataProperties properties;
    private final ReferenceDataSyncCoordinator coordinator;

    public ReferenceDataStartupSync(
            ReferenceDataProperties properties,
            ReferenceDataSyncCoordinator coordinator) {
        this.properties = properties;
        this.coordinator = coordinator;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.syncOnStartup()) {
            return;
        }
        coordinator.runFullSync("startup");
    }
}
