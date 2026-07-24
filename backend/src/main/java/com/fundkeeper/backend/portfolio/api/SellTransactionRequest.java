package com.fundkeeper.backend.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fundkeeper.backend.portfolio.application.SellTransactionCommand;
import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;

public record SellTransactionRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._:-]+")
        String requestId,

        @NotBlank
        @Size(max = 36)
        String accountId,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String fundCode,

        @NotNull
        SellMode sellMode,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal expectedAmount,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal actualReceivedAmount,

        @NotNull
        LocalDate submittedDate,

        @NotNull
        SubmittedPeriod submittedPeriod,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 8)
        BigDecimal confirmedShares,

        LocalDate confirmedDate,

        @Size(max = 500)
        String note) {

    SellTransactionCommand toCommand() {
        return new SellTransactionCommand(
                requestId,
                accountId,
                fundCode,
                sellMode,
                expectedAmount,
                actualReceivedAmount,
                submittedDate,
                submittedPeriod,
                confirmedShares,
                confirmedDate,
                note);
    }
}
