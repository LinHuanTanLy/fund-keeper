ALTER TABLE fund_transactions
    ADD COLUMN position_shares_before DECIMAL(24, 8);

ALTER TABLE fund_transactions
    ADD COLUMN position_cost_before DECIMAL(19, 4);

ALTER TABLE fund_transactions
    ADD COLUMN position_status_before VARCHAR(32);

ALTER TABLE fund_transactions
    ADD COLUMN position_holding_start_date_before DATE;

ALTER TABLE fund_transactions
    ADD COLUMN cancellation_reason VARCHAR(500);

ALTER TABLE fund_transactions
    ADD COLUMN cancelled_at TIMESTAMP(6);
