package com.fundkeeper.backend.portfolio.api;

import jakarta.validation.constraints.Size;

import com.fundkeeper.backend.portfolio.application.SellCancellationCommand;

public record SellCancellationRequest(
        @Size(max = 500)
        String reason) {

    SellCancellationCommand toCommand() {
        return new SellCancellationCommand(reason);
    }
}
