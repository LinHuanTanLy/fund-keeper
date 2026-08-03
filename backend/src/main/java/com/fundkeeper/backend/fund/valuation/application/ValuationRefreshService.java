package com.fundkeeper.backend.fund.valuation.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.quote.domain.MarketQuoteCache;
import com.fundkeeper.backend.fund.quote.domain.MarketQuoteProvider;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataStore;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationCache;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationProvider;
import com.fundkeeper.backend.fund.valuation.domain.MarketSessionState;

@Service
public class ValuationRefreshService {

    private final IntradayValuationProvider provider;
    private final MarketQuoteProvider marketQuoteProvider;
    private final MarketQuoteCache marketQuoteCache;
    private final IntradayValuationCache cache;
    private final FundReferenceDataStore referenceDataStore;
    private final MarketSessionService marketSessionService;
    private final ValuationProperties properties;

    @Autowired
    public ValuationRefreshService(
            Optional<IntradayValuationProvider> provider,
            Optional<MarketQuoteProvider> marketQuoteProvider,
            MarketQuoteCache marketQuoteCache,
            IntradayValuationCache cache,
            FundReferenceDataStore referenceDataStore,
            MarketSessionService marketSessionService,
            ValuationProperties properties) {
        this.provider = provider.orElse(null);
        this.marketQuoteProvider = marketQuoteProvider.orElse(null);
        this.marketQuoteCache = marketQuoteCache;
        this.cache = cache;
        this.referenceDataStore = referenceDataStore;
        this.marketSessionService = marketSessionService;
        this.properties = properties;
    }

    public ValuationRefreshReport refreshActiveFunds() {
        if (marketQuoteProvider != null) {
            return refreshMarketQuotes();
        }
        if (provider == null) {
            return skipped("none", "provider-disabled");
        }
        MarketSessionState session =
                marketSessionService.currentState();
        if (session != MarketSessionState.OPEN) {
            return skipped(
                    provider.providerName(),
                    "market-" + session.name().toLowerCase());
        }

        var fundCodes = new LinkedHashSet<>(
                properties.fundCodes());
        fundCodes.addAll(referenceDataStore.findActiveFundCodes());
        if (fundCodes.isEmpty()) {
            return skipped(
                    provider.providerName(),
                    "no-active-funds");
        }

        var valuations = provider.fetchLatest(fundCodes);
        var fetchedCodes = new LinkedHashSet<String>();
        for (var valuation : valuations) {
            cache.put(valuation, properties.cacheTtl());
            fetchedCodes.add(valuation.fundCode());
        }
        var missing = new ArrayList<>(fundCodes);
        missing.removeAll(fetchedCodes);
        return new ValuationRefreshReport(
                provider.providerName(),
                fundCodes.size(),
                valuations.size(),
                missing,
                null);
    }

    private ValuationRefreshReport refreshMarketQuotes() {
        MarketSessionState session =
                marketSessionService.currentState();
        if (session != MarketSessionState.OPEN) {
            return skipped(
                    marketQuoteProvider.providerName(),
                    "market-" + session.name().toLowerCase());
        }

        var fundCodes = new LinkedHashSet<String>();
        properties.fundCodes().stream()
                .filter(code ->
                        code.matches("(15\\d{4}|5\\d{5})"))
                .forEach(fundCodes::add);
        fundCodes.addAll(
                referenceDataStore
                        .findActiveExchangeTradedFundCodes());
        if (fundCodes.isEmpty()) {
            return skipped(
                    marketQuoteProvider.providerName(),
                    "no-active-exchange-etfs");
        }

        var quotes = marketQuoteProvider.fetchLatest(fundCodes);
        var fetchedCodes = new LinkedHashSet<String>();
        for (var quote : quotes) {
            marketQuoteCache.put(quote, properties.cacheTtl());
            fetchedCodes.add(quote.fundCode());
        }
        var missing = new ArrayList<>(fundCodes);
        missing.removeAll(fetchedCodes);
        return new ValuationRefreshReport(
                marketQuoteProvider.providerName(),
                fundCodes.size(),
                quotes.size(),
                missing,
                null);
    }

    private ValuationRefreshReport skipped(
            String provider,
            String reason) {
        return new ValuationRefreshReport(
                provider,
                0,
                0,
                java.util.List.of(),
                reason);
    }
}
