package com.fundkeeper.backend.portfolio.domain;

import java.util.List;

public record TransactionPage(
        List<FundTransaction> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
