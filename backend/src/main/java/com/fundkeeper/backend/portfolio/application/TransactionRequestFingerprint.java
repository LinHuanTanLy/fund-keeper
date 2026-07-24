package com.fundkeeper.backend.portfolio.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class TransactionRequestFingerprint {

    public String create(BuyTransactionCommand command) {
        String canonical = String.join(
                "\u001f",
                value(command.accountPublicId()),
                value(command.fundCode()),
                decimal(command.amount()),
                value(command.submittedDate()),
                value(command.submittedPeriod()),
                decimal(command.confirmedShares()),
                value(command.confirmedDate()),
                value(command.note()));
        return digest(canonical);
    }

    public String create(SellTransactionCommand command) {
        String canonical = String.join(
                "\u001f",
                value(command.accountPublicId()),
                value(command.fundCode()),
                value(command.sellMode()),
                decimal(command.expectedAmount()),
                decimal(command.actualReceivedAmount()),
                value(command.submittedDate()),
                value(command.submittedPeriod()),
                decimal(command.confirmedShares()),
                value(command.confirmedDate()),
                value(command.note()));
        return digest(canonical);
    }

    private String digest(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception);
        }
    }

    private String decimal(java.math.BigDecimal value) {
        return value == null
                ? ""
                : value.stripTrailingZeros().toPlainString();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
