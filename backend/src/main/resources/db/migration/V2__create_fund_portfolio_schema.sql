CREATE TABLE funds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(6) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    supported BOOLEAN NOT NULL,
    confirmation_delay_trading_days INTEGER,
    data_source VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_funds PRIMARY KEY (id),
    CONSTRAINT uk_funds_code UNIQUE (code)
);

CREATE TABLE fund_trading_days (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    is_open BOOLEAN NOT NULL,
    data_source VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_fund_trading_days PRIMARY KEY (id),
    CONSTRAINT uk_fund_trading_days_market_date
        UNIQUE (market, trade_date)
);

CREATE INDEX idx_fund_trading_days_lookup
    ON fund_trading_days (market, is_open, trade_date);

CREATE TABLE fund_navs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fund_id BIGINT NOT NULL,
    nav_date DATE NOT NULL,
    unit_nav DECIMAL(18, 8) NOT NULL,
    data_source VARCHAR(100) NOT NULL,
    published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_fund_navs PRIMARY KEY (id),
    CONSTRAINT uk_fund_navs_fund_date UNIQUE (fund_id, nav_date),
    CONSTRAINT fk_fund_navs_fund
        FOREIGN KEY (fund_id) REFERENCES funds (id)
        ON DELETE RESTRICT
);

CREATE TABLE fund_purchase_fee_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fund_id BIGINT NOT NULL,
    minimum_amount DECIMAL(19, 4) NOT NULL,
    maximum_amount DECIMAL(19, 4),
    fee_rate DECIMAL(12, 8) NOT NULL,
    calculation_method VARCHAR(32) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    data_source VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_fund_purchase_fee_rules PRIMARY KEY (id),
    CONSTRAINT fk_fund_purchase_fee_rules_fund
        FOREIGN KEY (fund_id) REFERENCES funds (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_fund_purchase_fee_rules_lookup
    ON fund_purchase_fee_rules
        (fund_id, effective_from, effective_to, minimum_amount, maximum_amount);

CREATE TABLE fund_positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    fund_id BIGINT NOT NULL,
    shares DECIMAL(24, 8) NOT NULL,
    remaining_cost DECIMAL(19, 4) NOT NULL,
    average_unit_cost DECIMAL(18, 8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    holding_start_date DATE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_fund_positions PRIMARY KEY (id),
    CONSTRAINT uk_fund_positions_public_id UNIQUE (public_id),
    CONSTRAINT uk_fund_positions_account_fund UNIQUE (account_id, fund_id),
    CONSTRAINT fk_fund_positions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_fund_positions_account
        FOREIGN KEY (account_id) REFERENCES fund_accounts (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_fund_positions_fund
        FOREIGN KEY (fund_id) REFERENCES funds (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_fund_positions_user_account
    ON fund_positions (user_id, account_id);

CREATE TABLE fund_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    fund_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    gross_amount DECIMAL(19, 4) NOT NULL,
    fee_amount DECIMAL(19, 4),
    net_amount DECIMAL(19, 4),
    shares DECIMAL(24, 8),
    submitted_date DATE NOT NULL,
    submitted_period VARCHAR(32) NOT NULL,
    effective_trade_date DATE NOT NULL,
    confirmed_date DATE,
    nav_date DATE,
    unit_nav DECIMAL(18, 8),
    nav_source VARCHAR(100),
    fee_rate DECIMAL(12, 8),
    fee_source VARCHAR(100),
    pending_reason VARCHAR(64),
    note VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_fund_transactions PRIMARY KEY (id),
    CONSTRAINT uk_fund_transactions_public_id UNIQUE (public_id),
    CONSTRAINT uk_fund_transactions_user_request UNIQUE (user_id, request_id),
    CONSTRAINT fk_fund_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_fund_transactions_account
        FOREIGN KEY (account_id) REFERENCES fund_accounts (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_fund_transactions_fund
        FOREIGN KEY (fund_id) REFERENCES funds (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_fund_transactions_user_created
    ON fund_transactions (user_id, created_at);

CREATE INDEX idx_fund_transactions_account_fund_status
    ON fund_transactions (account_id, fund_id, status);
