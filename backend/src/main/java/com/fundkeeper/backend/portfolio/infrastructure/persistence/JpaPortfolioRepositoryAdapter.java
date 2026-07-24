package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;

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
    public Optional<FundPosition> findPositionByAccountIdAndFundId(
            long accountId,
            long fundId) {
        return positionRepository.findByAccountIdAndFundId(accountId, fundId)
                .map(FundPositionJpaEntity::toDomain);
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
}
