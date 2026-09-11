ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS billing_party_amount NUMERIC(14, 2);

UPDATE service_tickets
SET billing_party_amount = COALESCE(outstanding_amount, invoice_total, collected_amount, 0)
WHERE billing_party_id IS NOT NULL
  AND billing_party_amount IS NULL;

ALTER TABLE service_tickets DROP CONSTRAINT IF EXISTS ck_service_ticket_billing_responsibility;
ALTER TABLE service_tickets
    ADD CONSTRAINT ck_service_ticket_billing_responsibility
    CHECK (billing_responsibility IS NULL OR billing_responsibility IN ('CUSTOMER', 'ORGANIZATION', 'INTERNAL', 'SPLIT'));
