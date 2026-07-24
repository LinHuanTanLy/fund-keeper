package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.application.PositionValuationDetails;
import com.fundkeeper.backend.portfolio.application.ValuationPriceType;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;

public record PositionValuationView(
        String id,
        String accountId,
        String accountName,
        String fundCode,
        String fundName,
        BigDecimal shares,
        BigDecimal remainingCost,
        PositionStatus positionStatus,
        ValuationStatus valuationStatus,
        ValuationPriceType priceType,
        BigDecimal unitNav,
        BigDecimal estimatedChangePercent,
        BigDecimal marketValue,
        BigDecimal profit,
        BigDecimal returnPercent,
        LocalDate dataDate,
        Instant observedAt,
        String dataSource) {

    static PositionValuationView from(
            PositionValuationDetails details) {
        var position = details.positionDetails().position();
        var account = details.positionDetails().account();
        var fund = details.positionDetails().fund();
        return new PositionValuationView(
                position.publicId(),
                account.publicId(),
                account.name(),
                fund.code(),
                fund.name(),
                position.shares(),
                position.remainingCost(),
                position.status(),
                details.valuationStatus(),
                details.priceType(),
                details.unitNav(),
                details.estimatedChangePercent(),
                details.marketValue(),
                details.profit(),
                details.returnPercent(),
                details.dataDate(),
                details.observedAt(),
                details.dataSource());
    }
}
