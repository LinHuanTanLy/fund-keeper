package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
        return portfolioService
                .listPositions(userPublicId, accountPublicId)
                .stream()
                .map(this::value)
                .toList();
    }

    private PositionValuationDetails value(
            PositionDetails details) {
        var quote = valuationQueryService.quote(
                details.fund().code());
        if (quote.valuation().isPresent()
                && quote.status() != ValuationStatus.STALE) {
            IntradayValuation valuation =
                    quote.valuation().get();
            return details(
                    details,
                    quote.status(),
                    ValuationPriceType.ESTIMATED,
                    valuation.estimatedNav(),
                    valuation.estimatedChangePercent(),
                    valuation.valuationDate(),
                    valuation.fetchedAt(),
                    valuation.dataSource());
        }

        var official = fundDataRepository
                .findLatestOfficialNav(details.fund().id());
        if (official.isPresent()) {
            OfficialNav nav = official.get();
            return details(
                    details,
                    quote.status(),
                    ValuationPriceType.OFFICIAL,
                    nav.unitNav(),
                    null,
                    nav.navDate(),
                    null,
                    nav.dataSource());
        }
        return new PositionValuationDetails(
                details,
                quote.status(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private PositionValuationDetails details(
            PositionDetails details,
            ValuationStatus status,
            ValuationPriceType priceType,
            BigDecimal unitNav,
            BigDecimal estimatedChangePercent,
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
                marketValue,
                profit,
                returnPercent,
                dataDate,
                observedAt,
                dataSource);
    }
}
