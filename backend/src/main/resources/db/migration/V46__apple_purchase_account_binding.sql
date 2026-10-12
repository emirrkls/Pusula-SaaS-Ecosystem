-- Stable ownership across devices, users of the same company and subscription renewals.
-- PostgreSQL 13+ provides gen_random_uuid() without an extension.
ALTER TABLE companies ADD COLUMN app_store_account_token UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX uq_company_app_store_account_token ON companies(app_store_account_token);
CREATE INDEX idx_payment_event_subscription_owner
    ON payment_events(provider, external_subscription_id, status);
