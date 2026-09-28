ALTER TABLE whatsapp_business_integrations
    ADD COLUMN IF NOT EXISTS webhook_subscription_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS webhook_subscription_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS webhook_subscription_next_attempt_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS webhook_subscription_last_error VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS webhook_subscribed_at TIMESTAMP;

UPDATE whatsapp_business_integrations
SET webhook_subscription_status = 'PENDING',
    webhook_subscription_next_attempt_at = COALESCE(webhook_subscription_next_attempt_at, CURRENT_TIMESTAMP)
WHERE is_deleted = FALSE
  AND webhook_subscription_status <> 'SUBSCRIBED';

CREATE INDEX IF NOT EXISTS idx_whatsapp_subscription_retry
    ON whatsapp_business_integrations(webhook_subscription_status, webhook_subscription_next_attempt_at)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS whatsapp_message_outbox (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    ticket_id BIGINT,
    notification_type VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    recipient_phone VARCHAR(24) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    template_language VARCHAR(12) NOT NULL,
    parameters_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP,
    provider_message_id VARCHAR(160),
    provider_status VARCHAR(32),
    last_error VARCHAR(1000),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_whatsapp_outbox_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_whatsapp_outbox_ticket FOREIGN KEY (ticket_id) REFERENCES service_tickets(id),
    CONSTRAINT chk_whatsapp_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_outbox_due
    ON whatsapp_message_outbox(status, next_attempt_at, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_outbox_provider_message
    ON whatsapp_message_outbox(provider_message_id)
    WHERE provider_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS whatsapp_message_status_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL UNIQUE,
    company_id BIGINT,
    outbox_id BIGINT,
    provider_message_id VARCHAR(160) NOT NULL,
    phone_number_id VARCHAR(32),
    recipient_id VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    event_timestamp TIMESTAMP,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_whatsapp_status_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_whatsapp_status_outbox FOREIGN KEY (outbox_id) REFERENCES whatsapp_message_outbox(id)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_status_message
    ON whatsapp_message_status_events(provider_message_id, event_timestamp);
