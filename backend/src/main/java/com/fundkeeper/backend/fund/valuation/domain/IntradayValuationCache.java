package com.fundkeeper.backend.fund.valuation.domain;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface IntradayValuationCache {

    void put(IntradayValuation valuation, Duration ttl);

    Optional<IntradayValuation> find(String fundCode);

    default Map<String, IntradayValuation> findAll(
            Iterable<String> fundCodes) {
        var result =
                new java.util.LinkedHashMap<String, IntradayValuation>();
        for (String fundCode : fundCodes) {
            find(fundCode).ifPresent(
                    valuation -> result.put(fundCode, valuation));
        }
        return result;
    }
}
