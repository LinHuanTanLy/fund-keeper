package com.fundkeeper.backend.account.api;

import java.time.Instant;

import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.AccountStatus;
import com.fundkeeper.backend.account.domain.FundAccount;

public record FundAccountView(
        String id,
        String name,
        AccountPlatform platform,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {

    static FundAccountView from(FundAccount account) {
        return new FundAccountView(
                account.publicId(),
                account.name(),
                account.platform(),
                account.status(),
                account.createdAt(),
                account.updatedAt(),
                account.archivedAt());
    }
}
