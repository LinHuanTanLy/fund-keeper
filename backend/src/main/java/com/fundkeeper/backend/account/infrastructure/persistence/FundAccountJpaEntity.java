package com.fundkeeper.backend.account.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.AccountStatus;
import com.fundkeeper.backend.account.domain.FundAccount;

@Entity
@Table(name = "fund_accounts")
class FundAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "active_name_normalized", length = 100)
    private String activeNormalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FundAccountJpaEntity() {
    }

    private FundAccountJpaEntity(FundAccount account) {
        this.id = account.id();
        this.publicId = account.publicId();
        this.userId = account.userId();
        this.name = account.name();
        this.normalizedName = account.normalizedName();
        this.activeNormalizedName = activeName(account);
        this.platform = account.platform();
        this.status = account.status();
        this.createdAt = account.createdAt();
        this.updatedAt = account.updatedAt();
        this.archivedAt = account.archivedAt();
    }

    static FundAccountJpaEntity fromDomain(FundAccount account) {
        return new FundAccountJpaEntity(account);
    }

    void apply(FundAccount account) {
        this.name = account.name();
        this.normalizedName = account.normalizedName();
        this.activeNormalizedName = activeName(account);
        this.platform = account.platform();
        this.status = account.status();
        this.updatedAt = account.updatedAt();
        this.archivedAt = account.archivedAt();
    }

    FundAccount toDomain() {
        return new FundAccount(
                id,
                publicId,
                userId,
                name,
                normalizedName,
                platform,
                status,
                createdAt,
                updatedAt,
                archivedAt);
    }

    private static String activeName(FundAccount account) {
        return account.status() == AccountStatus.ACTIVE
                ? account.normalizedName()
                : null;
    }
}
