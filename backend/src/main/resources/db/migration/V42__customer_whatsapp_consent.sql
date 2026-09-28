ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS whatsapp_opt_in_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS whatsapp_opt_in_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS whatsapp_opt_out_at TIMESTAMP;

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS ck_customers_whatsapp_consent_source;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_whatsapp_consent_source CHECK (
        whatsapp_opt_in_source IS NULL OR whatsapp_opt_in_source IN (
            'WRITTEN_FORM',
            'VERBAL_CONFIRMATION',
            'DIGITAL_FORM',
            'WHATSAPP_CONVERSATION',
            'OTHER'
        )
    );

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS ck_customers_active_whatsapp_consent;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_active_whatsapp_consent CHECK (
        whatsapp_opt_in = FALSE OR (
            whatsapp_opt_in_at IS NOT NULL
            AND whatsapp_opt_in_source IS NOT NULL
            AND whatsapp_opt_out_at IS NULL
        )
    );

CREATE INDEX IF NOT EXISTS idx_customers_company_whatsapp_opt_in
    ON customers(company_id, id)
    WHERE whatsapp_opt_in = TRUE AND is_deleted = FALSE;
