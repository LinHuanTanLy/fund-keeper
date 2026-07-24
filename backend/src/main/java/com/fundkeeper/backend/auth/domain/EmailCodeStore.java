package com.fundkeeper.backend.auth.domain;

import java.time.Duration;

public interface EmailCodeStore {

    void issue(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            Duration ttl,
            Duration cooldown);

    VerificationResult verifyAndConsume(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            int maxAttempts);

    void delete(String emailKey, EmailCodePurpose purpose);

    enum VerificationResult {
        VERIFIED,
        MISSING_OR_EXPIRED,
        INVALID
    }
}
