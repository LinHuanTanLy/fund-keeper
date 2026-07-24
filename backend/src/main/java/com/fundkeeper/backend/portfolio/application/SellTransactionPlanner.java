package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.PositionSaleImpact;
import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Component
public class SellTransactionPlanner {

    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final FundDataRepository fundDataRepository;
    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;

    public SellTransactionPlanner(
            FundDataRepository fundDataRepository,
            TradingCalendarService tradingCalendarService,
            Clock clock) {
        this.fundDataRepository = fundDataRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.clock = clock;
    }

    public SellTransactionCommand normalize(
            SellTransactionCommand command) {
        return new SellTransactionCommand(
                command.requestId().trim(),
                command.accountPublicId().trim(),
                command.fundCode().trim(),
                command.sellMode(),
                money(command.expectedAmount()),
                money(command.actualReceivedAmount()),
                command.submittedDate(),
                command.submittedPeriod(),
                shares(command.confirmedShares()),
                command.confirmedDate(),
                note(command.note()));
    }

    public SellTransactionPlan planNormalized(
            SellTransactionCommand command,
            FundDefinition fund,
            FundPosition position) {
        validateFields(command);
        validateDates(command);
        LocalDate effectiveDate =
                tradingCalendarService.effectiveTradeDate(
                        command.submittedDate(),
                        command.submittedPeriod());
        validateConfirmedDate(command, effectiveDate);

        OfficialNav nav = fundDataRepository.findOfficialNav(
                        fund.id(),
                        effectiveDate)
                .orElse(null);
        if (nav != null && nav.unitNav().signum() <= 0) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "基金净值数据不正确");
        }
        return command.sellMode() == SellMode.FULL
                ? full(command, fund, position, effectiveDate, nav)
                : partial(command, fund, position, effectiveDate, nav);
    }

    private SellTransactionPlan full(
            SellTransactionCommand command,
            FundDefinition fund,
            FundPosition position,
            LocalDate effectiveDate,
            OfficialNav nav) {
        if (command.confirmedShares() != null
                && command.confirmedShares()
                        .compareTo(position.shares()) != 0) {
            throw new BusinessException(
                    ErrorCode.SELL_SHARES_EXCEED_POSITION,
                    "全部卖出的确认份额必须等于当前持仓份额");
        }
        if (command.actualReceivedAmount() == null) {
            return plan(
                    command,
                    fund,
                    TransactionStatus.PENDING,
                    command.expectedAmount() == null
                            ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                            : command.expectedAmount(),
                    position.shares(),
                    effectiveDate,
                    nav,
                    PendingReason.SELL_CONFIRMATION_REQUIRED,
                    null);
        }
        PositionSaleImpact impact = position.saleImpact(
                position.shares(),
                command.actualReceivedAmount());
        return plan(
                command,
                fund,
                TransactionStatus.CONFIRMED,
                command.actualReceivedAmount(),
                position.shares(),
                effectiveDate,
                nav,
                null,
                impact);
    }

    private SellTransactionPlan partial(
            SellTransactionCommand command,
            FundDefinition fund,
            FundPosition position,
            LocalDate effectiveDate,
            OfficialNav nav) {
        if (nav != null) {
            BigDecimal marketValue = position.shares()
                    .multiply(nav.unitNav())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (command.expectedAmount()
                    .compareTo(marketValue) > 0) {
                throw new BusinessException(
                        ErrorCode.SELL_AMOUNT_EXCEEDS_POSITION,
                        "预计到账金额超过当前持仓市值，请检查金额或使用全部卖出");
            }
        }

        BigDecimal soldShares = command.confirmedShares();
        if (soldShares == null && nav != null) {
            soldShares = command.expectedAmount().divide(
                    nav.unitNav(),
                    SHARE_SCALE,
                    RoundingMode.HALF_UP);
        }
        if (soldShares == null) {
            return plan(
                    command,
                    fund,
                    TransactionStatus.PENDING,
                    command.actualReceivedAmount() == null
                            ? command.expectedAmount()
                            : command.actualReceivedAmount(),
                    null,
                    effectiveDate,
                    null,
                    PendingReason.OFFICIAL_NAV_UNAVAILABLE,
                    null);
        }
        if (soldShares.compareTo(position.shares()) >= 0) {
            throw new BusinessException(
                    ErrorCode.SELL_SHARES_EXCEED_POSITION,
                    "部分卖出份额必须小于当前持仓份额，请改用全部卖出");
        }

        BigDecimal proceeds =
                command.actualReceivedAmount() == null
                        ? command.expectedAmount()
                        : command.actualReceivedAmount();
        TransactionStatus status =
                command.actualReceivedAmount() != null
                                && command.confirmedShares() != null
                        ? TransactionStatus.CONFIRMED
                        : TransactionStatus.ESTIMATED;
        PositionSaleImpact impact = position.saleImpact(
                soldShares,
                proceeds);
        return plan(
                command,
                fund,
                status,
                proceeds,
                soldShares,
                effectiveDate,
                nav,
                null,
                impact);
    }

    private SellTransactionPlan plan(
            SellTransactionCommand command,
            FundDefinition fund,
            TransactionStatus status,
            BigDecimal amount,
            BigDecimal soldShares,
            LocalDate effectiveDate,
            OfficialNav nav,
            PendingReason pendingReason,
            PositionSaleImpact impact) {
        return new SellTransactionPlan(
                command,
                fund,
                status,
                amount,
                soldShares,
                effectiveDate,
                nav == null ? null : nav.navDate(),
                nav == null ? null : nav.unitNav(),
                nav == null ? null : nav.dataSource(),
                pendingReason,
                impact);
    }

    private void validateFields(SellTransactionCommand command) {
        if (command.sellMode() == SellMode.PARTIAL
                && command.expectedAmount() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "部分卖出必须填写预计到账金额");
        }
        if (command.confirmedDate() != null
                && command.confirmedShares() == null
                && command.actualReceivedAmount() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "填写确认日期时必须同时填写确认份额或实际到账金额");
        }
    }

    private void validateDates(SellTransactionCommand command) {
        LocalDate today = LocalDate.now(clock);
        if (command.submittedDate().isAfter(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "提交日期不能晚于当前日期");
        }
        if (command.confirmedDate() != null
                && command.confirmedDate().isAfter(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "确认日期不能晚于当前日期");
        }
    }

    private void validateConfirmedDate(
            SellTransactionCommand command,
            LocalDate effectiveDate) {
        if (command.confirmedDate() != null
                && command.confirmedDate().isBefore(effectiveDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "确认日期不能早于有效交易日");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal shares(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private String note(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
