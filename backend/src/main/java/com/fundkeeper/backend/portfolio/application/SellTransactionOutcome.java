package com.fundkeeper.backend.portfolio.application;

public record SellTransactionOutcome(
        TransactionDetails details,
        boolean idempotentReplay) {
}
