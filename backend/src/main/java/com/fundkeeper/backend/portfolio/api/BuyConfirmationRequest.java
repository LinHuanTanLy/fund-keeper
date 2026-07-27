package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import com.fundkeeper.backend.portfolio.application.BuyConfirmationCommand;

public record BuyConfirmationRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 8)
        BigDecimal confirmedShares,

        LocalDate confirmedDate) {

    BuyConfirmationCommand toCommand() {
        return new BuyConfirmationCommand(
                confirmedShares,
                confirmedDate);
    }
}
