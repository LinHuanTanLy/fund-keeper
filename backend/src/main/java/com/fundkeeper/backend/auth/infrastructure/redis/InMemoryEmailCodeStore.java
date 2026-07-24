package com.fundkeeper.backend.auth.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.EmailCodeStore;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.email-code-store",
        havingValue = "memory")
public class InMemoryEmailCodeStore implements EmailCodeStore {

    private final Clock clock;
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public InMemoryEmailCodeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void issue(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            Duration ttl,
            Duration cooldown) {
        String key = key(emailKey, purpose);
        Instant now = clock.instant();
        Instant cooldownUntil = cooldowns.get(key);
        if (cooldownUntil != null && cooldownUntil.isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_CODE_RATE_LIMITED,
                    "验证码发送过于频繁，请稍后重试");
        }
        codes.put(key, new Entry(codeSignature, now.plus(ttl), 0));
        cooldowns.put(key, now.plus(cooldown));
    }

    @Override
    public synchronized VerificationResult verifyAndConsume(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            int maxAttempts) {
        String key = key(emailKey, purpose);
        Entry entry = codes.get(key);
        Instant now = clock.instant();
        if (entry == null || !entry.expiresAt().isAfter(now)) {
            codes.remove(key);
            return VerificationResult.MISSING_OR_EXPIRED;
        }
        if (entry.signature().equals(codeSignature)) {
            codes.remove(key);
            return VerificationResult.VERIFIED;
        }
        int attempts = entry.attempts() + 1;
        if (attempts >= maxAttempts) {
            codes.remove(key);
        } else {
            codes.put(key, new Entry(entry.signature(), entry.expiresAt(), attempts));
        }
        return VerificationResult.INVALID;
    }

    @Override
    public synchronized void delete(String emailKey, EmailCodePurpose purpose) {
        String key = key(emailKey, purpose);
        codes.remove(key);
        cooldowns.remove(key);
    }

    public synchronized void clear() {
        codes.clear();
        cooldowns.clear();
    }

    private String key(String emailKey, EmailCodePurpose purpose) {
        return purpose.name() + ":" + emailKey;
    }

    private record Entry(String signature, Instant expiresAt, int attempts) {
    }
}
