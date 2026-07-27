package com.fundkeeper.backend.portfolio.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.fundkeeper.backend.portfolio.domain.SellTransactionSummary;
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.TransactionQuery;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class PortfolioService {

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final FundDataRepository fundDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotBoundaryRepository snapshotBoundaryRepository;
    private final BuyTransactionPlanner buyPlanner;
    private final TransactionRequestFingerprint requestFingerprint;
    private final Clock clock;

    public PortfolioService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            SnapshotBoundaryRepository snapshotBoundaryRepository,
            BuyTransactionPlanner buyPlanner,
            TransactionRequestFingerprint requestFingerprint,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.snapshotBoundaryRepository = snapshotBoundaryRepository;
        this.buyPlanner = buyPlanner;
        this.requestFingerprint = requestFingerprint;
        this.clock = clock;
    }

    @Transactional
    public BuyTransactionOutcome buy(
            String userPublicId,
            BuyTransactionCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        BuyTransactionCommand command = buyPlanner.normalize(rawCommand);
        String fingerprint = requestFingerprint.create(command);

        var existing = portfolioRepository
                .findTransactionByUserIdAndRequestId(
                        user.id(),
                        command.requestId());
        if (existing.isPresent()) {
            FundTransaction transaction = existing.get();
            if (transaction.type() != TransactionType.BUY
                    || !transaction.requestFingerprint()
                            .equals(fingerprint)) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_CONFLICT,
                        "requestId 已用于不同的交易内容");
            }
            return new BuyTransactionOutcome(
                    details(user.id(), transaction),
                    true);
        }

        FundAccount account = lockedActiveAccount(
                user.id(),
                command.accountPublicId());
        BuyTransactionPlan plan =
                buyPlanner.planNormalized(command);
        if (portfolioRepository.existsOpenSell(
                user.id(),
                account.id(),
                plan.fund().id())) {
            throw new BusinessException(
                    ErrorCode.SELL_ALREADY_OPEN,
                    "该基金存在待确认或待校准卖出，处理完成前不能继续买入");
        }
        validateSnapshotBoundary(
                user.id(),
                account.id(),
                plan.effectiveDate());
        Instant now = clock.instant();
        FundTransaction transaction = portfolioRepository.saveTransaction(
                FundTransaction.createBuy(
                        user.id(),
                        account.id(),
                        plan.fund().id(),
                        command.requestId(),
                        fingerprint,
                        plan.status(),
                        command.amount(),
                        plan.feeAmount(),
                        plan.netAmount(),
                        plan.shares(),
                        command.submittedDate(),
                        command.submittedPeriod(),
                        plan.effectiveDate(),
                        command.confirmedDate(),
                        plan.navDate(),
                        plan.unitNav(),
                        plan.navSource(),
                        plan.feeRate(),
                        plan.feeSource(),
                        plan.pendingReason(),
                        command.note(),
                        now));

        if (transaction.appliesToPosition()) {
            applyBuyToPosition(
                    user.id(),
                    account.id(),
                    plan.fund().id(),
                    transaction,
                    plan.holdingStartDate(),
                    now);
        }
        return new BuyTransactionOutcome(
                new TransactionDetails(
                        transaction,
                        account,
                        plan.fund()),
                false);
    }

    @Transactional(readOnly = true)
    public TransactionDetails getTransaction(
            String userPublicId,
            String transactionPublicId) {
        User user = activeUser(userPublicId);
        FundTransaction transaction = portfolioRepository
                .findTransactionByPublicIdAndUserId(
                        transactionPublicId,
                        user.id())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        return details(user.id(), transaction);
    }

    @Transactional(readOnly = true)
    public TransactionDetails getTransactionByRequestId(
            String userPublicId,
            String requestId) {
        User user = activeUser(userPublicId);
        FundTransaction transaction = portfolioRepository
                .findTransactionByUserIdAndRequestId(user.id(), requestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        return details(user.id(), transaction);
    }

    @Transactional(readOnly = true)
    public TransactionPageDetails listTransactions(
            String userPublicId,
            String accountPublicId,
            String fundCode,
            String rawType,
            String rawStatus,
            int page,
            int size) {
        User user = activeUser(userPublicId);
        validatePage(page, size);
        Long accountId = optionalAccountId(
                user.id(),
                accountPublicId);
        Long fundId = optionalFundId(fundCode);
        TransactionType type = optionalEnum(
                rawType,
                TransactionType.class,
                "type");
        TransactionStatus status = optionalEnum(
                rawStatus,
                TransactionStatus.class,
                "status");

        var result = portfolioRepository.findTransactions(
                new TransactionQuery(
                        user.id(),
                        accountId,
                        fundId,
                        type,
                        status,
                        page,
                        size));
        return new TransactionPageDetails(
                transactionDetails(user.id(), result.items()),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @Transactional(readOnly = true)
    public SellTransactionSummary summarizeSells(
            String userPublicId,
            String accountPublicId,
            String fundCode) {
        User user = activeUser(userPublicId);
        return portfolioRepository.summarizeSells(
                user.id(),
                optionalAccountId(user.id(), accountPublicId),
                optionalFundId(fundCode));
    }

    @Transactional(readOnly = true)
    public List<PositionDetails> listPositions(
            String userPublicId,
            String accountPublicId) {
        User user = activeUser(userPublicId);
        List<FundPosition> positions;
        if (accountPublicId == null || accountPublicId.isBlank()) {
            positions = portfolioRepository.findPositionsByUserId(user.id());
        } else {
            FundAccount account = scopedAccount(
                    user.id(),
                    accountPublicId);
            positions = portfolioRepository
                    .findPositionsByUserIdAndAccountId(
                            user.id(),
                            account.id());
        }
        return positions.stream()
                .map(position -> positionDetails(user.id(), position))
                .toList();
    }

    private void applyBuyToPosition(
            long userId,
            long accountId,
            long fundId,
            FundTransaction transaction,
            LocalDate holdingStartDate,
            java.time.Instant now) {
        FundPosition position = portfolioRepository
                .findPositionByAccountIdAndFundId(accountId, fundId)
                .map(existing -> existing.applyBuy(
                        transaction.shares(),
                        transaction.grossAmount(),
                        transaction.status(),
                        holdingStartDate,
                        now))
                .orElseGet(() -> FundPosition.fromBuy(
                        userId,
                        accountId,
                        fundId,
                        transaction.shares(),
                        transaction.grossAmount(),
                        transaction.status(),
                        holdingStartDate,
                        now));
        portfolioRepository.savePosition(position);
    }

    private void validateSnapshotBoundary(
            long userId,
            long accountId,
            LocalDate effectiveDate) {
        snapshotBoundaryRepository
                .findLatestCommittedSnapshotAt(userId, accountId)
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

    private User activeUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
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

    private FundAccount scopedAccount(
            long userId,
            String accountPublicId) {
        return accountRepository
                .findByPublicIdAndUserId(accountPublicId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
    }

    private Long optionalAccountId(
            long userId,
            String accountPublicId) {
        if (accountPublicId == null || accountPublicId.isBlank()) {
            return null;
        }
        return scopedAccount(userId, accountPublicId.trim()).id();
    }

    private Long optionalFundId(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }
        return fundDataRepository
                .findFundByCode(fundCode.trim())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FUND_NOT_FOUND,
                        "基金不存在"))
                .id();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "page 必须大于等于 0，size 必须在 1 到 100 之间");
        }
    }

    private <E extends Enum<E>> E optionalEnum(
            String rawValue,
            Class<E> enumType,
            String parameterName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(
                    enumType,
                    rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    parameterName + " 不是支持的值");
        }
    }

    private List<TransactionDetails> transactionDetails(
            long userId,
            List<FundTransaction> transactions) {
        Map<Long, FundAccount> accounts = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .collect(Collectors.toMap(
                        FundAccount::id,
                        Function.identity()));
        Map<Long, FundDefinition> funds = new HashMap<>();
        return transactions.stream()
                .map(transaction -> {
                    FundAccount account = accounts.get(
                            transaction.accountId());
                    if (account == null) {
                        throw new IllegalStateException(
                                "Transaction account no longer exists");
                    }
                    FundDefinition fund = funds.computeIfAbsent(
                            transaction.fundId(),
                            fundId -> fundDataRepository
                                    .findFundById(fundId)
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "Transaction fund no longer exists")));
                    return new TransactionDetails(
                            transaction,
                            account,
                            fund);
                })
                .toList();
    }

    private TransactionDetails details(
            long userId,
            FundTransaction transaction) {
        FundAccount account = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .filter(candidate ->
                        candidate.id().equals(transaction.accountId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        FundDefinition fund = fundDataRepository
                .findFundById(transaction.fundId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "交易记录不存在"));
        return new TransactionDetails(transaction, account, fund);
    }

    private PositionDetails positionDetails(
            long userId,
            FundPosition position) {
        FundAccount account = accountRepository
                .findAllByUserId(userId, true)
                .stream()
                .filter(candidate ->
                        candidate.id().equals(position.accountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Position account no longer exists"));
        FundDefinition fund = fundDataRepository
                .findFundById(position.fundId())
                .orElseThrow(() -> new IllegalStateException(
                        "Position fund no longer exists"));
        return new PositionDetails(position, account, fund);
    }

}
