package com.fundkeeper.backend.portfolio.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PortfolioRepository {

    Optional<FundTransaction> findTransactionByUserIdAndRequestId(
            long userId,
            String requestId);

    Optional<FundTransaction> findTransactionByPublicIdAndUserId(
            String publicId,
            long userId);

    Optional<FundTransaction> findTransactionByPublicIdAndUserIdForUpdate(
            String publicId,
            long userId);

    TransactionPage findTransactions(TransactionQuery query);

    SellTransactionSummary summarizeSells(
            long userId,
            Collection<Long> accountIds,
            Long fundId);

    Map<Long, SellTransactionSummary> summarizeSellsByFund(
            long userId,
            Collection<Long> accountIds);

    Map<Long, SellTransactionSummary> summarizeSellsByAccount(
            long userId,
            Collection<Long> accountIds,
            long fundId);

    List<FundTransaction> findOpenTransactions(
            long userId,
            Collection<Long> accountIds,
            long fundId);

    List<FundTransaction> findOpenTransactions(
            long userId,
            Collection<Long> accountIds);

    Optional<FundPosition> findPositionByAccountIdAndFundId(
            long accountId,
            long fundId);

    boolean existsOpenSell(
            long userId,
            long accountId,
            long fundId);

    boolean existsOpenSell(
            long userId,
            long accountId);

    boolean existsPositionAffectingTransactionAfter(
            long userId,
            long accountId,
            long fundId,
            long transactionId);

    List<FundPosition> findPositionsByUserId(long userId);

    List<FundPosition> findPositionsByUserIdAndAccountId(
            long userId,
            long accountId);

    FundTransaction saveTransaction(FundTransaction transaction);

    FundTransaction updateTransaction(FundTransaction transaction);

    FundPosition savePosition(FundPosition position);

    void deletePosition(FundPosition position);
}
