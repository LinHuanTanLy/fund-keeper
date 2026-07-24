package com.fundkeeper.backend.portfolio.application;

import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.portfolio.domain.FundPosition;

public record PositionDetails(
        FundPosition position,
        FundAccount account,
        FundDefinition fund) {
}
