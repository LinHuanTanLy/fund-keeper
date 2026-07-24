package com.fundkeeper.backend.auth.infrastructure.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.EmailCodeStore;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.email-code-store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisEmailCodeStore implements EmailCodeStore {

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('GET', KEYS[1])
                    if not current then
                        return 0
                    end
                    if current == ARGV[1] then
                        redis.call('DEL', KEYS[1])
                        redis.call('DEL', KEYS[2])
                        return 1
                    end
                    local attempts = redis.call('INCR', KEYS[2])
                    if attempts == 1 then
                        local remaining = redis.call('PTTL', KEYS[1])
                        if remaining > 0 then
                            redis.call('PEXPIRE', KEYS[2], remaining)
                        end
                    end
                    if attempts >= tonumber(ARGV[2]) then
                        redis.call('DEL', KEYS[1])
                    end
                    return -attempts
                    """, Long.class);

    private final StringRedisTemplate redis;

    public RedisEmailCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void issue(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            Duration ttl,
            Duration cooldown) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(cooldownKey(emailKey, purpose), "1", cooldown);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_CODE_RATE_LIMITED,
                    "验证码发送过于频繁，请稍后重试");
        }
        redis.opsForValue().set(codeKey(emailKey, purpose), codeSignature, ttl);
    }

    @Override
    public VerificationResult verifyAndConsume(
            String emailKey,
            EmailCodePurpose purpose,
            String codeSignature,
            int maxAttempts) {
        Long result = redis.execute(
                VERIFY_SCRIPT,
                List.of(
                        codeKey(emailKey, purpose),
                        attemptsKey(emailKey, purpose)),
                codeSignature,
                Integer.toString(maxAttempts));
        if (result != null && result == 1) {
            return VerificationResult.VERIFIED;
        }
        if (result == null || result == 0) {
            return VerificationResult.MISSING_OR_EXPIRED;
        }
        return VerificationResult.INVALID;
    }

    @Override
    public void delete(String emailKey, EmailCodePurpose purpose) {
        redis.delete(List.of(
                codeKey(emailKey, purpose),
                attemptsKey(emailKey, purpose),
                cooldownKey(emailKey, purpose)));
    }

    private String codeKey(String emailKey, EmailCodePurpose purpose) {
        return "auth:email-code:v1:" + purpose.name() + ":" + emailKey;
    }

    private String attemptsKey(String emailKey, EmailCodePurpose purpose) {
        return "auth:email-code-attempts:v1:" + purpose.name() + ":" + emailKey;
    }

    private String cooldownKey(String emailKey, EmailCodePurpose purpose) {
        return "auth:email-code-cooldown:v1:" + purpose.name() + ":" + emailKey;
    }
}
