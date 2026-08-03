ALTER TABLE funds
    ADD COLUMN trading_mode VARCHAR(32) NOT NULL DEFAULT 'OFF_EXCHANGE'
        AFTER category;

UPDATE funds
   SET trading_mode = 'EXCHANGE_TRADED'
 WHERE name LIKE '%ETF%'
   AND name NOT LIKE '%联接%'
   AND (code LIKE '15%' OR code LIKE '5%');

CREATE INDEX idx_funds_trading_mode
    ON funds (trading_mode, code);
