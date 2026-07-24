package com.fundkeeper.backend.portfolio.application;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;

public record TransactionDetails(
        FundTransaction transaction,
        FundAccount account,
        FundDefinition fund) {
}
