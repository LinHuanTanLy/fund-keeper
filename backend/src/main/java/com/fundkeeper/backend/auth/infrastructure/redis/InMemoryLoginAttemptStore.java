package com.fundkeeper.backend.auth.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.domain.LoginAttemptStore;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.login-attempt-store",
        havingValue = "memory")
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Clock clock;
    private final Map<String, AttemptEntry> attempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> blockedUntil = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized boolean isBlocked(String subjectKey) {
        Instant until = blockedUntil.get(subjectKey);
        if (until == null) {
            return false;
        }
        if (!until.isAfter(clock.instant())) {
            blockedUntil.remove(subjectKey);
            return false;
        }
        return true;
    }

    @Override
    public synchronized void recordFailure(
            String subjectKey,
            int maxAttempts,
            Duration attemptWindow,
            Duration blockDuration) {
        if (isBlocked(subjectKey)) {
            return;
        }
        Instant now = clock.instant();
        AttemptEntry current = attempts.get(subjectKey);
        int count = current == null || !current.expiresAt().isAfter(now)
                ? 1
                : current.count() + 1;
        if (count >= maxAttempts) {
            attempts.remove(subjectKey);
            blockedUntil.put(subjectKey, now.plus(blockDuration));
            return;
        }
        attempts.put(subjectKey, new AttemptEntry(
                count,
                current == null || !current.expiresAt().isAfter(now)
                        ? now.plus(attemptWindow)
                        : current.expiresAt()));
    }

    @Override
    public synchronized void clear(String subjectKey) {
        attempts.remove(subjectKey);
        blockedUntil.remove(subjectKey);
    }

    public synchronized void clearAll() {
        attempts.clear();
        blockedUntil.clear();
    }

    private record AttemptEntry(int count, Instant expiresAt) {
    }
}
