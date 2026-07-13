-- App Store transaction ids are hashed in payment_events.token_hash for idempotency.
-- Hashed original transaction ids are stored as appstore:<sha256> on companies for ownership.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='companies' AND column_name='subscription_provider') THEN
        ALTER TABLE companies ADD COLUMN subscription_provider VARCHAR(32);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='companies' AND column_name='external_subscription_id') THEN
        ALTER TABLE companies ADD COLUMN external_subscription_id VARCHAR(255);
    END IF;
END $$;

ALTER TABLE companies
    ALTER COLUMN iyzico_subscription_id TYPE VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_event_provider_token_hash
    ON payment_events(provider, token_hash);

-- PostgreSQL permits multiple NULL values; the partial predicate also keeps legacy/unbound
-- companies outside this ownership constraint.
CREATE UNIQUE INDEX IF NOT EXISTS ux_companies_subscription_provider_external_id
    ON companies(subscription_provider, external_subscription_id)
    WHERE subscription_provider IS NOT NULL AND external_subscription_id IS NOT NULL;
