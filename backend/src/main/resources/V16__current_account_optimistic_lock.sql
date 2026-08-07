ALTER TABLE current_accounts
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE current_accounts
SET version = 0
WHERE version IS NULL;

ALTER TABLE current_accounts
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;
