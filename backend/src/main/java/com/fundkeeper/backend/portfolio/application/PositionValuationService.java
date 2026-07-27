package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.fund.valuation.application.ValuationQueryService;
import com.fundkeeper.backend.fund.valuation.domain.IntradayValuation;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;

@Service
public class PositionValuationService {

    private static final int MONEY_SCALE = 4;
    private static final int RETURN_SCALE = 4;

    private final PortfolioService portfolioService;
    private final FundDataRepository fundDataRepository;
    private final ValuationQueryService valuationQueryService;

    public PositionValuationService(
            PortfolioService portfolioService,
            FundDataRepository fundDataRepository,
            ValuationQueryService valuationQueryService) {
        this.portfolioService = portfolioService;
        this.fundDataRepository = fundDataRepository;
        this.valuationQueryService = valuationQueryService;
    }

    @Transactional(readOnly = true)
    public List<PositionValuationDetails> list(
            String userPublicId,
            String accountPublicId) {
        List<PositionDetails> positions = portfolioService
                .listPositions(userPublicId, accountPublicId);
        Map<Long, PositionPrice> prices = new HashMap<>();
        return positions
                .stream()
                .map(details -> value(
                        details,
                        prices.computeIfAbsent(
                                details.fund().id(),
                                ignored -> price(details))))
                .toList();
    }

    private PositionPrice price(
            PositionDetails details) {
        var quote = valuationQueryService.quote(
                details.fund().code());
        if (quote.valuation().isPresent()
                && quote.status() != ValuationStatus.STALE) {
            IntradayValuation valuation =
                    quote.valuation().get();
            return new PositionPrice(
                    quote.status(),
                    ValuationPriceType.ESTIMATED,
                    valuation.estimatedNav(),
                    valuation.estimatedChangePercent(),
                    valuation.baseNavDate(),
                    valuation.baseNav(),
                    valuation.valuationDate(),
                    valuation.fetchedAt(),
                    valuation.dataSource());
        }

        var official = fundDataRepository
                .findLatestOfficialNav(details.fund().id());
        if (official.isPresent()) {
            OfficialNav nav = official.get();
            return new PositionPrice(
                    quote.status(),
                    ValuationPriceType.OFFICIAL,
                    nav.unitNav(),
                    null,
                    null,
                    null,
                    nav.navDate(),
                    null,
                    nav.dataSource());
        }
        return new PositionPrice(
                quote.status(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private PositionValuationDetails value(
            PositionDetails details,
            PositionPrice price) {
        if (price.unitNav() != null) {
            return details(
                    details,
                    price.status(),
                    price.priceType(),
                    price.unitNav(),
                    price.estimatedChangePercent(),
                    price.baseNavDate(),
                    price.baseNav(),
                    price.dataDate(),
                    price.observedAt(),
                    price.dataSource());
        }
        return new PositionValuationDetails(
                details,
                price.status(),
                price.priceType(),
                price.unitNav(),
                price.estimatedChangePercent(),
                price.baseNavDate(),
                price.baseNav(),
                null,
                null,
                null,
                null,
                price.dataDate(),
                price.observedAt(),
                price.dataSource());
    }

    private PositionValuationDetails details(
            PositionDetails details,
            ValuationStatus status,
            ValuationPriceType priceType,
            BigDecimal unitNav,
            BigDecimal estimatedChangePercent,
            java.time.LocalDate baseNavDate,
            BigDecimal baseNav,
            java.time.LocalDate dataDate,
            java.time.Instant observedAt,
            String dataSource) {
        BigDecimal marketValue = details.position()
                .shares()
                .multiply(unitNav)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal profit = marketValue
                .subtract(details.position().remainingCost())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal todayEstimatedProfit = baseNav == null
                ? null
                : details.position()
                        .shares()
                        .multiply(unitNav.subtract(baseNav))
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal returnPercent =
                details.position().remainingCost().signum() == 0
                        ? null
                        : profit
                                .divide(
                                        details.position().remainingCost(),
                                        RETURN_SCALE + 4,
                                        RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(
                                        RETURN_SCALE,
                                        RoundingMode.HALF_UP);
        return new PositionValuationDetails(
                details,
                status,
                priceType,
                unitNav,
                estimatedChangePercent,
                baseNavDate,
                baseNav,
                marketValue,
                profit,
                todayEstimatedProfit,
                returnPercent,
                dataDate,
                observedAt,
                dataSource);
    }

    private record PositionPrice(
            ValuationStatus status,
            ValuationPriceType priceType,
            BigDecimal unitNav,
            BigDecimal estimatedChangePercent,
            java.time.LocalDate baseNavDate,
            BigDecimal baseNav,
            java.time.LocalDate dataDate,
            java.time.Instant observedAt,
            String dataSource) {
    }
}
