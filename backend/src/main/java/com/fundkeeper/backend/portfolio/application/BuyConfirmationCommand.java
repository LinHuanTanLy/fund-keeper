package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BuyConfirmationCommand(
        BigDecimal confirmedShares,
        LocalDate confirmedDate) {
}
