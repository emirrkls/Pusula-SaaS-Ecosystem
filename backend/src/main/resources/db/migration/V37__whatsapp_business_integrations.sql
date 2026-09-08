CREATE TABLE IF NOT EXISTS whatsapp_business_integrations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL UNIQUE,
    waba_id VARCHAR(32) NOT NULL,
    phone_number_id VARCHAR(32) NOT NULL,
    display_phone_number VARCHAR(32),
    verified_name VARCHAR(160),
    access_token_ciphertext TEXT NOT NULL,
    token_expires_at TIMESTAMP,
    status VARCHAR(24) NOT NULL DEFAULT 'CONNECTED',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_whatsapp_integration_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT chk_whatsapp_integration_status CHECK (status IN ('CONNECTED', 'EXPIRED', 'DISCONNECTED', 'ERROR'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_phone_number_active
    ON whatsapp_business_integrations(phone_number_id) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS whatsapp_onboarding_sessions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_whatsapp_session_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_whatsapp_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_sessions_company_created
    ON whatsapp_onboarding_sessions(company_id, created_at DESC);
