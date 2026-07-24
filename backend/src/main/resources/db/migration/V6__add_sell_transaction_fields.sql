ALTER TABLE fund_transactions
    ADD COLUMN sell_mode VARCHAR(32);

ALTER TABLE fund_transactions
    ADD COLUMN expected_amount DECIMAL(19, 4);

ALTER TABLE fund_transactions
    ADD COLUMN actual_received_amount DECIMAL(19, 4);

ALTER TABLE fund_transactions
    ADD COLUMN removed_cost DECIMAL(19, 4);

ALTER TABLE fund_transactions
    ADD COLUMN realized_profit DECIMAL(19, 4);

CREATE INDEX idx_fund_transactions_open_sell
    ON fund_transactions
        (user_id, account_id, fund_id, transaction_type, status);
