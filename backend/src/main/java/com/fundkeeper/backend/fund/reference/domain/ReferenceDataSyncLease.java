package com.fundkeeper.backend.fund.reference.domain;

import java.time.Duration;

public interface ReferenceDataSyncLease {

    boolean tryAcquire(
            String jobName,
            String ownerId,
            String description,
            Duration lockAtMostFor);

    void complete(
            String jobName,
            String ownerId,
            String status,
            String summary);
}
