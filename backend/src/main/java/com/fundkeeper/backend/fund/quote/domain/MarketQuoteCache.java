package com.fundkeeper.backend.fund.quote.domain;

import java.time.Duration;
import java.util.Optional;

public interface MarketQuoteCache {

    void put(MarketQuote quote, Duration ttl);

    Optional<MarketQuote> find(String fundCode);
}
