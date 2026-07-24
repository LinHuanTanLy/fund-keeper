package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.application.FundCatalogService;
import com.fundkeeper.backend.fund.domain.FeeCalculationMethod;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.fund.domain.PurchaseFeeRule;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Component
public class BuyTransactionPlanner {

    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final FundCatalogService fundCatalogService;
    private final FundDataRepository fundDataRepository;
    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;

    public BuyTransactionPlanner(
            FundCatalogService fundCatalogService,
            FundDataRepository fundDataRepository,
            TradingCalendarService tradingCalendarService,
            Clock clock) {
        this.fundCatalogService = fundCatalogService;
        this.fundDataRepository = fundDataRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.clock = clock;
    }

    public BuyTransactionCommand normalize(
            BuyTransactionCommand command) {
        BigDecimal amount = command.amount()
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal confirmedShares = command.confirmedShares() == null
                ? null
                : command.confirmedShares().setScale(
                        SHARE_SCALE,
                        RoundingMode.HALF_UP);
        String note = command.note() == null
                ? null
                : command.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        return new BuyTransactionCommand(
                command.requestId().trim(),
                command.accountPublicId().trim(),
                command.fundCode().trim(),
                amount,
                command.submittedDate(),
                command.submittedPeriod(),
                confirmedShares,
                command.confirmedDate(),
                note);
    }

    public BuyTransactionPlan planNormalized(
            BuyTransactionCommand command) {
        FundDefinition fund = fundCatalogService.getSupportedFund(
                command.fundCode());
        validateDates(command);
        LocalDate effectiveDate =
                tradingCalendarService.effectiveTradeDate(
                        command.submittedDate(),
                        command.submittedPeriod());
        validateConfirmedDate(command, effectiveDate);

        Calculation calculation = calculate(
                fund,
                command,
                effectiveDate);
        LocalDate holdingStartDate = holdingStartDate(
                fund,
                command,
                effectiveDate,
                calculation.status());
        return new BuyTransactionPlan(
                command,
                fund,
                calculation.status(),
                calculation.feeAmount(),
                calculation.netAmount(),
                calculation.shares(),
                effectiveDate,
                holdingStartDate,
                calculation.navDate(),
                calculation.unitNav(),
                calculation.navSource(),
                calculation.feeRate(),
                calculation.feeSource(),
                calculation.pendingReason());
    }

    private Calculation calculate(
            FundDefinition fund,
            BuyTransactionCommand command,
            LocalDate effectiveDate) {
        if (command.confirmedShares() != null) {
            return new Calculation(
                    TransactionStatus.CONFIRMED,
                    null,
                    null,
                    command.confirmedShares(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        var nav = fundDataRepository.findOfficialNav(
                fund.id(),
                effectiveDate);
        var feeRule = fundDataRepository.findPurchaseFeeRule(
                fund.id(),
                command.amount(),
                effectiveDate);
        if (nav.isEmpty() || feeRule.isEmpty()) {
            return pending(nav.isEmpty(), feeRule.isEmpty());
        }
        return estimated(command.amount(), nav.get(), feeRule.get());
    }

    private Calculation estimated(
            BigDecimal grossAmount,
            OfficialNav nav,
            PurchaseFeeRule feeRule) {
        if (nav.unitNav().signum() <= 0
                || feeRule.feeRate().signum() < 0
                || feeRule.calculationMethod()
                        != FeeCalculationMethod.GROSS_INCLUDES_FEE) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "基金净值或费率数据不正确");
        }
        BigDecimal netAmount = grossAmount.divide(
                        BigDecimal.ONE.add(feeRule.feeRate()),
                        MONEY_SCALE,
                        RoundingMode.HALF_UP);
        BigDecimal feeAmount = grossAmount.subtract(netAmount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal shares = netAmount.divide(
                nav.unitNav(),
                SHARE_SCALE,
                RoundingMode.HALF_UP);
        return new Calculation(
                TransactionStatus.ESTIMATED,
                feeAmount,
                netAmount,
                shares,
                nav.navDate(),
                nav.unitNav(),
                nav.dataSource(),
                feeRule.feeRate(),
                feeRule.dataSource(),
                null);
    }

    private Calculation pending(
            boolean navMissing,
            boolean feeMissing) {
        PendingReason reason;
        if (navMissing && feeMissing) {
            reason = PendingReason.NAV_AND_FEE_UNAVAILABLE;
        } else if (navMissing) {
            reason = PendingReason.OFFICIAL_NAV_UNAVAILABLE;
        } else {
            reason = PendingReason.FEE_RULE_UNAVAILABLE;
        }
        return new Calculation(
                TransactionStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reason);
    }

    private void validateDates(BuyTransactionCommand command) {
        LocalDate today = LocalDate.now(clock);
        if (command.submittedDate().isAfter(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "提交日期不能晚于当前日期");
        }
        if (command.confirmedDate() != null) {
            if (command.confirmedShares() == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_TRANSACTION_DATE,
                        "填写确认日期时必须同时填写平台确认份额");
            }
            if (command.confirmedDate().isAfter(today)) {
                throw new BusinessException(
                        ErrorCode.INVALID_TRANSACTION_DATE,
                        "确认日期不能晚于当前日期");
            }
        }
    }

    private void validateConfirmedDate(
            BuyTransactionCommand command,
            LocalDate effectiveDate) {
        if (command.confirmedDate() != null
                && command.confirmedDate().isBefore(effectiveDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "确认日期不能早于有效交易日");
        }
    }

    private LocalDate holdingStartDate(
            FundDefinition fund,
            BuyTransactionCommand command,
            LocalDate effectiveDate,
            TransactionStatus status) {
        if (status == TransactionStatus.PENDING) {
            return null;
        }
        if (status == TransactionStatus.CONFIRMED) {
            return command.confirmedDate();
        }
        return tradingCalendarService
                .estimatedConfirmationDate(
                        effectiveDate,
                        fund.confirmationDelayTradingDays())
                .orElse(null);
    }

    private record Calculation(
            TransactionStatus status,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            BigDecimal shares,
            LocalDate navDate,
            BigDecimal unitNav,
            String navSource,
            BigDecimal feeRate,
            String feeSource,
            PendingReason pendingReason) {
    }
}
