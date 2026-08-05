-- Separate business event dates from the technical updated_at timestamp.
ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS collection_date DATE;

-- Preserve the historical behavior for existing completed tickets.
UPDATE service_tickets
SET completed_at = updated_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

-- CURRENT_ACCOUNT is debt, not liquid income; it has no collection date yet.
UPDATE service_tickets
SET collection_date = CAST(COALESCE(completed_at, updated_at) AS DATE)
WHERE status = 'COMPLETED'
  AND COALESCE(payment_method, 'CASH') <> 'CURRENT_ACCOUNT'
  AND collection_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_tickets_completed_at
    ON service_tickets(company_id, completed_at DESC)
    WHERE status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_service_tickets_collection_date
    ON service_tickets(company_id, collection_date DESC)
    WHERE status = 'COMPLETED' AND collection_date IS NOT NULL;
