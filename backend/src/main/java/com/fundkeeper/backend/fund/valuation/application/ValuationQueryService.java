package com.fundkeeper.backend.fund.valuation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuationCache;
import com.fundkeeper.backend.fund.valuation.domain.MarketSessionState;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

@Service
public class ValuationQueryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ValuationQueryService.class);

    private final IntradayValuationCache cache;
    private final MarketSessionService marketSessionService;
    private final ValuationProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public ValuationQueryService(
            IntradayValuationCache cache,
            MarketSessionService marketSessionService,
            ValuationProperties properties,
            Clock clock) {
        this.cache = cache;
        this.marketSessionService = marketSessionService;
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.zone());
    }

    public ValuationQuote quote(String fundCode) {
        MarketSessionState session =
                marketSessionService.currentState();
        if (session == MarketSessionState.CLOSED) {
            return new ValuationQuote(
                    ValuationStatus.MARKET_CLOSED,
                    Optional.empty());
        }
        if (session == MarketSessionState.UNKNOWN) {
            return new ValuationQuote(
                    ValuationStatus.UNAVAILABLE,
                    Optional.empty());
        }

        Optional<IntradayValuation> valuation;
        try {
            valuation = cache.find(fundCode);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Intraday valuation cache is unavailable for fund {}",
                    fundCode);
            return new ValuationQuote(
                    ValuationStatus.UNAVAILABLE,
                    Optional.empty());
        }
        if (valuation.isEmpty()) {
            return new ValuationQuote(
                    ValuationStatus.UNAVAILABLE,
                    Optional.empty());
        }

        IntradayValuation value = valuation.get();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Duration age = Duration.between(
                value.fetchedAt(),
                clock.instant());
        boolean stale = !value.valuationDate().equals(today)
                || age.isNegative()
                || age.compareTo(properties.staleAfter()) > 0;
        if (stale) {
            return new ValuationQuote(
                    ValuationStatus.STALE,
                    valuation);
        }
        ValuationStatus status =
                age.compareTo(properties.delayedAfter()) > 0
                        ? ValuationStatus.DELAYED
                        : ValuationStatus.LIVE;
        return new ValuationQuote(status, valuation);
    }
}
