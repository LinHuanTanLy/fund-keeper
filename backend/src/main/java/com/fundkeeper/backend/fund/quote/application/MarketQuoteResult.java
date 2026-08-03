package com.fundkeeper.backend.fund.quote.application;

import java.util.Optional;

import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

public record MarketQuoteResult(
        ValuationStatus status,
        Optional<MarketQuote> quote) {

    public MarketQuoteResult {
        quote = quote == null ? Optional.empty() : quote;
    }
}
