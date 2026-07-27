package com.fundkeeper.backend.portfolio.api;

import java.util.List;

import com.fundkeeper.backend.portfolio.application.TransactionPageDetails;

public record TransactionPageView(
        List<TransactionView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static TransactionPageView from(TransactionPageDetails details) {
        return new TransactionPageView(
                details.items()
                        .stream()
                        .map(TransactionView::from)
                        .toList(),
                details.page(),
                details.size(),
                details.totalElements(),
                details.totalPages());
    }
}
