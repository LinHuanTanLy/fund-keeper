package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.fund.application.FundCatalogService;
import com.fundkeeper.backend.fund.domain.FeeCalculationMethod;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.fund.domain.PurchaseFeeRule;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class PortfolioService {

    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final FundCatalogService fundCatalogService;
    private final FundDataRepository fundDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradingCalendarService tradingCalendarService;
    private final TransactionRequestFingerprint requestFingerprint;
    private final Clock clock;

    public PortfolioService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            FundCatalogService fundCatalogService,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            TradingCalendarService tradingCalendarService,
            TransactionRequestFingerprint requestFingerprint,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.fundCatalogService = fundCatalogService;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.requestFingerprint = requestFingerprint;
        this.clock = clock;
    }

    @Transactional
    public BuyTransactionOutcome buy(
            String userPublicId,
            BuyTransactionCommand rawCommand) {
        User user = activeUserForUpdate(userPublicId);
        BuyTransactionCommand command = normalize(rawCommand);
        String fingerprint = requestFingerprint.create(command);

        var existing = portfolioRepository
                .findTransactionByUserIdAndRequestId(
                        user.id(),
                        command.requestId());
        if (existing.isPresent()) {
            FundTransaction transaction = existing.get();
            if (!transaction.requestFingerprint().equals(fingerprint)) {
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
        Instant now = clock.instant();
        FundTransaction transaction = portfolioRepository.saveTransaction(
                FundTransaction.createBuy(
                        user.id(),
                        account.id(),
                        fund.id(),
                        command.requestId(),
                        fingerprint,
                        calculation.status(),
                        command.amount(),
                        calculation.feeAmount(),
                        calculation.netAmount(),
                        calculation.shares(),
                        command.submittedDate(),
                        command.submittedPeriod(),
                        effectiveDate,
                        command.confirmedDate(),
                        calculation.navDate(),
                        calculation.unitNav(),
                        calculation.navSource(),
                        calculation.feeRate(),
                        calculation.feeSource(),
                        calculation.pendingReason(),
                        command.note(),
                        now));

        if (transaction.appliesToPosition()) {
            applyBuyToPosition(
                    user.id(),
                    account.id(),
                    fund.id(),
                    transaction,
                    holdingStartDate,
                    now);
        }
        return new BuyTransactionOutcome(
                new TransactionDetails(transaction, account, fund),
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

    private BuyTransactionCommand normalize(
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
