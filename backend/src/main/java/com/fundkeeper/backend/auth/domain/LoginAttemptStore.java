package com.fundkeeper.backend.auth.domain;

import java.time.Duration;

public interface LoginAttemptStore {

    boolean isBlocked(String subjectKey);

    void recordFailure(
            String subjectKey,
            int maxAttempts,
            Duration attemptWindow,
            Duration blockDuration);

    void clear(String subjectKey);
}
