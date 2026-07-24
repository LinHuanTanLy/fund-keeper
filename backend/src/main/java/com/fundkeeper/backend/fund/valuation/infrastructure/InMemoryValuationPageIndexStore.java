package com.fundkeeper.backend.fund.valuation.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.valuation.domain.ValuationPageIndexStore;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "page-index-store",
        havingValue = "memory")
public class InMemoryValuationPageIndexStore
        implements ValuationPageIndexStore {

    private final Clock clock;
    private Map<String, Integer> pages = Map.of();
    private Instant expiresAt = Instant.EPOCH;

    public InMemoryValuationPageIndexStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Map<String, Integer> findPages(
            Set<String> fundCodes) {
        if (!expiresAt.isAfter(clock.instant())) {
            clear();
            return Map.of();
        }
        var result = new HashMap<String, Integer>();
        for (String fundCode : fundCodes) {
            Integer page = pages.get(fundCode);
            if (page != null) {
                result.put(fundCode, page);
            }
        }
        return result;
    }

    @Override
    public synchronized void replace(
            Map<String, Integer> values,
            Duration ttl) {
        pages = Map.copyOf(values);
        expiresAt = clock.instant().plus(ttl);
    }

    @Override
    public synchronized void clear() {
        pages = Map.of();
        expiresAt = Instant.EPOCH;
    }
}
