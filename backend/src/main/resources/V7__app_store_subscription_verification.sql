-- App Store verification uses the existing payment_events idempotency model.
-- Apple original transaction ids are stored hashed in payment_events.token_hash.

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
