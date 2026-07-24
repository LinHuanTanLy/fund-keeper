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

import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PendingReason;
import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionType;

@Entity
@Table(name = "fund_transactions")
class FundTransactionJpaEntity {

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

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "sell_mode", length = 32)
    private SellMode sellMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "expected_amount", precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "actual_received_amount", precision = 19, scale = 4)
    private BigDecimal actualReceivedAmount;

    @Column(name = "removed_cost", precision = 19, scale = 4)
    private BigDecimal removedCost;

    @Column(name = "realized_profit", precision = 19, scale = 4)
    private BigDecimal realizedProfit;

    @Column(precision = 24, scale = 8)
    private BigDecimal shares;

    @Column(name = "submitted_date", nullable = false)
    private LocalDate submittedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_period", nullable = false, length = 32)
    private SubmittedPeriod submittedPeriod;

    @Column(name = "effective_trade_date", nullable = false)
    private LocalDate effectiveTradeDate;

    @Column(name = "confirmed_date")
    private LocalDate confirmedDate;

    @Column(name = "nav_date")
    private LocalDate navDate;

    @Column(name = "unit_nav", precision = 18, scale = 8)
    private BigDecimal unitNav;

    @Column(name = "nav_source", length = 100)
    private String navSource;

    @Column(name = "fee_rate", precision = 12, scale = 8)
    private BigDecimal feeRate;

    @Column(name = "fee_source", length = 100)
    private String feeSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_reason", length = 64)
    private PendingReason pendingReason;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FundTransactionJpaEntity() {
    }

    private FundTransactionJpaEntity(FundTransaction transaction) {
        this.id = transaction.id();
        this.publicId = transaction.publicId();
        this.userId = transaction.userId();
        this.accountId = transaction.accountId();
        this.fundId = transaction.fundId();
        this.requestId = transaction.requestId();
        this.requestFingerprint = transaction.requestFingerprint();
        this.type = transaction.type();
        this.sellMode = transaction.sellMode();
        this.status = transaction.status();
        this.grossAmount = transaction.grossAmount();
        this.feeAmount = transaction.feeAmount();
        this.netAmount = transaction.netAmount();
        this.expectedAmount = transaction.expectedAmount();
        this.actualReceivedAmount =
                transaction.actualReceivedAmount();
        this.removedCost = transaction.removedCost();
        this.realizedProfit = transaction.realizedProfit();
        this.shares = transaction.shares();
        this.submittedDate = transaction.submittedDate();
        this.submittedPeriod = transaction.submittedPeriod();
        this.effectiveTradeDate = transaction.effectiveTradeDate();
        this.confirmedDate = transaction.confirmedDate();
        this.navDate = transaction.navDate();
        this.unitNav = transaction.unitNav();
        this.navSource = transaction.navSource();
        this.feeRate = transaction.feeRate();
        this.feeSource = transaction.feeSource();
        this.pendingReason = transaction.pendingReason();
        this.note = transaction.note();
        this.createdAt = transaction.createdAt();
        this.updatedAt = transaction.updatedAt();
    }

    static FundTransactionJpaEntity fromDomain(FundTransaction transaction) {
        return new FundTransactionJpaEntity(transaction);
    }

    FundTransaction toDomain() {
        return new FundTransaction(
                id,
                publicId,
                userId,
                accountId,
                fundId,
                requestId,
                requestFingerprint,
                type,
                sellMode,
                status,
                grossAmount,
                feeAmount,
                netAmount,
                expectedAmount,
                actualReceivedAmount,
                removedCost,
                realizedProfit,
                shares,
                submittedDate,
                submittedPeriod,
                effectiveTradeDate,
                confirmedDate,
                navDate,
                unitNav,
                navSource,
                feeRate,
                feeSource,
                pendingReason,
                note,
                createdAt,
                updatedAt);
    }
}
