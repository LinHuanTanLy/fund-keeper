package com.fundkeeper.backend.auth.application;

import org.springframework.stereotype.Service;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.EmailCodeStore;
import com.fundkeeper.backend.auth.domain.VerificationEmailSender;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class EmailCodeService {

    private final AuthProperties properties;
    private final CryptoSupport cryptoSupport;
    private final EmailCodeStore store;
    private final VerificationEmailSender sender;

    public EmailCodeService(
            AuthProperties properties,
            CryptoSupport cryptoSupport,
            EmailCodeStore store,
            VerificationEmailSender sender) {
        this.properties = properties;
        this.cryptoSupport = cryptoSupport;
        this.store = store;
        this.sender = sender;
    }

    public void issue(String normalizedEmail, EmailCodePurpose purpose) {
        String emailKey = cryptoSupport.sha256(normalizedEmail);
        String code = cryptoSupport.randomSixDigitCode();
        String signature = signature(normalizedEmail, purpose, code);

        store.issue(
                emailKey,
                purpose,
                signature,
                properties.verificationCodeTtl(),
                properties.verificationCodeCooldown());

        try {
            sender.send(
                    normalizedEmail,
                    purpose,
                    code,
                    properties.verificationCodeTtl());
        } catch (RuntimeException exception) {
            store.delete(emailKey, purpose);
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "验证码邮件暂时无法发送，请稍后重试",
                    exception);
        }
    }

    public void verifyAndConsume(
            String normalizedEmail,
            EmailCodePurpose purpose,
            String code) {
        String emailKey = cryptoSupport.sha256(normalizedEmail);
        String signature = signature(normalizedEmail, purpose, code);
        var result = store.verifyAndConsume(
                emailKey,
                purpose,
                signature,
                properties.verificationCodeMaxAttempts());

        if (result != EmailCodeStore.VerificationResult.VERIFIED) {
            throw new BusinessException(
                    ErrorCode.INVALID_EMAIL_CODE,
                    "验证码错误或已过期");
        }
    }

    private String signature(
            String normalizedEmail,
            EmailCodePurpose purpose,
            String code) {
        return cryptoSupport.hmacSha256(
                properties.verificationSecretBase64(),
                purpose.name() + ":" + normalizedEmail + ":" + code);
    }
}
