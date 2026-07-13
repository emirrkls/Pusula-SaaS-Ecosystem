CREATE TABLE IF NOT EXISTS push_devices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    bundle_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    token_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_push_devices_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_push_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_push_devices_platform CHECK (platform IN ('IOS')),
    CONSTRAINT ck_push_devices_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_push_devices_token_hash
    ON push_devices(token_hash);

CREATE INDEX IF NOT EXISTS idx_push_devices_active_recipient
    ON push_devices(company_id, user_id, active, platform);
