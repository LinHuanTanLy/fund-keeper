package com.fundkeeper.backend.account.application;

import java.time.Clock;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class FundAccountService {

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final AccountBusinessActivity businessActivity;
    private final AccountNameNormalizer nameNormalizer;
    private final Clock clock;

    public FundAccountService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            AccountBusinessActivity businessActivity,
            AccountNameNormalizer nameNormalizer,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.businessActivity = businessActivity;
        this.nameNormalizer = nameNormalizer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FundAccount> list(
            String userPublicId,
            boolean includeArchived) {
        User user = activeUser(userPublicId);
        return accountRepository.findAllByUserId(user.id(), includeArchived);
    }

    @Transactional(readOnly = true)
    public FundAccount get(String userPublicId, String accountPublicId) {
        User user = activeUser(userPublicId);
        return scopedAccount(user.id(), accountPublicId);
    }

    @Transactional
    public FundAccount create(
            String userPublicId,
            String name,
            AccountPlatform platform) {
        User user = activeUser(userPublicId);
        var normalized = nameNormalizer.normalize(name);
        ensureUniqueActiveName(
                user.id(),
                normalized.normalizedName(),
                "");
        return saveWithNameConflict(FundAccount.create(
                        user.id(),
                        normalized.displayName(),
                        normalized.normalizedName(),
                        platform,
                        clock.instant()));
    }

    @Transactional
    public FundAccount update(
            String userPublicId,
            String accountPublicId,
            String name,
            AccountPlatform platform) {
        User user = activeUser(userPublicId);
        FundAccount account = scopedAccount(user.id(), accountPublicId);
        if (!account.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ARCHIVED,
                    "已归档账户不能修改");
        }
        var normalized = nameNormalizer.normalize(name);
        ensureUniqueActiveName(
                user.id(),
                normalized.normalizedName(),
                account.publicId());
        return saveWithNameConflict(account.update(
                        normalized.displayName(),
                        normalized.normalizedName(),
                        platform,
                        clock.instant()));
    }

    @Transactional
    public FundAccount archive(
            String userPublicId,
            String accountPublicId) {
        User user = activeUser(userPublicId);
        FundAccount account = scopedAccount(user.id(), accountPublicId);
        if (!account.isActive()) {
            return account;
        }
        if (accountRepository.countActiveByUserIdForUpdate(user.id()) <= 1) {
            throw new BusinessException(
                    ErrorCode.LAST_ACTIVE_ACCOUNT,
                    "至少需要保留一个有效账户");
        }
        if (businessActivity.hasCurrentPositionOrPendingTransaction(
                user.id(),
                account.id())) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_HAS_OPEN_ACTIVITY,
                    "存在当前持仓或待确认交易，不能归档");
        }
        return accountRepository.save(account.archive(clock.instant()));
    }

    private FundAccount saveWithNameConflict(FundAccount account) {
        try {
            return accountRepository.save(account);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NAME_CONFLICT,
                    "有效账户名称不能重复",
                    exception);
        }
    }

    private User activeUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
    }

    private FundAccount scopedAccount(long userId, String accountPublicId) {
        return accountRepository
                .findByPublicIdAndUserId(accountPublicId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "账户不存在"));
    }

    private void ensureUniqueActiveName(
            long userId,
            String normalizedName,
            String excludedPublicId) {
        if (accountRepository.existsActiveName(
                userId,
                normalizedName,
                excludedPublicId)) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NAME_CONFLICT,
                    "有效账户名称不能重复");
        }
    }
}
