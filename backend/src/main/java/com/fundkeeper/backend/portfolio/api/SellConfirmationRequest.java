package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import com.fundkeeper.backend.portfolio.application.SellConfirmationCommand;

public record SellConfirmationRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal actualReceivedAmount,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 8)
        BigDecimal confirmedShares,

        LocalDate confirmedDate) {

    SellConfirmationCommand toCommand() {
        return new SellConfirmationCommand(
                actualReceivedAmount,
                confirmedShares,
                confirmedDate);
    }
}
