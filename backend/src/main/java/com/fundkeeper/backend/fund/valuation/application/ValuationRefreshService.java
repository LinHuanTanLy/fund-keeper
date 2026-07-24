package com.fundkeeper.backend.fund.valuation.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataStore;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationCache;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationProvider;
import com.fundkeeper.backend.fund.valuation.domain.MarketSessionState;

@Service
public class ValuationRefreshService {

    private final IntradayValuationProvider provider;
    private final IntradayValuationCache cache;
    private final FundReferenceDataStore referenceDataStore;
    private final MarketSessionService marketSessionService;
    private final ValuationProperties properties;

    @Autowired
    public ValuationRefreshService(
            Optional<IntradayValuationProvider> provider,
            IntradayValuationCache cache,
            FundReferenceDataStore referenceDataStore,
            MarketSessionService marketSessionService,
            ValuationProperties properties) {
        this.provider = provider.orElse(null);
        this.cache = cache;
        this.referenceDataStore = referenceDataStore;
        this.marketSessionService = marketSessionService;
        this.properties = properties;
    }

    public ValuationRefreshReport refreshActiveFunds() {
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
