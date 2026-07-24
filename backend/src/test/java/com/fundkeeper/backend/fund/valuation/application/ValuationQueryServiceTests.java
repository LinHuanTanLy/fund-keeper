package com.fundkeeper.backend.fund.valuation.application;

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
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.fund.valuation.infrastructure.InMemoryIntradayValuationCache;

class ValuationQueryServiceTests {

    private static final ZoneId CHINA =
            ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 24);

    @Test
    void freshSameDayValueIsLiveDuringTradingSession() {
        Clock clock = clockAt("2026-07-24T02:00:00Z");
        ValuationQueryService service = service(
                clock,
                Instant.parse("2026-07-24T01:59:00Z"));

        assertThat(service.quote("005827").status())
                .isEqualTo(ValuationStatus.LIVE);
    }

    @Test
    void oldFetchIsStaleEvenWhenValuationDateIsToday() {
        Clock clock = clockAt("2026-07-24T02:05:00Z");
        ValuationQueryService service = service(
                clock,
                Instant.parse("2026-07-24T02:00:00Z"));

        assertThat(service.quote("005827").status())
                .isEqualTo(ValuationStatus.STALE);
    }

    @Test
    void fetchOlderThanNinetySecondsIsDelayed() {
        Clock clock = clockAt("2026-07-24T02:02:00Z");
        ValuationQueryService service = service(
                clock,
                Instant.parse("2026-07-24T02:00:00Z"));

        assertThat(service.quote("005827").status())
                .isEqualTo(ValuationStatus.DELAYED);
    }

    private ValuationQueryService service(
            Clock clock,
            Instant fetchedAt) {
        FundDataRepository repository =
                mock(FundDataRepository.class);
        when(repository.findTradingDayOpenFlag(TODAY))
                .thenReturn(Optional.of(true));
        ValuationProperties properties = properties();
        var cache = new InMemoryIntradayValuationCache(clock);
        cache.put(
                new IntradayValuation(
                        "005827",
                        TODAY,
                        new BigDecimal("1.4918"),
                        new BigDecimal("-1.39"),
                        LocalDate.of(2026, 7, 23),
                        new BigDecimal("1.5128"),
                        fetchedAt,
                        "test"),
                Duration.ofMinutes(30));
        MarketSessionService marketSessionService =
                new MarketSessionService(
                        repository,
                        clock,
                        properties);
        return new ValuationQueryService(
                cache,
                marketSessionService,
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
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                Duration.ZERO,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(55),
                100,
                30_000,
                10,
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
