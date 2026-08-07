-- Current-account collections are cash movements against an already recognized
-- service sale. They must remain visible in collection reports without being
-- counted as a second sale in completion-based revenue/profit reports.
ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS current_account_payment BOOLEAN;

UPDATE service_tickets
SET current_account_payment = false
WHERE current_account_payment IS NULL;

-- Backfill rows created by the current-account payment endpoint and the audited
-- historical payment schedules entered before this explicit classification
-- existed.
UPDATE service_tickets
SET current_account_payment = true
WHERE current_account_payment = false
  AND (
      lower(COALESCE(description, '')) LIKE lower('Cari hesap ödemesi -%')
      OR COALESCE(notes, '') LIKE '%NECDET-40VILLA-PAYMENT-SCHEDULE%'
      OR lower(COALESCE(notes, '')) LIKE '%cari tahsilatı%'
  );

ALTER TABLE service_tickets
    ALTER COLUMN current_account_payment SET DEFAULT false,
    ALTER COLUMN current_account_payment SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_service_tickets_current_account_payment
    ON service_tickets(company_id, current_account_payment)
    WHERE current_account_payment = true AND is_deleted = false;
