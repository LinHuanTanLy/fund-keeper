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
import com.fundkeeper.backend.fund.application.FundCatalogService;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.PositionSaleImpact;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class SellTransactionService {

    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final FundCatalogService fundCatalogService;
    private final FundDataRepository fundDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotBoundaryRepository boundaryRepository;
    private final SellTransactionPlanner sellPlanner;
    private final TransactionRequestFingerprint requestFingerprint;
    private final Clock clock;

    public SellTransactionService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            FundCatalogService fundCatalogService,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            SnapshotBoundaryRepository boundaryRepository,
            SellTransactionPlanner sellPlanner,
            TransactionRequestFingerprint requestFingerprint,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.fundCatalogService = fundCatalogService;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.boundaryRepository = boundaryRepository;
        this.sellPlanner = sellPlanner;
        this.requestFingerprint = requestFingerprint;
        this.clock = clock;
    }

    @Transactional
    public SellTransactionOutcome sell(
            String userPublicId,
            SellTransactionCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        SellTransactionCommand command =
                sellPlanner.normalize(rawCommand);
        String fingerprint = requestFingerprint.create(command);
        var existing = portfolioRepository
                .findTransactionByUserIdAndRequestId(
                        user.id(),
                        command.requestId());
        if (existing.isPresent()) {
            FundTransaction transaction = existing.get();
            if (transaction.type() != TransactionType.SELL
                    || !transaction.requestFingerprint()
                            .equals(fingerprint)) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_CONFLICT,
                        "requestId 已用于不同的交易内容");
            }
            return new SellTransactionOutcome(
                    details(user.id(), transaction),
                    true);
        }

        FundAccount account = lockedActiveAccount(
                user.id(),
                command.accountPublicId());
        FundDefinition fund =
                fundCatalogService.getSupportedFund(
                        command.fundCode());
        FundPosition position = portfolioRepository
                .findPositionByAccountIdAndFundId(
                        account.id(),
                        fund.id())
                .filter(value -> value.userId() == user.id())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POSITION_NOT_FOUND,
                        "当前账户不存在该基金持仓"));
        if (portfolioRepository.existsOpenSell(
                user.id(),
                account.id(),
                fund.id())) {
            throw new BusinessException(
                    ErrorCode.SELL_ALREADY_OPEN,
                    "该账户下的基金已有待确认或待校准卖出");
        }

        SellTransactionPlan plan = sellPlanner.planNormalized(
                command,
                fund,
                position);
        validateSnapshotBoundary(
                user.id(),
                account.id(),
                plan.effectiveDate());
        FundTransaction transaction =
                portfolioRepository.saveTransaction(
                        FundTransaction.createSell(
                                user.id(),
                                account.id(),
                                fund.id(),
                                command.requestId(),
                                fingerprint,
                                command.sellMode(),
                                plan.status(),
                                plan.amount(),
                                command.expectedAmount(),
                                command.actualReceivedAmount(),
                                plan.impact() == null
                                        ? null
                                        : plan.impact()
                                                .removedCost(),
                                plan.impact() == null
                                        ? null
                                        : plan.impact()
                                                .realizedProfit(),
                                plan.soldShares(),
                                position,
                                command.submittedDate(),
                                command.submittedPeriod(),
                                plan.effectiveDate(),
                                command.confirmedDate(),
                                plan.navDate(),
                                plan.unitNav(),
                                plan.navSource(),
                                plan.pendingReason(),
                                command.note(),
                                clock.instant()));
        if (plan.appliesToPosition()) {
            if (plan.impact().clearsPosition()) {
                portfolioRepository.deletePosition(position);
            } else {
                portfolioRepository.savePosition(
                        position.applySell(
                                plan.impact(),
                                plan.status(),
                                clock.instant()));
            }
        }
        return new SellTransactionOutcome(
                new TransactionDetails(
                        transaction,
                        account,
                        fund),
                false);
    }

    @Transactional
    public TransactionDetails confirm(
            String userPublicId,
            String transactionPublicId,
            SellConfirmationCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        SellConfirmationCommand command =
                normalizeConfirmation(rawCommand);
        FundTransaction transaction = lockedSell(
                user.id(),
                transactionPublicId);
        validateConfirmationDate(
                command.confirmedDate(),
                transaction.effectiveTradeDate());

        if (transaction.status() == TransactionStatus.CONFIRMED) {
            if (sameConfirmation(transaction, command)) {
                return details(user.id(), transaction);
            }
            throw stateConflict("卖出交易已经确认，不能覆盖确认结果");
        }
        ensureOpen(transaction);
        ensurePositionSnapshot(transaction);

        FundPosition current = currentPosition(
                user.id(),
                transaction);
        verifyCurrentPosition(transaction, current);
        FundPosition before = current.restoreSellSnapshot(
                transaction.positionSharesBefore(),
                transaction.positionCostBefore(),
                transaction.positionStatusBefore(),
                transaction.positionHoldingStartDateBefore(),
                clock.instant());

        BigDecimal finalShares;
        if (transaction.sellMode() == SellMode.PARTIAL) {
            if (command.confirmedShares() == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "部分卖出补录确认时必须填写平台确认份额");
            }
            if (command.confirmedShares()
                            .compareTo(before.shares())
                    >= 0) {
                throw new BusinessException(
                        ErrorCode.SELL_SHARES_EXCEED_POSITION,
                        "部分卖出份额必须小于卖出前持仓份额");
            }
            finalShares = command.confirmedShares();
        } else {
            if (command.confirmedShares() != null
                    && command.confirmedShares()
                                    .compareTo(before.shares())
                            != 0) {
                throw new BusinessException(
                        ErrorCode.SELL_SHARES_EXCEED_POSITION,
                        "全部卖出的确认份额必须等于卖出前持仓份额");
            }
            finalShares = before.shares();
        }

        PositionSaleImpact impact = before.saleImpact(
                finalShares,
                command.actualReceivedAmount());
        Instant now = clock.instant();
        if (impact.clearsPosition()) {
            portfolioRepository.deletePosition(current);
        } else {
            portfolioRepository.savePosition(
                    before.applySell(
                            impact,
                            TransactionStatus.CONFIRMED,
                            now));
        }
        FundTransaction confirmed =
                portfolioRepository.updateTransaction(
                        transaction.confirmSell(
                                command.actualReceivedAmount(),
                                impact.soldShares(),
                                impact.removedCost(),
                                impact.realizedProfit(),
                                command.confirmedDate(),
                                now));
        return details(user.id(), confirmed);
    }

    @Transactional
    public TransactionDetails cancel(
            String userPublicId,
            String transactionPublicId,
            SellCancellationCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        FundTransaction transaction = lockedSell(
                user.id(),
                transactionPublicId);
        if (transaction.status() == TransactionStatus.CANCELLED) {
            return details(user.id(), transaction);
        }
        ensureOpen(transaction);
        ensurePositionSnapshot(transaction);

        FundPosition current = currentPosition(
                user.id(),
                transaction);
        verifyCurrentPosition(transaction, current);
        Instant now = clock.instant();
        if (transaction.status() == TransactionStatus.ESTIMATED) {
            portfolioRepository.savePosition(
                    current.restoreSellSnapshot(
                            transaction.positionSharesBefore(),
                            transaction.positionCostBefore(),
                            transaction.positionStatusBefore(),
                            transaction.positionHoldingStartDateBefore(),
                            now));
        }
        FundTransaction cancelled =
                portfolioRepository.updateTransaction(
                        transaction.cancelSell(
                                normalizeReason(rawCommand.reason()),
                                now));
        return details(user.id(), cancelled);
    }

    private SellConfirmationCommand normalizeConfirmation(
            SellConfirmationCommand command) {
        return new SellConfirmationCommand(
                command.actualReceivedAmount().setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP),
                command.confirmedShares() == null
                        ? null
                        : command.confirmedShares().setScale(
                                SHARE_SCALE,
                                RoundingMode.HALF_UP),
                command.confirmedDate());
    }

    private FundTransaction lockedSell(
            long userId,
            String transactionPublicId) {
        FundTransaction transaction = portfolioRepository
                .findTransactionByPublicIdAndUserIdForUpdate(
                        transactionPublicId,
                        userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        if (transaction.type() != TransactionType.SELL) {
            throw stateConflict("只有卖出交易可以执行该操作");
        }
        return transaction;
    }

    private void ensureOpen(FundTransaction transaction) {
        if (transaction.status() != TransactionStatus.PENDING
                && transaction.status()
                        != TransactionStatus.ESTIMATED) {
            throw stateConflict("当前卖出状态不允许确认或撤销");
        }
    }

    private void ensurePositionSnapshot(
            FundTransaction transaction) {
        if (!transaction.hasPositionSnapshot()) {
            throw stateConflict(
                    "该历史卖出缺少恢复快照，不能自动处理，请人工校准");
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
                        "持仓已发生变化，不能自动确认或撤销"));
    }

    private void verifyCurrentPosition(
            FundTransaction transaction,
            FundPosition current) {
        BigDecimal expectedShares =
                transaction.positionSharesBefore();
        BigDecimal expectedCost =
                transaction.positionCostBefore();
        if (transaction.status() == TransactionStatus.ESTIMATED) {
            if (transaction.shares() == null
                    || transaction.removedCost() == null) {
                throw stateConflict("卖出估算数据不完整，不能自动处理");
            }
            expectedShares = expectedShares
                    .subtract(transaction.shares());
            expectedCost = expectedCost
                    .subtract(transaction.removedCost());
        }
        var expectedStatus =
                transaction.status() == TransactionStatus.ESTIMATED
                        ? PositionStatus.ESTIMATED
                        : transaction.positionStatusBefore();
        if (!sameNumber(current.shares(), expectedShares)
                || !sameNumber(
                        current.remainingCost(),
                        expectedCost)
                || current.status() != expectedStatus
                || !Objects.equals(
                        current.holdingStartDate(),
                        transaction
                                .positionHoldingStartDateBefore())) {
            throw stateConflict(
                    "持仓在卖出后已发生变化，请先执行历史校准");
        }
    }

    private boolean sameConfirmation(
            FundTransaction transaction,
            SellConfirmationCommand command) {
        return sameNumber(
                        transaction.actualReceivedAmount(),
                        command.actualReceivedAmount())
                && (command.confirmedShares() == null
                        || sameNumber(
                                transaction.shares(),
                                command.confirmedShares()))
                && Objects.equals(
                        transaction.confirmedDate(),
                        command.confirmedDate());
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

    private boolean sameNumber(
            BigDecimal left,
            BigDecimal right) {
        return left == null
                ? right == null
                : right != null && left.compareTo(right) == 0;
    }

    private String normalizeReason(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BusinessException stateConflict(String message) {
        return new BusinessException(
                ErrorCode.SELL_STATE_CONFLICT,
                message);
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
                            "新交易必须晚于最近一次生效快照");
                });
    }

    private User activeUserForUpdate(String publicId) {
        return userRepository.findByPublicIdForUpdate(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
    }

    private FundAccount lockedActiveAccount(
            long userId,
            String accountPublicId) {
        FundAccount account = accountRepository
                .findByPublicIdAndUserIdForUpdate(
                        accountPublicId,
                        userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
        if (!account.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ARCHIVED,
                    "已归档账户不能新增交易");
        }
        return account;
    }

    private TransactionDetails details(
            long userId,
            FundTransaction transaction) {
        FundAccount account = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .filter(candidate ->
                        candidate.id().equals(
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
}
