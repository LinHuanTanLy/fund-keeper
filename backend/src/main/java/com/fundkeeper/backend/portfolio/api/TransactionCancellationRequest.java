package com.fundkeeper.backend.portfolio.api;

import jakarta.validation.constraints.Size;

public record TransactionCancellationRequest(
        @Size(max = 500)
        String reason) {
}
