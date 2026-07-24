package com.fundkeeper.backend.portfolio.importing.application;

import com.fundkeeper.backend.account.domain.AccountPlatform;

public record SnapshotAccountPreview(
        String accountId,
        String name,
        AccountPlatform platform,
        boolean willCreate) {
}
