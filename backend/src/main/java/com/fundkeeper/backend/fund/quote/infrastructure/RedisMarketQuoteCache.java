package com.fundkeeper.backend.fund.quote.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.quote.domain.MarketQuoteCache;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "cache-store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisMarketQuoteCache implements MarketQuoteCache {

    private static final String KEY_PREFIX = "fund:market-quote:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisMarketQuoteCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(MarketQuote quote, Duration ttl) {
        redis.opsForValue().set(
                key(quote.fundCode()),
                objectMapper.writeValueAsString(quote),
                ttl);
    }

    @Override
    public Optional<MarketQuote> find(String fundCode) {
        String json = redis.opsForValue().get(key(fundCode));
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(
                json,
                MarketQuote.class));
    }

    private String key(String fundCode) {
        return KEY_PREFIX + fundCode;
    }
}
