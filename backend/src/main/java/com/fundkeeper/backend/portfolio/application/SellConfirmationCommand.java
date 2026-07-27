package com.fundkeeper.backend.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SellConfirmationCommand(
        BigDecimal actualReceivedAmount,
        BigDecimal confirmedShares,
        LocalDate confirmedDate) {
}
