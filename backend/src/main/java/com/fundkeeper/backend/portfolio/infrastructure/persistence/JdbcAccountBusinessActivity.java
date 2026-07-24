package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.account.application.AccountBusinessActivity;

@Repository
public class JdbcAccountBusinessActivity implements AccountBusinessActivity {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountBusinessActivity(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasCurrentPositionOrPendingTransaction(
            long userId,
            long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*)
                       FROM fund_positions
                      WHERE user_id = ?
                        AND account_id = ?
                        AND shares > 0)
                  + (SELECT COUNT(*)
                       FROM fund_transactions
                      WHERE user_id = ?
                        AND account_id = ?
                        AND status = 'PENDING')
                """,
                Integer.class,
                userId,
                accountId,
                userId,
                accountId);
        return count != null && count > 0;
    }
}
