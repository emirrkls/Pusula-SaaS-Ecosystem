CREATE TABLE google_play_purchases (
    id VARCHAR(64) PRIMARY KEY,
    version BIGINT,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    encrypted_token TEXT,
    product_id VARCHAR(32) NOT NULL,
    base_plan_id VARCHAR(64),
    deferred_product_id VARCHAR(32),
    state VARCHAR(64),
    acknowledgement_state VARCHAR(48),
    expires_at TIMESTAMP,
    verified_at TIMESTAMP NOT NULL,
    next_check_at TIMESTAMP NOT NULL,
    retired BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_google_play_purchase_due ON google_play_purchases(next_check_at);
CREATE INDEX idx_google_play_purchase_company ON google_play_purchases(company_id);
