package com.fundkeeper.backend.auth.infrastructure.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.domain.LoginAttemptStore;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.login-attempt-store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[2]) == 1 then
                        return -1
                    end
                    local attempts = redis.call('INCR', KEYS[1])
                    if attempts == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    end
                    if attempts >= tonumber(ARGV[1]) then
                        redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
                        redis.call('DEL', KEYS[1])
                        return 1
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redis;

    public RedisLoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isBlocked(String subjectKey) {
        return Boolean.TRUE.equals(redis.hasKey(blockKey(subjectKey)));
    }

    @Override
    public void recordFailure(
            String subjectKey,
            int maxAttempts,
            Duration attemptWindow,
            Duration blockDuration) {
        redis.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(attemptsKey(subjectKey), blockKey(subjectKey)),
                Integer.toString(maxAttempts),
                Long.toString(attemptWindow.toMillis()),
                Long.toString(blockDuration.toMillis()));
    }

    @Override
    public void clear(String subjectKey) {
        redis.delete(List.of(
                attemptsKey(subjectKey),
                blockKey(subjectKey)));
    }

    private String attemptsKey(String subjectKey) {
        return "auth:login-attempts:v1:" + subjectKey;
    }

    private String blockKey(String subjectKey) {
        return "auth:login-block:v1:" + subjectKey;
    }
}
