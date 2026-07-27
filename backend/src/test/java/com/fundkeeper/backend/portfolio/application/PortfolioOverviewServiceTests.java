package com.fundkeeper.backend.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.AccountStatus;
import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.auth.domain.UserStatus;
import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.valuation.domain.ValuationStatus;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.domain.SellTransactionSummary;

class PortfolioOverviewServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-07-24T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(
            NOW,
            ZoneId.of("Asia/Shanghai"));

    private UserRepository userRepository;
    private FundAccountRepository accountRepository;
    private FundDataRepository fundDataRepository;
    private PortfolioRepository portfolioRepository;
    private PositionValuationService valuationService;
    private PortfolioOverviewService service;
    private User user;
    private FundAccount account;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accountRepository = mock(FundAccountRepository.class);
        fundDataRepository = mock(FundDataRepository.class);
        portfolioRepository = mock(PortfolioRepository.class);
        valuationService = mock(PositionValuationService.class);
        service = new PortfolioOverviewService(
                userRepository,
                accountRepository,
                fundDataRepository,
                portfolioRepository,
                valuationService,
                CLOCK);
        user = new User(
                1L,
                "user-1",
                "overview@example.com",
                "hash",
                UserStatus.ACTIVE,
                0,
                NOW,
                NOW);
        account = new FundAccount(
                10L,
                "account-1",
                1L,
                "默认账户",
                "默认账户",
                AccountPlatform.OTHER,
                AccountStatus.ACTIVE,
                NOW,
                NOW,
                null);
        when(userRepository.findByPublicId("user-1"))
                .thenReturn(Optional.of(user));
        when(accountRepository.findAllByUserId(1L, false))
                .thenReturn(List.of(account));
    }

    @Test
    void combinesIntradayHoldingAndConfirmedRealizedProfit() {
        PositionValuationDetails valuation = intradayValuation(
                position(100L, 1000L, "300", "600", "2026-07-01"),
                "660",
                "60",
                "60");
        when(valuationService.list("user-1", null))
                .thenReturn(List.of(valuation));
        when(portfolioRepository.summarizeSells(
                        1L,
                        Set.of(10L),
                        null))
                .thenReturn(summary("390", "400", "-10", 0));

        PortfolioOverviewDetails result = service.get("user-1", null);

        assertThat(result.positionCount()).isEqualTo(1);
        assertThat(result.totalHoldingCost())
                .isEqualByComparingTo("600.0000");
        assertThat(result.currentMarketValue())
                .isEqualByComparingTo("660.0000");
        assertThat(result.currentHoldingProfit())
                .isEqualByComparingTo("60.0000");
        assertThat(result.currentHoldingReturnPercent())
                .isEqualByComparingTo("10.0000");
        assertThat(result.realizedProfit())
                .isEqualByComparingTo("-10.0000");
        assertThat(result.cumulativeProfit())
                .isEqualByComparingTo("50.0000");
        assertThat(result.returnCostBasis())
                .isEqualByComparingTo("1000.0000");
        assertThat(result.cumulativeReturnPercent())
                .isEqualByComparingTo("5.0000");
        assertThat(result.todayEstimatedProfit())
                .isEqualByComparingTo("60.0000");
        assertThat(result.todayEstimateComplete()).isTrue();
        assertThat(result.valuationComplete()).isTrue();
        assertThat(result.containsEstimatedData()).isTrue();
        assertThat(result.valuationStatus())
                .isEqualTo(ValuationStatus.LIVE);
        assertThat(result.priceType())
                .isEqualTo(ValuationPriceType.ESTIMATED);
        assertThat(result.holdingDays()).isEqualTo(24L);
    }

    @Test
    void partialValuationUsesOnlyIncludedCostBasisAndMarksIncomplete() {
        PositionValuationDetails valued = officialValuation(
                position(100L, 1000L, "10", "90", "2026-06-01"),
                "100",
                "10");
        PositionValuationDetails missing = missingValuation(
                position(101L, 1001L, "5", "50", "2026-07-01"));
        when(valuationService.list("user-1", null))
                .thenReturn(List.of(valued, missing));
        when(portfolioRepository.summarizeSells(
                        1L,
                        Set.of(10L),
                        null))
                .thenReturn(summary("0", "0", "0", 0));

        PortfolioOverviewDetails result = service.get("user-1", null);

        assertThat(result.totalHoldingCost())
                .isEqualByComparingTo("140.0000");
        assertThat(result.valuedHoldingCost())
                .isEqualByComparingTo("90.0000");
        assertThat(result.currentMarketValue())
                .isEqualByComparingTo("100.0000");
        assertThat(result.currentHoldingProfit())
                .isEqualByComparingTo("10.0000");
        assertThat(result.cumulativeReturnPercent())
                .isEqualByComparingTo("11.1111");
        assertThat(result.valuedPositionCount()).isEqualTo(1);
        assertThat(result.missingValuationCount()).isEqualTo(1);
        assertThat(result.valuationComplete()).isFalse();
        assertThat(result.todayEstimatedProfit()).isNull();
        assertThat(result.valuationStatus())
                .isEqualTo(ValuationStatus.UNAVAILABLE);
        assertThat(result.priceType())
                .isEqualTo(ValuationPriceType.OFFICIAL);
    }

    @Test
    void missingAllPricesDoesNotTurnUnknownProfitIntoZero() {
        PositionValuationDetails missing = missingValuation(
                position(100L, 1000L, "5", "50", "2026-07-01"));
        when(valuationService.list("user-1", null))
                .thenReturn(List.of(missing));
        when(portfolioRepository.summarizeSells(
                        1L,
                        Set.of(10L),
                        null))
                .thenReturn(summary("27", "20", "7", 1));

        PortfolioOverviewDetails result = service.get("user-1", null);

        assertThat(result.totalHoldingCost())
                .isEqualByComparingTo("50.0000");
        assertThat(result.currentMarketValue()).isNull();
        assertThat(result.currentHoldingProfit()).isNull();
        assertThat(result.cumulativeProfit()).isNull();
        assertThat(result.cumulativeReturnPercent()).isNull();
        assertThat(result.realizedProfit())
                .isEqualByComparingTo("7.0000");
        assertThat(result.openSellCount()).isEqualTo(1);
        assertThat(result.containsEstimatedData()).isTrue();
    }

    @Test
    void groupsSameFundAcrossAccountsAndSortsMissingPricesLast() {
        FundAccount secondAccount = new FundAccount(
                11L,
                "account-2",
                1L,
                "银行卡",
                "银行卡",
                AccountPlatform.BANK,
                AccountStatus.ACTIVE,
                NOW,
                NOW,
                null);
        when(accountRepository.findAllByUserId(1L, false))
                .thenReturn(List.of(account, secondAccount));
        PositionValuationDetails first = intradayValuation(
                position(
                        100L,
                        1000L,
                        "300",
                        "600",
                        "2026-07-01",
                        account),
                "660",
                "60",
                "60");
        PositionValuationDetails second = intradayValuation(
                position(
                        101L,
                        1000L,
                        "150",
                        "300",
                        "2026-07-10",
                        secondAccount),
                "330",
                "30",
                "30");
        PositionValuationDetails missing = missingValuation(
                position(
                        102L,
                        1001L,
                        "5",
                        "50",
                        "2026-07-20",
                        account));
        when(valuationService.list("user-1", null))
                .thenReturn(List.of(first, missing, second));
        when(portfolioRepository.summarizeSellsByFund(
                        1L,
                        Set.of(10L, 11L)))
                .thenReturn(Map.of(
                        1000L,
                        summary("390", "400", "-10", 0)));
        when(portfolioRepository.findOpenTransactions(
                        1L,
                        Set.of(10L, 11L)))
                .thenReturn(List.of());
        when(fundDataRepository.findFundsByIds(
                        Set.of(1000L, 1001L)))
                .thenReturn(List.of(
                        first.positionDetails().fund(),
                        missing.positionDetails().fund()));

        List<FundPortfolioCardDetails> cards =
                service.listFunds("user-1", null);

        assertThat(cards).hasSize(2);
        FundPortfolioCardDetails aggregated = cards.getFirst();
        assertThat(aggregated.fund().code()).isEqualTo("000001");
        assertThat(aggregated.accountCount()).isEqualTo(2);
        assertThat(aggregated.totalShares())
                .isEqualByComparingTo("450.00000000");
        assertThat(aggregated.metrics().totalHoldingCost())
                .isEqualByComparingTo("900.0000");
        assertThat(aggregated.metrics().currentMarketValue())
                .isEqualByComparingTo("990.0000");
        assertThat(aggregated.metrics().currentHoldingProfit())
                .isEqualByComparingTo("90.0000");
        assertThat(aggregated.metrics()
                        .currentHoldingReturnPercent())
                .isEqualByComparingTo("10.0000");
        assertThat(aggregated.metrics().realizedProfit())
                .isEqualByComparingTo("-10.0000");
        assertThat(aggregated.metrics().cumulativeProfit())
                .isEqualByComparingTo("80.0000");
        assertThat(aggregated.metrics().todayEstimatedProfit())
                .isEqualByComparingTo("90.0000");
        assertThat(aggregated.metrics().holdingStartDate())
                .isEqualTo(LocalDate.of(2026, 7, 1));

        FundPortfolioCardDetails unavailable = cards.get(1);
        assertThat(unavailable.fund().code()).isEqualTo("000002");
        assertThat(unavailable.metrics().currentMarketValue()).isNull();
        assertThat(unavailable.metrics().valuationComplete()).isFalse();
    }

    private PositionValuationDetails intradayValuation(
            PositionDetails position,
            String marketValue,
            String profit,
            String todayProfit) {
        return new PositionValuationDetails(
                position,
                ValuationStatus.LIVE,
                ValuationPriceType.ESTIMATED,
                new BigDecimal("2.2"),
                new BigDecimal("10"),
                LocalDate.of(2026, 7, 23),
                new BigDecimal("2"),
                new BigDecimal(marketValue),
                new BigDecimal(profit),
                new BigDecimal(todayProfit),
                new BigDecimal("10"),
                LocalDate.of(2026, 7, 24),
                NOW.minusSeconds(60),
                "test");
    }

    private PositionValuationDetails officialValuation(
            PositionDetails position,
            String marketValue,
            String profit) {
        return new PositionValuationDetails(
                position,
                ValuationStatus.MARKET_CLOSED,
                ValuationPriceType.OFFICIAL,
                BigDecimal.TEN,
                null,
                null,
                null,
                new BigDecimal(marketValue),
                new BigDecimal(profit),
                null,
                new BigDecimal("11.1111"),
                LocalDate.of(2026, 7, 23),
                null,
                "test");
    }

    private PositionValuationDetails missingValuation(
            PositionDetails position) {
        return new PositionValuationDetails(
                position,
                ValuationStatus.UNAVAILABLE,
                null,
                null,
                null,
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

    private PositionDetails position(
            long id,
            long fundId,
            String shares,
            String cost,
            String holdingStartDate) {
        return position(
                id,
                fundId,
                shares,
                cost,
                holdingStartDate,
                account);
    }

    private PositionDetails position(
            long id,
            long fundId,
            String shares,
            String cost,
            String holdingStartDate,
            FundAccount positionAccount) {
        FundPosition position = new FundPosition(
                id,
                "position-" + id,
                1L,
                positionAccount.id(),
                fundId,
                new BigDecimal(shares),
                new BigDecimal(cost),
                new BigDecimal(cost).divide(
                        new BigDecimal(shares)),
                PositionStatus.CONFIRMED,
                LocalDate.parse(holdingStartDate),
                NOW,
                NOW);
        FundDefinition fund = new FundDefinition(
                fundId,
                String.format("%06d", fundId - 999),
                "测试基金",
                FundCategory.MIXED,
                "CNY",
                true,
                1,
                "test",
                NOW,
                NOW);
        return new PositionDetails(position, positionAccount, fund);
    }

    private SellTransactionSummary summary(
            String actual,
            String removed,
            String profit,
            long openCount) {
        return new SellTransactionSummary(
                actual.equals("0") ? 0 : 1,
                new BigDecimal(actual),
                new BigDecimal(removed),
                new BigDecimal(profit),
                openCount);
    }
}
