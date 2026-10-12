-- Stable ownership across devices, users of the same company and subscription renewals.
-- PostgreSQL 13+ provides gen_random_uuid() without an extension.
ALTER TABLE companies ADD COLUMN app_store_account_token UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX uq_company_app_store_account_token ON companies(app_store_account_token);
-- Some legacy installations keep payment_events under a separate DDL owner.
-- In that case the operator prepares this index as the table owner before
-- deployment; do not expand the application's database privileges.
DO $$
BEGIN
    IF to_regclass('idx_payment_event_subscription_owner') IS NULL THEN
        CREATE INDEX idx_payment_event_subscription_owner
            ON payment_events(provider, external_subscription_id, status);
    END IF;
END $$;
