package com.fundkeeper.backend.auth.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.LoginAttemptStore;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class AuthFacade {

    private final UserRepository userRepository;
    private final EmailNormalizer emailNormalizer;
    private final EmailCodeService emailCodeService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final CryptoSupport cryptoSupport;
    private final AuthProperties properties;
    private final LoginAttemptStore loginAttemptStore;
    private final AuthTransactionService transactions;
    private final AccessTokenService accessTokenService;
    private final String dummyPasswordHash;

    public AuthFacade(
            UserRepository userRepository,
            EmailNormalizer emailNormalizer,
            EmailCodeService emailCodeService,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            CryptoSupport cryptoSupport,
            AuthProperties properties,
            LoginAttemptStore loginAttemptStore,
            AuthTransactionService transactions,
            AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.emailNormalizer = emailNormalizer;
        this.emailCodeService = emailCodeService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.cryptoSupport = cryptoSupport;
        this.properties = properties;
        this.loginAttemptStore = loginAttemptStore;
        this.transactions = transactions;
        this.accessTokenService = accessTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(
                cryptoSupport.randomRefreshToken());
    }

    public void requestEmailCode(String email, EmailCodePurpose purpose) {
        String normalizedEmail = emailNormalizer.normalize(email);
        boolean registered = userRepository.existsByEmail(normalizedEmail);
        if (purpose == EmailCodePurpose.REGISTER && registered) {
            return;
        }
        if (purpose == EmailCodePurpose.RESET_PASSWORD && !registered) {
            return;
        }
        emailCodeService.issue(normalizedEmail, purpose);
    }

    public User register(String email, String password, String code) {
        String normalizedEmail = emailNormalizer.normalize(email);
        passwordPolicy.validate(password);
        emailCodeService.verifyAndConsume(
                normalizedEmail,
                EmailCodePurpose.REGISTER,
                code);
        String passwordHash = passwordEncoder.encode(password);
        try {
            return transactions.register(normalizedEmail, passwordHash);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "该邮箱已注册",
                    exception);
        }
    }

    public AuthTokens login(String email, String password) {
        String normalizedEmail = emailNormalizer.normalize(email);
        String subjectKey = cryptoSupport.sha256(normalizedEmail);
        if (loginAttemptStore.isBlocked(subjectKey)) {
            throw invalidCredentials();
        }
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(User::isActive)
                .orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                password,
                user == null ? dummyPasswordHash : user.passwordHash());
        if (user == null || !passwordMatches) {
            loginAttemptStore.recordFailure(
                    subjectKey,
                    properties.loginMaxAttempts(),
                    properties.loginAttemptWindow(),
                    properties.loginBlockDuration());
            throw invalidCredentials();
        }
        loginAttemptStore.clear(subjectKey);
        return issueSession(user.id());
    }

    public AuthTokens refresh(String refreshToken) {
        String newRefreshToken = cryptoSupport.randomRefreshToken();
        var issue = transactions.rotateSession(
                cryptoSupport.sha256(refreshToken),
                newRefreshToken,
                cryptoSupport.sha256(newRefreshToken));
        return tokens(issue);
    }

    public void logout(String refreshToken) {
        transactions.logout(cryptoSupport.sha256(refreshToken));
    }

    public void resetPassword(
            String email,
            String code,
            String newPassword) {
        String normalizedEmail = emailNormalizer.normalize(email);
        passwordPolicy.validate(newPassword);
        emailCodeService.verifyAndConsume(
                normalizedEmail,
                EmailCodePurpose.RESET_PASSWORD,
                code);
        transactions.resetPassword(
                normalizedEmail,
                passwordEncoder.encode(newPassword));
    }

    public AuthTokens.UserView currentUser(String publicId) {
        User user = userRepository.findByPublicId(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
        return userView(user);
    }

    private AuthTokens issueSession(long userId) {
        String refreshToken = cryptoSupport.randomRefreshToken();
        var issue = transactions.createSession(
                userId,
                refreshToken,
                cryptoSupport.sha256(refreshToken));
        return tokens(issue);
    }

    private AuthTokens tokens(AuthTransactionService.SessionIssue issue) {
        var accessToken = accessTokenService.issue(
                issue.user(),
                issue.session());
        return AuthTokens.of(
                accessToken,
                issue.refreshToken(),
                issue.session().expiresAt(),
                userView(issue.user()));
    }

    private AuthTokens.UserView userView(User user) {
        return new AuthTokens.UserView(user.publicId(), user.email());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                ErrorCode.INVALID_CREDENTIALS,
                "邮箱或密码错误");
    }
}
