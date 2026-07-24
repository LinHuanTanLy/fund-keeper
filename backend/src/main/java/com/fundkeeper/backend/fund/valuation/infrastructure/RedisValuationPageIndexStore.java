package com.fundkeeper.backend.fund.valuation.infrastructure;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.valuation.domain.ValuationPageIndexStore;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "page-index-store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisValuationPageIndexStore
        implements ValuationPageIndexStore {

    private static final String KEY = "fund:valuation-page-index:v1";

    private final StringRedisTemplate redis;

    public RedisValuationPageIndexStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Map<String, Integer> findPages(Set<String> fundCodes) {
        if (fundCodes.isEmpty()) {
            return Map.of();
        }
        var codes = fundCodes.stream().toList();
        var values = redis.<String, String>opsForHash()
                .multiGet(KEY, codes);
        var result = new HashMap<String, Integer>();
        for (int index = 0; index < codes.size(); index++) {
            String value = values.get(index);
            if (value != null) {
                result.put(codes.get(index), Integer.parseInt(value));
            }
        }
        return result;
    }

    @Override
    public void replace(
            Map<String, Integer> pages,
            Duration ttl) {
        redis.delete(KEY);
        if (pages.isEmpty()) {
            return;
        }
        var values = new HashMap<String, String>();
        pages.forEach((code, page) ->
                values.put(code, Integer.toString(page)));
        redis.<String, String>opsForHash().putAll(KEY, values);
        redis.expire(KEY, ttl);
    }

    @Override
    public void clear() {
        redis.delete(KEY);
    }
}
