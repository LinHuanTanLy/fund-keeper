package com.fundkeeper.backend.fund.valuation.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.valuation.domain.MarketSessionState;

@Service
public class MarketSessionService {

    private static final LocalTime MORNING_OPEN =
            LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE =
            LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN =
            LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE =
            LocalTime.of(15, 0);

    private final FundDataRepository fundDataRepository;
    private final Clock clock;
    private final ZoneId zone;

    public MarketSessionService(
            FundDataRepository fundDataRepository,
            Clock clock,
            ValuationProperties properties) {
        this.fundDataRepository = fundDataRepository;
        this.clock = clock;
        this.zone = ZoneId.of(properties.zone());
    }

    public MarketSessionState currentState() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        var openFlag = fundDataRepository
                .findTradingDayOpenFlag(today);
        if (openFlag.isEmpty()) {
            return MarketSessionState.UNKNOWN;
        }
        if (!openFlag.get()) {
            return MarketSessionState.CLOSED;
        }
        LocalTime now = LocalTime.now(clock.withZone(zone));
        boolean morning = !now.isBefore(MORNING_OPEN)
                && !now.isAfter(MORNING_CLOSE);
        boolean afternoon = !now.isBefore(AFTERNOON_OPEN)
                && !now.isAfter(AFTERNOON_CLOSE);
        return morning || afternoon
                ? MarketSessionState.OPEN
                : MarketSessionState.CLOSED;
    }
}
