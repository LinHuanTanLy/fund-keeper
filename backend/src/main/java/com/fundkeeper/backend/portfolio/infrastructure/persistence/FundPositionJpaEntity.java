package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;

@Entity
@Table(name = "fund_positions")
class FundPositionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "fund_id", nullable = false)
    private long fundId;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal shares;

    @Column(name = "remaining_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingCost;

    @Column(name = "average_unit_cost", nullable = false, precision = 18, scale = 8)
    private BigDecimal averageUnitCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PositionStatus status;

    @Column(name = "holding_start_date")
    private LocalDate holdingStartDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FundPositionJpaEntity() {
    }

    private FundPositionJpaEntity(FundPosition position) {
        this.id = position.id();
        this.publicId = position.publicId();
        this.userId = position.userId();
        this.accountId = position.accountId();
        this.fundId = position.fundId();
        apply(position);
        this.createdAt = position.createdAt();
    }

    static FundPositionJpaEntity fromDomain(FundPosition position) {
        return new FundPositionJpaEntity(position);
    }

    void apply(FundPosition position) {
        this.shares = position.shares();
        this.remainingCost = position.remainingCost();
        this.averageUnitCost = position.averageUnitCost();
        this.status = position.status();
        this.holdingStartDate = position.holdingStartDate();
        this.updatedAt = position.updatedAt();
    }

    FundPosition toDomain() {
        return new FundPosition(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                shares,
                remainingCost,
                averageUnitCost,
                status,
                holdingStartDate,
                createdAt,
                updatedAt);
    }
}
