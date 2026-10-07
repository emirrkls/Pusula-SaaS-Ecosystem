-- Additive migration. Existing plans and business records are not modified.
ALTER TABLE companies ADD COLUMN app_store_transaction_hash VARCHAR(64);
ALTER TABLE companies ADD COLUMN app_store_purchase_date TIMESTAMP;
ALTER TABLE companies ADD COLUMN app_store_signed_date BIGINT;
ALTER TABLE companies ADD COLUMN app_store_environment VARCHAR(16);
-- Serialize ownership across different transaction IDs in the same subscription.
-- Preflight must confirm no duplicate active Apple bindings before deployment.
CREATE UNIQUE INDEX uq_company_app_store_subscription ON companies(external_subscription_id)
WHERE is_deleted = FALSE AND subscription_provider = 'APP_STORE' AND external_subscription_id IS NOT NULL;

CREATE TABLE app_store_notifications (
    id VARCHAR(36) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    type VARCHAR(64) NOT NULL,
    subtype VARCHAR(64),
    environment VARCHAR(16) NOT NULL,
    signed_date BIGINT NOT NULL,
    original_transaction_hash VARCHAR(64),
    transaction_hash VARCHAR(64),
    plan_type VARCHAR(16),
    purchase_date TIMESTAMP,
    expires_date TIMESTAMP,
    grace_expires_date TIMESTAMP,
    revoked BOOLEAN NOT NULL,
    subscription_status VARCHAR(32),
    processing_status VARCHAR(32) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    next_retry_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    company_id BIGINT REFERENCES companies(id)
);
CREATE INDEX idx_app_store_notifications_pending ON app_store_notifications(processing_status, next_retry_at);
