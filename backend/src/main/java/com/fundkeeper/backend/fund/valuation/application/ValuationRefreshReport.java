package com.fundkeeper.backend.fund.valuation.application;

import java.util.List;

public record ValuationRefreshReport(
        String provider,
        int requested,
        int fetched,
        List<String> missingFundCodes,
        String skippedReason) {

    public ValuationRefreshReport {
        missingFundCodes = List.copyOf(missingFundCodes);
    }

    public boolean skipped() {
        return skippedReason != null;
    }

    public boolean complete() {
        return !skipped() && missingFundCodes.isEmpty();
    }
}
