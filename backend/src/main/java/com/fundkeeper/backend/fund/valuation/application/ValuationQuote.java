package com.fundkeeper.backend.fund.valuation.application;

import java.util.Optional;

import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

public record ValuationQuote(
        ValuationStatus status,
        Optional<IntradayValuation> valuation) {

    public ValuationQuote {
        valuation = valuation == null
                ? Optional.empty()
                : valuation;
    }
}
