package com.fundkeeper.backend.fund.reference.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataProvider;
import com.fundkeeper.backend.fund.reference.domain.FundReferenceDataStore;

@Service
public class ReferenceDataSyncService {

    private final FundReferenceDataProvider provider;
    private final FundReferenceDataStore store;
    private final ReferenceDataProperties properties;
    private final Clock clock;

    @Autowired
    public ReferenceDataSyncService(
            Optional<FundReferenceDataProvider> provider,
            FundReferenceDataStore store,
            ReferenceDataProperties properties,
            Clock clock) {
        this.provider = provider.orElse(null);
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    public ReferenceDataSyncService(
            FundReferenceDataProvider provider,
            FundReferenceDataStore store,
            ReferenceDataProperties properties,
            Clock clock) {
        this(Optional.of(provider), store, properties, clock);
    }

    public boolean available() {
        return provider != null;
    }

    public ReferenceDataSyncReport syncConfiguredData() {
        return sync(true, true);
    }

    public ReferenceDataSyncReport syncDailyNavs() {
        return sync(false, false);
    }

    private ReferenceDataSyncReport sync(
            boolean includeFunds,
            boolean includeCalendar) {
        if (provider == null) {
            throw new IllegalStateException(
                    "No fund reference-data provider is enabled");
        }
        LocalDate today = LocalDate.now(clock);
        int fundsWritten = 0;
        int tradingDaysWritten = 0;
        int navsWritten = 0;
        var failures = new ArrayList<String>();

        if (includeFunds) {
            try {
                fundsWritten = store.upsertFunds(
                        provider.providerName(),
                        provider.fundSourceLabel(),
                        provider.fetchFunds());
            } catch (RuntimeException exception) {
                failures.add("funds: " + safeMessage(exception));
            }
        }

        if (includeCalendar) {
            try {
                tradingDaysWritten = store.upsertTradingDays(
                        provider.calendarSourceLabel(),
                        provider.fetchTradingDays(
                                today.minusDays(properties.calendarHistoryDays()),
                                today.plusDays(properties.calendarFutureDays())));
            } catch (RuntimeException exception) {
                failures.add("calendar: " + safeMessage(exception));
            }
        }

        LocalDate navStartDate =
                today.minusDays(properties.navLookbackDays() - 1L);
        var navSyncFundCodes = new LinkedHashSet<>(properties.fundCodes());
        navSyncFundCodes.addAll(store.findActiveFundCodes());
        for (String fundCode : navSyncFundCodes) {
            try {
                String providerFundCode = store.findProviderFundCode(
                                provider.providerName(),
                                fundCode)
                        .orElseThrow(() -> new IllegalStateException(
                                "fund code has no provider identifier: " + fundCode));
                navsWritten += store.upsertNavs(
                        provider.providerName(),
                        provider.navSourceLabel(),
                        provider.fetchNavs(
                                providerFundCode,
                                navStartDate,
                                today));
            } catch (RuntimeException exception) {
                failures.add(
                        "nav[" + fundCode + "]: " + safeMessage(exception));
            }
        }

        return new ReferenceDataSyncReport(
                provider.providerName(),
                fundsWritten,
                tradingDaysWritten,
                navsWritten,
                failures);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
