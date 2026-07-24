package com.fundkeeper.backend.account.application;

public interface AccountBusinessActivity {

    boolean hasCurrentPositionOrPendingTransaction(
            long userId,
            long accountId);
}
