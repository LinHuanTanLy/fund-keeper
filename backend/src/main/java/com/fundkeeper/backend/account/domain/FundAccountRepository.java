package com.fundkeeper.backend.account.domain;

import java.util.List;
import java.util.Optional;

public interface FundAccountRepository {

    List<FundAccount> findAllByUserId(long userId, boolean includeArchived);

    Optional<FundAccount> findByPublicIdAndUserId(String publicId, long userId);

    Optional<FundAccount> findByPublicIdAndUserIdForUpdate(
            String publicId,
            long userId);

    boolean existsActiveName(
            long userId,
            String normalizedName,
            String excludedPublicId);

    long countActiveByUserIdForUpdate(long userId);

    FundAccount save(FundAccount account);
}
