-- Free-plan trial dates were legacy registration defaults, not a paid entitlement.
-- Preserve explicit read-only flags and manual suspensions.
ALTER TABLE users ADD COLUMN local_password_enabled BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE companies SET plan_type = 'CIRAK' WHERE plan_type IS NULL;
UPDATE companies SET subscription_status = 'ACTIVE'
WHERE COALESCE(plan_type, 'CIRAK') = 'CIRAK' AND subscription_status = 'TRIAL';
UPDATE companies SET trial_ends_at = NULL, subscription_expires_at = NULL
WHERE COALESCE(plan_type, 'CIRAK') = 'CIRAK';

CREATE TABLE social_auth_identities (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('GOOGLE', 'APPLE')),
    subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    verified_email VARCHAR(320),
    refresh_token_ciphertext TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_social_auth_provider_subject UNIQUE (provider, subject),
    CONSTRAINT uq_social_auth_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE apple_auth_challenges (
    id VARCHAR(36) PRIMARY KEY,
    nonce VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP
);
CREATE INDEX idx_apple_auth_challenge_expiry ON apple_auth_challenges(expires_at);
