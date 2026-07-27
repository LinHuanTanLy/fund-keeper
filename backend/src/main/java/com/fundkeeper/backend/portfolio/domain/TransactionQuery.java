package com.fundkeeper.backend.portfolio.domain;

public record TransactionQuery(
        long userId,
        Long accountId,
        Long fundId,
        TransactionType type,
        TransactionStatus status,
        int page,
        int size) {
}
