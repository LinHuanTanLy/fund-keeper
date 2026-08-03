package com.fundkeeper.backend.fund.quote.domain;

import java.util.List;
import java.util.Set;

public interface MarketQuoteProvider {

    String providerName();

    List<MarketQuote> fetchLatest(Set<String> fundCodes);
}
