package com.fundkeeper.backend.fund.valuation.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationCache;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "cache-store",
        havingValue = "memory")
public class InMemoryIntradayValuationCache
        implements IntradayValuationCache {

    private final Clock clock;
    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    public InMemoryIntradayValuationCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void put(IntradayValuation valuation, Duration ttl) {
        values.put(
                valuation.fundCode(),
                new Entry(valuation, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<IntradayValuation> find(String fundCode) {
        Entry entry = values.get(fundCode);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            values.remove(fundCode);
            return Optional.empty();
        }
        return Optional.of(entry.valuation());
    }

    private record Entry(
            IntradayValuation valuation,
            Instant expiresAt) {
    }
}
