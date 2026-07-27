package com.fundkeeper.backend.portfolio.application;

import java.util.List;

public record TransactionPageDetails(
        List<TransactionDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
