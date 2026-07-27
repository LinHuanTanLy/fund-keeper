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
    private final Clock clock;

    public BuyTransactionStateService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            SnapshotBoundaryRepository boundaryRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.boundaryRepository = boundaryRepository;
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
        ensurePending(transaction, "确认");

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

        Instant now = clock.instant();
        FundPosition position = portfolioRepository
                .findPositionByAccountIdAndFundId(
                        account.id(),
                        transaction.fundId())
                .filter(current -> current.userId() == user.id())
                .map(current -> current.applyBuy(
                        command.confirmedShares(),
                        transaction.grossAmount(),
                        TransactionStatus.CONFIRMED,
                        command.confirmedDate(),
                        now))
                .orElseGet(() -> FundPosition.fromBuy(
                        user.id(),
                        account.id(),
                        transaction.fundId(),
                        command.confirmedShares(),
                        transaction.grossAmount(),
                        TransactionStatus.CONFIRMED,
                        command.confirmedDate(),
                        now));
        portfolioRepository.savePosition(position);
        FundTransaction confirmed = portfolioRepository
                .updateTransaction(transaction.confirmBuy(
                        command.confirmedShares(),
                        command.confirmedDate(),
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
        ensurePending(transaction, "撤销");
        FundTransaction cancelled = portfolioRepository
                .updateTransaction(transaction.cancelBuy(
                        normalizeReason(rawReason),
                        clock.instant()));
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

    private void ensurePending(
            FundTransaction transaction,
            String action) {
        if (transaction.status() == TransactionStatus.ESTIMATED) {
            throw stateConflict(
                    "系统估算买入尚不支持自动" + action
                            + "，请通过持仓校准处理");
        }
        if (transaction.status() != TransactionStatus.PENDING) {
            throw stateConflict(
                    "当前买入状态不允许" + action);
        }
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
