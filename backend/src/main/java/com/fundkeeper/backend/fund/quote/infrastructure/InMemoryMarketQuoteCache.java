package com.fundkeeper.backend.fund.quote.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.quote.domain.MarketQuoteCache;

@Component
@ConditionalOnProperty(
        prefix = "fundkeeper.valuation",
        name = "cache-store",
        havingValue = "memory")
public class InMemoryMarketQuoteCache implements MarketQuoteCache {

    private final Clock clock;
    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    public InMemoryMarketQuoteCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void put(MarketQuote quote, Duration ttl) {
        values.put(
                quote.fundCode(),
                new Entry(quote, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<MarketQuote> find(String fundCode) {
        Entry entry = values.get(fundCode);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            values.remove(fundCode);
            return Optional.empty();
        }
        return Optional.of(entry.quote());
    }

    private record Entry(MarketQuote quote, Instant expiresAt) {
    }
}
