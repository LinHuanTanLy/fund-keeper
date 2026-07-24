package com.fundkeeper.backend.account.application;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class AccountNameNormalizer {

    public NormalizedAccountName normalize(String rawName) {
        String displayName = rawName.strip().replaceAll("\\s+", " ");
        String normalizedName = displayName.toLowerCase(Locale.ROOT);
        return new NormalizedAccountName(displayName, normalizedName);
    }

    public record NormalizedAccountName(
            String displayName,
            String normalizedName) {
    }
}
