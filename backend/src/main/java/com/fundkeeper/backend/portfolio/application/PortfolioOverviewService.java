package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.domain.SellTransactionSummary;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class PortfolioOverviewService {

    private static final int MONEY_SCALE = 4;
    private static final int RETURN_SCALE = 4;

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionValuationService positionValuationService;
    private final Clock clock;

    public PortfolioOverviewService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            PortfolioRepository portfolioRepository,
            PositionValuationService positionValuationService,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionValuationService = positionValuationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioOverviewDetails get(
            String userPublicId,
            String accountPublicId) {
        PortfolioScope scope = scope(
                userPublicId,
                accountPublicId);
        return calculate(
                scope.positions(),
                portfolioRepository.summarizeSells(
                        scope.user().id(),
                        scope.accountIds(),
                        null));
    }

    @Transactional(readOnly = true)
    public List<FundPortfolioCardDetails> listFunds(
            String userPublicId,
            String accountPublicId) {
        PortfolioScope scope = scope(
                userPublicId,
                accountPublicId);
        Map<Long, SellTransactionSummary> sellSummaries =
                portfolioRepository.summarizeSellsByFund(
                        scope.user().id(),
                        scope.accountIds());
        Map<Long, List<PositionValuationDetails>> byFund =
                scope.positions()
                        .stream()
                        .collect(Collectors.groupingBy(
                                details -> details
                                        .positionDetails()
                                        .fund()
                                        .id(),
                                LinkedHashMap::new,
                                Collectors.toList()));
        return byFund.entrySet()
                .stream()
                .map(entry -> fundCard(
                        entry.getValue(),
                        sellSummaries.getOrDefault(
                                entry.getKey(),
                                emptySellSummary())))
                .sorted(cardOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public FundPortfolioHoldingDetails getFund(
            String userPublicId,
            String fundCode) {
        PortfolioScope scope = scope(userPublicId, null);
        String normalizedFundCode = fundCode == null
                ? ""
                : fundCode.trim();
        List<PositionValuationDetails> positions =
                scope.positions()
                        .stream()
                        .filter(details -> details
                                .positionDetails()
                                .fund()
                                .code()
                                .equals(normalizedFundCode))
                        .toList();
        if (positions.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.POSITION_NOT_FOUND,
                    "当前没有该基金持仓");
        }
        long fundId = positions.getFirst()
                .positionDetails()
                .fund()
                .id();
        SellTransactionSummary totalSellSummary =
                portfolioRepository.summarizeSells(
                        scope.user().id(),
                        scope.accountIds(),
                        fundId);
        Map<Long, SellTransactionSummary> sellSummariesByAccount =
                portfolioRepository.summarizeSellsByAccount(
                        scope.user().id(),
                        scope.accountIds(),
                        fundId);
        List<FundPortfolioAccountDetails> accounts =
                positions.stream()
                        .sorted(Comparator
                                .comparing((PositionValuationDetails details) ->
                                        details.positionDetails()
                                                .account()
                                                .createdAt())
                                .thenComparing(details -> details
                                        .positionDetails()
                                        .account()
                                        .publicId()))
                        .map(details -> new FundPortfolioAccountDetails(
                                details,
                                calculate(
                                        List.of(details),
                                        sellSummariesByAccount.getOrDefault(
                                                details.positionDetails()
                                                        .account()
                                                        .id(),
                                                emptySellSummary()))))
                        .toList();
        return new FundPortfolioHoldingDetails(
                fundCard(positions, totalSellSummary),
                accounts);
    }

    private PortfolioScope scope(
            String userPublicId,
            String accountPublicId) {
        User user = activeUser(userPublicId);
        List<FundAccount> accounts = activeAccounts(
                user.id(),
                accountPublicId);
        Set<Long> accountIds = accounts.stream()
                .map(FundAccount::id)
                .collect(Collectors.toSet());
        List<PositionValuationDetails> positions =
                positionValuationService
                        .list(userPublicId, accountPublicId)
                        .stream()
                        .filter(details -> accountIds.contains(
                                details.positionDetails()
                                        .account()
                                        .id()))
                        .toList();
        return new PortfolioScope(user, accountIds, positions);
    }

    private PortfolioOverviewDetails calculate(
            List<PositionValuationDetails> positions,
            SellTransactionSummary sellSummary) {
        List<PositionValuationDetails> valued = positions.stream()
                .filter(details -> details.marketValue() != null)
                .toList();
        List<PositionValuationDetails> todayValued = valued.stream()
                .filter(details ->
                        details.todayEstimatedProfit() != null)
                .toList();
        BigDecimal totalHoldingCost = sumCosts(positions);
        BigDecimal valuedHoldingCost = sumCosts(valued);
        boolean hasPositions = !positions.isEmpty();
        boolean hasValuation = !valued.isEmpty();
        BigDecimal currentMarketValue = hasPositions && !hasValuation
                ? null
                : sumMarketValues(valued);
        BigDecimal currentHoldingProfit = hasPositions && !hasValuation
                ? null
                : sumHoldingProfits(valued);
        BigDecimal cumulativeProfit = currentHoldingProfit == null
                ? null
                : money(currentHoldingProfit.add(
                        sellSummary.totalRealizedProfit()));
        BigDecimal currentHoldingReturnPercent = percentage(
                currentHoldingProfit,
                currentHoldingProfit == null
                        ? null
                        : valuedHoldingCost);
        BigDecimal returnCostBasis = currentHoldingProfit == null
                ? null
                : money(valuedHoldingCost.add(
                        sellSummary.totalRemovedCost()));
        BigDecimal cumulativeReturnPercent = percentage(
                cumulativeProfit,
                returnCostBasis);
        BigDecimal todayEstimatedProfit = todayValued.isEmpty()
                ? null
                : money(todayValued.stream()
                        .map(PositionValuationDetails::
                                todayEstimatedProfit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        LocalDate holdingStartDate = positions.stream()
                .map(details -> details
                        .positionDetails()
                        .position()
                        .holdingStartDate())
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);

        return new PortfolioOverviewDetails(
                positions.size(),
                valued.size(),
                positions.size() - valued.size(),
                totalHoldingCost,
                valuedHoldingCost,
                currentMarketValue,
                currentHoldingProfit,
                currentHoldingReturnPercent,
                sellSummary.totalRealizedProfit(),
                cumulativeProfit,
                returnCostBasis,
                cumulativeReturnPercent,
                todayEstimatedProfit,
                hasPositions
                        && todayValued.size() == positions.size(),
                sellSummary.confirmedSellCount(),
                sellSummary.openSellCount(),
                containsEstimatedData(positions)
                        || sellSummary.openSellCount() > 0,
                valued.size() == positions.size(),
                aggregateStatus(positions),
                aggregatePriceType(valued),
                oldestDataDate(valued),
                valued.stream()
                        .map(PositionValuationDetails::observedAt)
                        .filter(java.util.Objects::nonNull)
                        .min(java.time.Instant::compareTo)
                        .orElse(null),
                holdingStartDate,
                holdingDays(holdingStartDate));
    }

    private FundPortfolioCardDetails fundCard(
            List<PositionValuationDetails> positions,
            SellTransactionSummary sellSummary) {
        FundDefinition fund = positions.getFirst()
                .positionDetails()
                .fund();
        int accountCount = (int) positions.stream()
                .map(details -> details
                        .positionDetails()
                        .account()
                        .id())
                .distinct()
                .count();
        BigDecimal totalShares = positions.stream()
                .map(details -> details
                        .positionDetails()
                        .position()
                        .shares())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
        return new FundPortfolioCardDetails(
                fund,
                accountCount,
                totalShares,
                calculate(positions, sellSummary));
    }

    private Comparator<FundPortfolioCardDetails> cardOrder() {
        return Comparator
                .comparing(
                        (FundPortfolioCardDetails card) ->
                                card.metrics().currentMarketValue(),
                        Comparator.nullsLast(
                                Comparator.reverseOrder()))
                .thenComparing(card -> card.fund().code());
    }

    private SellTransactionSummary emptySellSummary() {
        return new SellTransactionSummary(
                0,
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                0);
    }

    private User activeUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
    }

    private List<FundAccount> activeAccounts(
            long userId,
            String accountPublicId) {
        if (accountPublicId == null || accountPublicId.isBlank()) {
            return accountRepository.findAllByUserId(userId, false);
        }
        FundAccount account = accountRepository
                .findByPublicIdAndUserId(
                        accountPublicId.trim(),
                        userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
        if (!account.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ARCHIVED,
                    "已归档账户不参与首页汇总");
        }
        return List.of(account);
    }

    private BigDecimal sumCosts(
            List<PositionValuationDetails> positions) {
        return money(positions.stream()
                .map(details -> details
                        .positionDetails()
                        .position()
                        .remainingCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumMarketValues(
            List<PositionValuationDetails> positions) {
        return money(positions.stream()
                .map(PositionValuationDetails::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumHoldingProfits(
            List<PositionValuationDetails> positions) {
        return money(positions.stream()
                .map(PositionValuationDetails::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean containsEstimatedData(
            List<PositionValuationDetails> positions) {
        return positions.stream().anyMatch(details ->
                details.positionDetails().position().status()
                                != PositionStatus.CONFIRMED
                        || details.priceType()
                                == ValuationPriceType.ESTIMATED);
    }

    private ValuationStatus aggregateStatus(
            List<PositionValuationDetails> positions) {
        return positions.stream()
                .map(PositionValuationDetails::valuationStatus)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingInt(this::statusPriority))
                .orElse(null);
    }

    private int statusPriority(ValuationStatus status) {
        return switch (status) {
            case LIVE -> 1;
            case MARKET_CLOSED -> 2;
            case DELAYED -> 3;
            case STALE -> 4;
            case UNAVAILABLE -> 5;
        };
    }

    private ValuationPriceType aggregatePriceType(
            List<PositionValuationDetails> valued) {
        List<ValuationPriceType> types = valued.stream()
                .map(PositionValuationDetails::priceType)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return types.size() == 1 ? types.getFirst() : null;
    }

    private LocalDate oldestDataDate(
            List<PositionValuationDetails> valued) {
        return valued.stream()
                .map(PositionValuationDetails::dataDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private Long holdingDays(LocalDate holdingStartDate) {
        if (holdingStartDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        if (holdingStartDate.isAfter(today)) {
            return null;
        }
        return ChronoUnit.DAYS.between(holdingStartDate, today) + 1;
    }

    private BigDecimal percentage(
            BigDecimal numerator,
            BigDecimal denominator) {
        if (numerator == null
                || denominator == null
                || denominator.signum() == 0) {
            return null;
        }
        return numerator
                .divide(
                        denominator,
                        RETURN_SCALE + 4,
                        RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(RETURN_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record PortfolioScope(
            User user,
            Set<Long> accountIds,
            List<PositionValuationDetails> positions) {
    }
}
