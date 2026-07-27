package com.fundkeeper.backend.portfolio.infrastructure.persistence;

interface FundSellSummaryProjection extends SellSummaryProjection {

    long getFundId();
}
