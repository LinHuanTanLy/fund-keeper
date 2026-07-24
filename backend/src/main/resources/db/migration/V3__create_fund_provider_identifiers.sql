CREATE TABLE fund_provider_identifiers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fund_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_fund_provider_identifiers PRIMARY KEY (id),
    CONSTRAINT uk_fund_provider_identifiers_fund_provider
        UNIQUE (fund_id, provider),
    CONSTRAINT uk_fund_provider_identifiers_provider_code
        UNIQUE (provider, provider_code),
    CONSTRAINT fk_fund_provider_identifiers_fund
        FOREIGN KEY (fund_id) REFERENCES funds (id)
        ON DELETE CASCADE
);

