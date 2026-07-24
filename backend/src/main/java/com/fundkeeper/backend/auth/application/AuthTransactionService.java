package com.fundkeeper.backend.auth.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.AuthSession;
import com.fundkeeper.backend.auth.domain.AuthSessionRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class AuthTransactionService {

    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthTransactionService(
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            AuthSessionRepository sessionRepository,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public User register(String normalizedEmail, String passwordHash) {
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "该邮箱已注册");
        }
        Instant now = clock.instant();
        User user = userRepository.save(
                User.register(normalizedEmail, passwordHash, now));
        accountRepository.save(FundAccount.createDefault(user.id(), now));
        return user;
    }

    @Transactional
    public SessionIssue createSession(
            long userId,
            String refreshToken,
            String refreshTokenHash) {
        User user = activeUser(userId);
        Instant now = clock.instant();
        AuthSession session = sessionRepository.save(
                AuthSession.create(
                        user.id(),
                        refreshTokenHash,
                        now.plus(properties.refreshTokenTtl()),
                        now));
        return new SessionIssue(user, session, refreshToken);
    }

    @Transactional
    public SessionIssue rotateSession(
            String oldRefreshTokenHash,
            String newRefreshToken,
            String newRefreshTokenHash) {
        Instant now = clock.instant();
        AuthSession oldSession = sessionRepository
                .findByRefreshTokenHashForUpdate(oldRefreshTokenHash)
                .orElseThrow(this::invalidRefreshToken);

        if (!oldSession.isActiveAt(now)) {
            throw invalidRefreshToken();
        }

        User user = activeUser(oldSession.userId());
        sessionRepository.save(oldSession.markUsedAndRevoke(now));
        AuthSession newSession = sessionRepository.save(
                AuthSession.create(
                        user.id(),
                        newRefreshTokenHash,
                        now.plus(properties.refreshTokenTtl()),
                        now));
        return new SessionIssue(user, newSession, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshTokenHash) {
        Instant now = clock.instant();
        sessionRepository.findByRefreshTokenHashForUpdate(refreshTokenHash)
                .filter(session -> session.isActiveAt(now))
                .ifPresent(session -> sessionRepository.save(session.revoke(now)));
    }

    @Transactional
    public User resetPassword(String normalizedEmail, String passwordHash) {
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_EMAIL_CODE,
                        "验证码错误或已过期"));
        Instant now = clock.instant();
        User updated = userRepository.save(user.changePassword(passwordHash, now));
        sessionRepository.revokeAllByUserId(updated.id(), now);
        return updated;
    }

    private User activeUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(this::invalidRefreshToken);
        if (!user.isActive()) {
            throw invalidRefreshToken();
        }
        return user;
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(
                ErrorCode.INVALID_REFRESH_TOKEN,
                "刷新凭证无效或已过期");
    }

    public record SessionIssue(
            User user,
            AuthSession session,
            String refreshToken) {
    }
}
