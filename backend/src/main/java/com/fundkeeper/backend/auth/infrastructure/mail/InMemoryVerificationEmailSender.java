package com.fundkeeper.backend.auth.infrastructure.mail;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.VerificationEmailSender;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.mail-sender",
        havingValue = "memory")
public class InMemoryVerificationEmailSender implements VerificationEmailSender {

    private final Map<String, String> latestCodes = new ConcurrentHashMap<>();

    @Override
    public void send(
            String email,
            EmailCodePurpose purpose,
            String code,
            Duration validFor) {
        latestCodes.put(key(email, purpose), code);
    }

    public Optional<String> latestCode(String email, EmailCodePurpose purpose) {
        return Optional.ofNullable(latestCodes.get(key(email, purpose)));
    }

    public void clear() {
        latestCodes.clear();
    }

    private String key(String email, EmailCodePurpose purpose) {
        return purpose.name() + ":" + email;
    }
}
