CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_users_email UNIQUE (email_normalized)
);

CREATE TABLE fund_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL,
    active_name_normalized VARCHAR(100),
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    archived_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_fund_accounts PRIMARY KEY (id),
    CONSTRAINT uk_fund_accounts_public_id UNIQUE (public_id),
    CONSTRAINT uk_fund_accounts_active_name
        UNIQUE (user_id, active_name_normalized),
    CONSTRAINT fk_fund_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_fund_accounts_user_status
    ON fund_accounts (user_id, status);

CREATE TABLE auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    last_used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
    CONSTRAINT uk_auth_sessions_public_id UNIQUE (public_id),
    CONSTRAINT uk_auth_sessions_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_auth_sessions_user_active
    ON auth_sessions (user_id, revoked_at, expires_at);
