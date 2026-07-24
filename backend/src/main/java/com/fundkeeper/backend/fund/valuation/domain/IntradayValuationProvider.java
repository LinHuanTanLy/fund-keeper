package com.fundkeeper.backend.fund.valuation.domain;

import java.util.List;
import java.util.Set;

public interface IntradayValuationProvider {

    String providerName();

    List<IntradayValuation> fetchLatest(Set<String> fundCodes);
}
