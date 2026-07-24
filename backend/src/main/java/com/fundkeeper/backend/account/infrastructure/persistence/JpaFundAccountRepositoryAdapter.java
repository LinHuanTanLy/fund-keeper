package com.fundkeeper.backend.account.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.account.domain.AccountStatus;
import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;

@Repository
public class JpaFundAccountRepositoryAdapter implements FundAccountRepository {

    private final SpringDataFundAccountRepository repository;

    JpaFundAccountRepositoryAdapter(SpringDataFundAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<FundAccount> findAllByUserId(
            long userId,
            boolean includeArchived) {
        var entities = includeArchived
                ? repository.findAllByUserIdOrderByCreatedAtAsc(userId)
                : repository.findAllByUserIdAndStatusOrderByCreatedAtAsc(
                        userId,
                        AccountStatus.ACTIVE);
        return entities.stream()
                .map(FundAccountJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<FundAccount> findByPublicIdAndUserId(
            String publicId,
            long userId) {
        return repository.findByPublicIdAndUserId(publicId, userId)
                .map(FundAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<FundAccount> findByPublicIdAndUserIdForUpdate(
            String publicId,
            long userId) {
        return repository.findByPublicIdAndUserIdForUpdate(publicId, userId)
                .map(FundAccountJpaEntity::toDomain);
    }

    @Override
    public boolean existsActiveName(
            long userId,
            String normalizedName,
            String excludedPublicId) {
        return repository.existsByUserIdAndNormalizedNameAndStatusAndPublicIdNot(
                userId,
                normalizedName,
                AccountStatus.ACTIVE,
                excludedPublicId);
    }

    @Override
    public long countActiveByUserIdForUpdate(long userId) {
        return repository.findActiveByUserIdForUpdate(
                userId,
                AccountStatus.ACTIVE).size();
    }

    @Override
    public FundAccount save(FundAccount account) {
        if (account.id() == null) {
            return repository
                    .saveAndFlush(FundAccountJpaEntity.fromDomain(account))
                    .toDomain();
        }
        FundAccountJpaEntity entity = repository.findById(account.id())
                .orElseThrow(() -> new IllegalStateException("Fund account no longer exists"));
        entity.apply(account);
        return repository.saveAndFlush(entity).toDomain();
    }
}
