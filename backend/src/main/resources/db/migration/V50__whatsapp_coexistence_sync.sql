ALTER TABLE whatsapp_business_integrations
    ADD COLUMN coexistence BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN coexistence_generation VARCHAR(36),
    ADD COLUMN coexistence_started_at TIMESTAMP,
    ADD COLUMN coexistence_lifecycle_at TIMESTAMP;
ALTER TABLE whatsapp_business_integrations
    ADD CONSTRAINT uq_whatsapp_integration_tenant UNIQUE(id, company_id);

ALTER TABLE whatsapp_onboarding_sessions
    ADD COLUMN completed_integration_id BIGINT REFERENCES whatsapp_business_integrations(id),
    ADD COLUMN completed_generation VARCHAR(36);

CREATE TABLE whatsapp_coexistence_sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    integration_id BIGINT NOT NULL REFERENCES whatsapp_business_integrations(id),
    generation VARCHAR(36) NOT NULL,
    sync_type VARCHAR(32) NOT NULL CHECK (sync_type IN ('smb_app_state_sync', 'history')),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    request_id VARCHAR(160),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    requested_at TIMESTAMP,
    deadline_at TIMESTAMP NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    error_code VARCHAR(64),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_whatsapp_sync_generation UNIQUE(integration_id, generation, sync_type),
    CONSTRAINT chk_whatsapp_sync_status CHECK
        (status IN ('PENDING','REQUESTING','ACCEPTED','RECEIVING','COMPLETE','DECLINED','UNKNOWN','FAILED'))
);
CREATE INDEX idx_whatsapp_coex_sync_due ON whatsapp_coexistence_sync_jobs(status, next_attempt_at);

-- Isolated, encrypted mirror; never imports contacts into customers or grants consent.
CREATE TABLE whatsapp_coexistence_records (
    id BIGSERIAL PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    generation VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('CONTACT','MESSAGE')),
    source VARCHAR(16),
    record_key VARCHAR(64) NOT NULL,
    peer_key VARCHAR(64),
    payload_ciphertext TEXT,
    event_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_whatsapp_coex_record UNIQUE(integration_id, generation, kind, record_key),
    CONSTRAINT fk_whatsapp_coex_tenant FOREIGN KEY(integration_id, company_id)
        REFERENCES whatsapp_business_integrations(id, company_id)
);
CREATE INDEX idx_whatsapp_coex_thread ON whatsapp_coexistence_records
    (company_id, integration_id, generation, kind, peer_key, event_at);
