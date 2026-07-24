package com.fundkeeper.backend.portfolio.domain;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    Optional<FundTransaction> findTransactionByUserIdAndRequestId(
            long userId,
            String requestId);

    Optional<FundTransaction> findTransactionByPublicIdAndUserId(
            String publicId,
            long userId);

    Optional<FundPosition> findPositionByAccountIdAndFundId(
            long accountId,
            long fundId);

    List<FundPosition> findPositionsByUserId(long userId);

    List<FundPosition> findPositionsByUserIdAndAccountId(
            long userId,
            long accountId);

    FundTransaction saveTransaction(FundTransaction transaction);

    FundPosition savePosition(FundPosition position);
}
