package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class BuyTransactionStateService {

    private static final int SHARE_SCALE = 8;

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final FundDataRepository fundDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotBoundaryRepository boundaryRepository;
    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;

    public BuyTransactionStateService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            SnapshotBoundaryRepository boundaryRepository,
            TradingCalendarService tradingCalendarService,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.boundaryRepository = boundaryRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.clock = clock;
    }

    @Transactional
    public TransactionDetails confirm(
            String userPublicId,
            String transactionPublicId,
            BuyConfirmationCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        BuyConfirmationCommand command = normalize(rawCommand);
        FundTransaction transaction = lockedBuy(
                user.id(),
                transactionPublicId);
        validateConfirmationDate(
                command.confirmedDate(),
                transaction.effectiveTradeDate());
        if (transaction.status() == TransactionStatus.CONFIRMED) {
            if (sameConfirmation(transaction, command)) {
                return details(user.id(), transaction);
            }
            throw stateConflict(
                    "买入交易已经确认，不能覆盖确认结果");
        }
        ensureConfirmable(transaction);

        FundAccount account = lockedActiveAccount(
                user.id(),
                transaction.accountId());
        if (portfolioRepository.existsOpenSell(
                user.id(),
                account.id(),
                transaction.fundId())) {
            throw new BusinessException(
                    ErrorCode.SELL_ALREADY_OPEN,
                    "该基金存在待确认或待校准卖出，处理完成前不能确认买入");
        }
        validateSnapshotBoundary(
                user.id(),
                account.id(),
                transaction.effectiveTradeDate());
        ensureNoLaterPositionMutation(user.id(), transaction);

        Instant now = clock.instant();
        FundPosition current = portfolioRepository
                .findPositionByAccountIdAndFundId(
                        account.id(),
                        transaction.fundId())
                .filter(position -> position.userId() == user.id())
                .orElse(null);
        FundPosition calibrated;
        FundPosition positionBefore;
        if (transaction.status() == TransactionStatus.ESTIMATED) {
            ensurePositionSnapshot(transaction);
            if (current == null) {
                throw stateConflict(
                        "估算买入对应的持仓不存在，不能自动校准");
            }
            verifyEstimatedPosition(transaction, current);
            calibrated = calibratedPosition(
                    transaction,
                    current,
                    command,
                    now);
            positionBefore = null;
        } else {
            positionBefore = current;
            calibrated = current == null
                    ? FundPosition.fromBuy(
                            user.id(),
                            account.id(),
                            transaction.fundId(),
                            command.confirmedShares(),
                            transaction.grossAmount(),
                            TransactionStatus.CONFIRMED,
                            command.confirmedDate(),
                            now)
                    : current.applyBuy(
                            command.confirmedShares(),
                            transaction.grossAmount(),
                            TransactionStatus.CONFIRMED,
                            command.confirmedDate(),
                            now);
        }
        portfolioRepository.savePosition(calibrated);
        FundTransaction confirmed = portfolioRepository
                .updateTransaction(transaction.confirmBuy(
                        command.confirmedShares(),
                        command.confirmedDate(),
                        positionBefore,
                        now));
        return details(user.id(), confirmed);
    }

    @Transactional
    public TransactionDetails cancel(
            String userPublicId,
            String transactionPublicId,
            String rawReason) {
        User user = activeUserForUpdate(userPublicId);
        FundTransaction transaction = lockedBuy(
                user.id(),
                transactionPublicId);
        if (transaction.status() == TransactionStatus.CANCELLED) {
            return details(user.id(), transaction);
        }
        ensureCancellable(transaction);
        Instant now = clock.instant();
        if (transaction.status() == TransactionStatus.ESTIMATED) {
            ensurePositionSnapshot(transaction);
            if (portfolioRepository.existsOpenSell(
                    user.id(),
                    transaction.accountId(),
                    transaction.fundId())) {
                throw new BusinessException(
                        ErrorCode.SELL_ALREADY_OPEN,
                        "该基金存在待确认或待校准卖出，处理完成前不能撤销买入");
            }
            validateSnapshotBoundary(
                    user.id(),
                    transaction.accountId(),
                    transaction.effectiveTradeDate());
            ensureNoLaterPositionMutation(user.id(), transaction);
            FundPosition current = currentPosition(
                    user.id(),
                    transaction);
            verifyEstimatedPosition(transaction, current);
            restorePositionBeforeBuy(
                    transaction,
                    current,
                    now);
        }
        FundTransaction cancelled = portfolioRepository
                .updateTransaction(transaction.cancelBuy(
                        normalizeReason(rawReason),
                        now));
        return details(user.id(), cancelled);
    }

    private BuyConfirmationCommand normalize(
            BuyConfirmationCommand command) {
        return new BuyConfirmationCommand(
                command.confirmedShares().setScale(
                        SHARE_SCALE,
                        RoundingMode.HALF_UP),
                command.confirmedDate());
    }

    private FundTransaction lockedBuy(
            long userId,
            String transactionPublicId) {
        FundTransaction transaction = portfolioRepository
                .findTransactionByPublicIdAndUserIdForUpdate(
                        transactionPublicId,
                        userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        if (transaction.type() != TransactionType.BUY) {
            throw stateConflict("只有买入交易可以执行该操作");
        }
        return transaction;
    }

    private void ensureConfirmable(FundTransaction transaction) {
        if (transaction.status() != TransactionStatus.PENDING
                && transaction.status()
                        != TransactionStatus.ESTIMATED) {
            throw stateConflict("当前买入状态不允许确认");
        }
    }

    private void ensureCancellable(FundTransaction transaction) {
        if (transaction.status() != TransactionStatus.PENDING
                && transaction.status()
                        != TransactionStatus.ESTIMATED) {
            throw stateConflict("当前买入状态不允许撤销");
        }
    }

    private void ensurePositionSnapshot(
            FundTransaction transaction) {
        if (!transaction.hasPositionSnapshot()) {
            throw stateConflict(
                    "该历史估算买入缺少恢复快照，不能自动处理，请执行历史校准");
        }
    }

    private void ensureNoLaterPositionMutation(
            long userId,
            FundTransaction transaction) {
        if (portfolioRepository
                .existsPositionAffectingTransactionAfter(
                        userId,
                        transaction.accountId(),
                        transaction.fundId(),
                        transaction.id())) {
            throw stateConflict(
                    "该买入之后已有其他持仓交易，不能局部修改，请执行历史重建");
        }
    }

    private FundPosition currentPosition(
            long userId,
            FundTransaction transaction) {
        return portfolioRepository
                .findPositionByAccountIdAndFundId(
                        transaction.accountId(),
                        transaction.fundId())
                .filter(position -> position.userId() == userId)
                .orElseThrow(() -> stateConflict(
                        "估算买入对应的持仓不存在，不能自动处理"));
    }

    private void verifyEstimatedPosition(
            FundTransaction transaction,
            FundPosition current) {
        if (transaction.shares() == null) {
            throw stateConflict(
                    "估算买入份额不完整，不能自动处理");
        }
        BigDecimal expectedShares = transaction
                .positionSharesBefore()
                .add(transaction.shares());
        BigDecimal expectedCost = transaction
                .positionCostBefore()
                .add(transaction.grossAmount());
        PositionStatus expectedStatus =
                transaction.positionStatusBefore()
                                == PositionStatus.NEEDS_CALIBRATION
                        ? PositionStatus.NEEDS_CALIBRATION
                        : PositionStatus.ESTIMATED;
        LocalDate estimatedHoldingStart =
                estimatedHoldingStart(transaction);
        LocalDate expectedHoldingStart = earliest(
                transaction.positionHoldingStartDateBefore(),
                estimatedHoldingStart);
        if (!sameNumber(current.shares(), expectedShares)
                || !sameNumber(
                        current.remainingCost(),
                        expectedCost)
                || current.status() != expectedStatus
                || !Objects.equals(
                        current.holdingStartDate(),
                        expectedHoldingStart)) {
            throw stateConflict(
                    "持仓在估算买入后已发生变化，请执行历史校准");
        }
    }

    private LocalDate estimatedHoldingStart(
            FundTransaction transaction) {
        FundDefinition fund = fundDataRepository
                .findFundById(transaction.fundId())
                .orElseThrow(() -> stateConflict(
                        "基金资料不存在，不能校准估算买入"));
        return tradingCalendarService
                .estimatedConfirmationDate(
                        transaction.effectiveTradeDate(),
                        fund.confirmationDelayTradingDays())
                .orElse(null);
    }

    private FundPosition calibratedPosition(
            FundTransaction transaction,
            FundPosition current,
            BuyConfirmationCommand command,
            Instant now) {
        if (transaction.positionWasEmptyBefore()) {
            return current.applySnapshot(
                    command.confirmedShares(),
                    transaction.grossAmount(),
                    PositionStatus.CONFIRMED,
                    command.confirmedDate(),
                    now);
        }
        return current.applySnapshot(
                        transaction.positionSharesBefore(),
                        transaction.positionCostBefore(),
                        transaction.positionStatusBefore(),
                        transaction.positionHoldingStartDateBefore(),
                        now)
                .applyBuy(
                        command.confirmedShares(),
                        transaction.grossAmount(),
                        TransactionStatus.CONFIRMED,
                        command.confirmedDate(),
                        now);
    }

    private void restorePositionBeforeBuy(
            FundTransaction transaction,
            FundPosition current,
            Instant now) {
        if (transaction.positionWasEmptyBefore()) {
            portfolioRepository.deletePosition(current);
            return;
        }
        portfolioRepository.savePosition(current.applySnapshot(
                transaction.positionSharesBefore(),
                transaction.positionCostBefore(),
                transaction.positionStatusBefore(),
                transaction.positionHoldingStartDateBefore(),
                now));
    }

    private FundAccount lockedActiveAccount(
            long userId,
            long accountId) {
        FundAccount account = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .filter(candidate ->
                        candidate.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
        FundAccount locked = accountRepository
                .findByPublicIdAndUserIdForUpdate(
                        account.publicId(),
                        userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
        if (!locked.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ARCHIVED,
                    "已归档账户不能确认买入");
        }
        return locked;
    }

    private void validateConfirmationDate(
            LocalDate confirmedDate,
            LocalDate effectiveDate) {
        if (confirmedDate == null) {
            return;
        }
        if (confirmedDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "确认日期不能晚于当前日期");
        }
        if (confirmedDate.isBefore(effectiveDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_DATE,
                    "确认日期不能早于有效交易日");
        }
    }

    private void validateSnapshotBoundary(
            long userId,
            long accountId,
            LocalDate effectiveDate) {
        boundaryRepository.findLatestCommittedSnapshotAt(
                        userId,
                        accountId)
                .map(instant -> instant
                        .atZone(clock.getZone())
                        .toLocalDate())
                .filter(snapshotDate ->
                        !effectiveDate.isAfter(snapshotDate))
                .ifPresent(snapshotDate -> {
                    throw new BusinessException(
                            ErrorCode.TRANSACTION_BEFORE_SNAPSHOT,
                            "待确认买入早于最近生效快照，不能直接写入持仓");
                });
    }

    private boolean sameConfirmation(
            FundTransaction transaction,
            BuyConfirmationCommand command) {
        return transaction.shares() != null
                && transaction.shares().compareTo(
                        command.confirmedShares()) == 0
                && Objects.equals(
                        transaction.confirmedDate(),
                        command.confirmedDate());
    }

    private boolean sameNumber(
            BigDecimal left,
            BigDecimal right) {
        return left == null
                ? right == null
                : right != null
                        && left.compareTo(right) == 0;
    }

    private LocalDate earliest(
            LocalDate current,
            LocalDate candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return current.isBefore(candidate)
                ? current
                : candidate;
    }

    private String normalizeReason(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private User activeUserForUpdate(String publicId) {
        return userRepository.findByPublicIdForUpdate(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
    }

    private TransactionDetails details(
            long userId,
            FundTransaction transaction) {
        FundAccount account = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .filter(candidate -> candidate.id().equals(
                        transaction.accountId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        FundDefinition fund = fundDataRepository
                .findFundById(transaction.fundId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        return new TransactionDetails(
                transaction,
                account,
                fund);
    }

    private BusinessException stateConflict(String message) {
        return new BusinessException(
                ErrorCode.BUY_STATE_CONFLICT,
                message);
    }
}
