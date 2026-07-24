package com.fundkeeper.backend.portfolio.application;

import java.time.Clock;
import java.time.LocalDate;

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
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.TransactionType;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class SellTransactionService {

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
