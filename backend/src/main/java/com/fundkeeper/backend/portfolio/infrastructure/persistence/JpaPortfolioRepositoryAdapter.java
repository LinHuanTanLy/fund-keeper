package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.SellTransactionSummary;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.domain.TransactionPage;
import com.fundkeeper.backend.portfolio.domain.TransactionQuery;
import com.fundkeeper.backend.portfolio.domain.TransactionType;

@Repository
public class JpaPortfolioRepositoryAdapter implements PortfolioRepository {

    private final SpringDataFundTransactionRepository transactionRepository;
    private final SpringDataFundPositionRepository positionRepository;

    JpaPortfolioRepositoryAdapter(
            SpringDataFundTransactionRepository transactionRepository,
            SpringDataFundPositionRepository positionRepository) {
        this.transactionRepository = transactionRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    public Optional<FundTransaction> findTransactionByUserIdAndRequestId(
            long userId,
            String requestId) {
        return transactionRepository.findByUserIdAndRequestId(userId, requestId)
                .map(FundTransactionJpaEntity::toDomain);
    }

    @Override
    public Optional<FundTransaction> findTransactionByPublicIdAndUserId(
            String publicId,
            long userId) {
        return transactionRepository.findByPublicIdAndUserId(publicId, userId)
                .map(FundTransactionJpaEntity::toDomain);
    }

    @Override
    public Optional<FundTransaction>
            findTransactionByPublicIdAndUserIdForUpdate(
                    String publicId,
                    long userId) {
        return transactionRepository
                .findForUpdateByPublicIdAndUserId(publicId, userId)
                .map(FundTransactionJpaEntity::toDomain);
    }

    @Override
    public TransactionPage findTransactions(TransactionQuery query) {
        var specification =
                (org.springframework.data.jpa.domain.Specification<
                        FundTransactionJpaEntity>)
                (root, criteriaQuery, builder) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(builder.equal(
                            root.get("userId"),
                            query.userId()));
                    if (query.accountId() != null) {
                        predicates.add(builder.equal(
                                root.get("accountId"),
                                query.accountId()));
                    }
                    if (query.fundId() != null) {
                        predicates.add(builder.equal(
                                root.get("fundId"),
                                query.fundId()));
                    }
                    if (query.type() != null) {
                        predicates.add(builder.equal(
                                root.get("type"),
                                query.type()));
                    }
                    if (query.status() != null) {
                        predicates.add(builder.equal(
                                root.get("status"),
                                query.status()));
                    }
                    return builder.and(
                            predicates.toArray(Predicate[]::new));
                };
        var pageRequest = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));
        var result = transactionRepository.findAll(
                specification,
                pageRequest);
        return new TransactionPage(
                result.getContent()
                        .stream()
                        .map(FundTransactionJpaEntity::toDomain)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public SellTransactionSummary summarizeSells(
            long userId,
            Collection<Long> accountIds,
            Long fundId) {
        if (accountIds.isEmpty()) {
            return new SellTransactionSummary(
                    0,
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    0);
        }
        SellSummaryProjection result = transactionRepository.summarizeSells(
                userId,
                accountIds,
                fundId,
                TransactionType.SELL,
                TransactionStatus.CONFIRMED,
                List.of(
                        TransactionStatus.PENDING,
                        TransactionStatus.ESTIMATED));
        return new SellTransactionSummary(
                result.getConfirmedSellCount(),
                money(result.getTotalActualReceivedAmount()),
                money(result.getTotalRemovedCost()),
                money(result.getTotalRealizedProfit()),
                result.getOpenSellCount());
    }

    @Override
    public Map<Long, SellTransactionSummary> summarizeSellsByFund(
            long userId,
            Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SellTransactionSummary> summaries =
                new LinkedHashMap<>();
        transactionRepository.summarizeSellsByFund(
                        userId,
                        accountIds,
                        TransactionType.SELL,
                        TransactionStatus.CONFIRMED,
                        List.of(
                                TransactionStatus.PENDING,
                                TransactionStatus.ESTIMATED))
                .forEach(result -> summaries.put(
                        result.getFundId(),
                        new SellTransactionSummary(
                                result.getConfirmedSellCount(),
                                money(result
                                        .getTotalActualReceivedAmount()),
                                money(result.getTotalRemovedCost()),
                                money(result.getTotalRealizedProfit()),
                                result.getOpenSellCount())));
        return Map.copyOf(summaries);
    }

    @Override
    public Optional<FundPosition> findPositionByAccountIdAndFundId(
            long accountId,
            long fundId) {
        return positionRepository.findByAccountIdAndFundId(accountId, fundId)
                .map(FundPositionJpaEntity::toDomain);
    }

    @Override
    public boolean existsOpenSell(
            long userId,
            long accountId,
            long fundId) {
        return transactionRepository
                .existsByUserIdAndAccountIdAndFundIdAndTypeAndStatusIn(
                        userId,
                        accountId,
                        fundId,
                        TransactionType.SELL,
                        List.of(
                                TransactionStatus.PENDING,
                                TransactionStatus.ESTIMATED));
    }

    @Override
    public boolean existsOpenSell(
            long userId,
            long accountId) {
        return transactionRepository
                .existsByUserIdAndAccountIdAndTypeAndStatusIn(
                        userId,
                        accountId,
                        TransactionType.SELL,
                        List.of(
                                TransactionStatus.PENDING,
                                TransactionStatus.ESTIMATED));
    }

    @Override
    public List<FundPosition> findPositionsByUserId(long userId) {
        return positionRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(FundPositionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<FundPosition> findPositionsByUserIdAndAccountId(
            long userId,
            long accountId) {
        return positionRepository
                .findAllByUserIdAndAccountIdOrderByCreatedAtAsc(
                        userId,
                        accountId)
                .stream()
                .map(FundPositionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public FundTransaction saveTransaction(FundTransaction transaction) {
        if (transaction.id() != null) {
            throw new IllegalArgumentException(
                    "Updating transactions is not supported by this slice");
        }
        return transactionRepository
                .saveAndFlush(FundTransactionJpaEntity.fromDomain(transaction))
                .toDomain();
    }

    @Override
    public FundTransaction updateTransaction(
            FundTransaction transaction) {
        if (transaction.id() == null) {
            throw new IllegalArgumentException(
                    "A persisted transaction is required");
        }
        FundTransactionJpaEntity entity = transactionRepository
                .findById(transaction.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Fund transaction no longer exists"));
        entity.apply(transaction);
        return transactionRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public FundPosition savePosition(FundPosition position) {
        if (position.id() == null) {
            return positionRepository
                    .saveAndFlush(FundPositionJpaEntity.fromDomain(position))
                    .toDomain();
        }
        FundPositionJpaEntity entity = positionRepository
                .findById(position.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Fund position no longer exists"));
        entity.apply(position);
        return positionRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public void deletePosition(FundPosition position) {
        if (position.id() == null) {
            return;
        }
        positionRepository.deleteById(position.id());
        positionRepository.flush();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
