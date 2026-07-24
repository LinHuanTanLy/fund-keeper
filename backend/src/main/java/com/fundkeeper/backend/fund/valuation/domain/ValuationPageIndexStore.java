package com.fundkeeper.backend.fund.valuation.domain;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public interface ValuationPageIndexStore {

    Map<String, Integer> findPages(Set<String> fundCodes);

    void replace(Map<String, Integer> pages, Duration ttl);

    void clear();
}
