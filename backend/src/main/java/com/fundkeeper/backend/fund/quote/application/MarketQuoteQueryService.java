package com.fundkeeper.backend.fund.quote.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.quote.domain.MarketQuoteCache;
import com.fundkeeper.backend.fund.valuation.application.MarketSessionService;
import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;
import com.fundkeeper.backend.fund.valuation.domain.MarketSessionState;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

@Service
public class MarketQuoteQueryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MarketQuoteQueryService.class);

    private final MarketQuoteCache cache;
    private final MarketSessionService marketSessionService;
    private final ValuationProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public MarketQuoteQueryService(
            MarketQuoteCache cache,
            MarketSessionService marketSessionService,
            ValuationProperties properties,
            Clock clock) {
        this.cache = cache;
        this.marketSessionService = marketSessionService;
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.zone());
    }

    public MarketQuoteResult quote(String fundCode) {
        Optional<MarketQuote> quote;
        try {
            quote = cache.find(fundCode);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Market quote cache is unavailable for fund {}",
                    fundCode);
            return unavailable();
        }
        if (quote.isEmpty()) {
            return unavailable();
        }

        MarketSessionState session =
                marketSessionService.currentState();
        if (session == MarketSessionState.CLOSED) {
            return new MarketQuoteResult(
                    ValuationStatus.MARKET_CLOSED,
                    quote);
        }
        if (session == MarketSessionState.UNKNOWN) {
            return new MarketQuoteResult(
                    ValuationStatus.UNAVAILABLE,
                    quote);
        }

        MarketQuote value = quote.get();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Duration age = Duration.between(
                value.observedAt(),
                clock.instant());
        boolean stale = !value.tradeDate().equals(today)
                || age.isNegative()
                || age.compareTo(properties.staleAfter()) > 0;
        if (stale) {
            return new MarketQuoteResult(
                    ValuationStatus.STALE,
                    quote);
        }
        return new MarketQuoteResult(
                age.compareTo(properties.delayedAfter()) > 0
                        ? ValuationStatus.DELAYED
                        : ValuationStatus.LIVE,
                quote);
    }

    private MarketQuoteResult unavailable() {
        return new MarketQuoteResult(
                ValuationStatus.UNAVAILABLE,
                Optional.empty());
    }
}
