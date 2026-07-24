CREATE TABLE portfolio_import_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT,
    batch_id VARCHAR(64) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    import_type VARCHAR(32) NOT NULL,
    snapshot_mode VARCHAR(32),
    snapshot_at TIMESTAMP(6),
    content_hash VARCHAR(64) NOT NULL,
    plan_hash VARCHAR(64),
    request_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    importable_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    preflight_json LONGTEXT NOT NULL,
    commit_result_json LONGTEXT,
    committed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_portfolio_import_batches PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_import_batches_public_id UNIQUE (public_id),
    CONSTRAINT uk_portfolio_import_batches_user_batch
        UNIQUE (user_id, batch_id),
    CONSTRAINT fk_portfolio_import_batches_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_portfolio_import_batches_account
        FOREIGN KEY (account_id) REFERENCES fund_accounts (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_portfolio_import_batches_user_status
    ON portfolio_import_batches (user_id, status, updated_at);

CREATE INDEX idx_portfolio_import_batches_account_snapshot
    ON portfolio_import_batches (account_id, status, snapshot_at);
