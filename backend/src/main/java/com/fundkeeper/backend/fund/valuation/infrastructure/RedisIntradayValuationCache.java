package com.fundkeeper.backend.fund.valuation.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationCache;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "cache-store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisIntradayValuationCache
        implements IntradayValuationCache {

    private static final String KEY_PREFIX = "fund:valuation:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisIntradayValuationCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(IntradayValuation valuation, Duration ttl) {
        redis.opsForValue().set(
                key(valuation.fundCode()),
                objectMapper.writeValueAsString(valuation),
                ttl);
    }

    @Override
    public Optional<IntradayValuation> find(String fundCode) {
        String json = redis.opsForValue().get(key(fundCode));
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(
                json,
                IntradayValuation.class));
    }

    private String key(String fundCode) {
        return KEY_PREFIX + fundCode;
    }
}
