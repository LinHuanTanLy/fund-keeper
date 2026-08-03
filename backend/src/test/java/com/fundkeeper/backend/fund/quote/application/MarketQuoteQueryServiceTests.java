package com.fundkeeper.backend.fund.quote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.quote.domain.MarketQuote;
import com.fundkeeper.backend.fund.quote.infrastructure.InMemoryMarketQuoteCache;
import com.fundkeeper.backend.fund.valuation.application.MarketSessionService;
import com.fundkeeper.backend.fund.valuation.application.ValuationProperties;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

class MarketQuoteQueryServiceTests {

    private static final ZoneId CHINA =
            ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 28);

    @Test
    void freshQuoteIsLiveDuringTradingSession() {
        Clock clock = clockAt("2026-07-28T02:00:00Z");
        assertThat(service(
                clock,
                Instant.parse("2026-07-28T01:59:00Z"),
                true).quote("510300").status())
                .isEqualTo(ValuationStatus.LIVE);
    }

    @Test
    void cachedClosingQuoteRemainsUsableAfterMarketClose() {
        Clock clock = clockAt("2026-07-28T08:00:00Z");
        var result = service(
                clock,
                Instant.parse("2026-07-28T07:00:00Z"),
                true).quote("510300");

        assertThat(result.status())
                .isEqualTo(ValuationStatus.MARKET_CLOSED);
        assertThat(result.quote()).isPresent();
    }

    private MarketQuoteQueryService service(
            Clock clock,
            Instant observedAt,
            boolean tradingDay) {
        FundDataRepository repository =
                mock(FundDataRepository.class);
        when(repository.findTradingDayOpenFlag(TODAY))
                .thenReturn(Optional.of(tradingDay));
        ValuationProperties properties = properties();
        var cache = new InMemoryMarketQuoteCache(clock);
        cache.put(
                new MarketQuote(
                        "510300",
                        TODAY,
                        new BigDecimal("4.627"),
                        new BigDecimal("4.753"),
                        new BigDecimal("-2.65"),
                        observedAt,
                        clock.instant(),
                        "test"),
                Duration.ofHours(24));
        return new MarketQuoteQueryService(
                cache,
                new MarketSessionService(
                        repository,
                        clock,
                        properties),
                properties,
                clock);
    }

    private ValuationProperties properties() {
        return new ValuationProperties(
                "none",
                false,
                60_000,
                10_000,
                List.of(),
                Duration.ofSeconds(90),
                Duration.ofMinutes(3),
                Duration.ofHours(24),
                Duration.ofHours(24),
                Duration.ZERO,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(55),
                100,
                30_000,
                10,
                "https://unused.invalid",
                "https://unused.invalid",
                "https://unused.invalid/referer",
                CHINA.getId(),
                "memory",
                "memory");
    }

    private Clock clockAt(String instant) {
        return Clock.fixed(
                Instant.parse(instant),
                CHINA);
    }
}
