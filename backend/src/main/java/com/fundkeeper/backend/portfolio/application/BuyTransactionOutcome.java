package com.fundkeeper.backend.portfolio.application;

public record BuyTransactionOutcome(
        TransactionDetails details,
        boolean idempotentReplay) {
}
