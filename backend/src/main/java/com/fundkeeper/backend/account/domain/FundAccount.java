package com.fundkeeper.backend.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FundAccount(
        Long id,
        String publicId,
        long userId,
        String name,
        String normalizedName,
        AccountPlatform platform,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {

    public FundAccount {
        Objects.requireNonNull(publicId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(normalizedName);
        Objects.requireNonNull(platform);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static FundAccount createDefault(long userId, Instant now) {
        return create(
                userId,
                "默认账户",
                "默认账户",
                AccountPlatform.OTHER,
                now);
    }

    public static FundAccount create(
            long userId,
            String name,
            String normalizedName,
            AccountPlatform platform,
            Instant now) {
        return new FundAccount(
                null,
                UUID.randomUUID().toString(),
                userId,
                name,
                normalizedName,
                platform,
                AccountStatus.ACTIVE,
                now,
                now,
                null);
    }

    public FundAccount update(
            String newName,
            String newNormalizedName,
            AccountPlatform newPlatform,
            Instant now) {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Archived account cannot be updated");
        }
        return new FundAccount(
                id,
                publicId,
                userId,
                newName,
                newNormalizedName,
                newPlatform,
                status,
                createdAt,
                now,
                archivedAt);
    }

    public FundAccount archive(Instant now) {
        if (status == AccountStatus.ARCHIVED) {
            return this;
        }
        return new FundAccount(
                id,
                publicId,
                userId,
                name,
                normalizedName,
                platform,
                AccountStatus.ARCHIVED,
                createdAt,
                now,
                now);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
