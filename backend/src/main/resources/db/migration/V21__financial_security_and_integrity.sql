-- Commercial-device sales need enough metadata to separate revenue recognition,
-- immediate cash/card collection and current-account transfer.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS payment_method VARCHAR(32);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS source_type VARCHAR(40);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS source_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_expenses_company_source
    ON expenses(company_id, source_type, source_id);

-- Narrative fields are user-entered and must not silently fail at 255 chars.
ALTER TABLE expenses ALTER COLUMN description TYPE TEXT;
ALTER TABLE proposal_items ALTER COLUMN description TYPE TEXT;
ALTER TABLE customers ALTER COLUMN address TYPE TEXT;

UPDATE service_used_parts sup
SET buying_price_snapshot = i.buy_price
FROM inventory i
WHERE sup.inventory_id = i.id
  AND sup.buying_price_snapshot IS NULL;

UPDATE service_used_parts sup
SET selling_price_snapshot = COALESCE(i.sell_price, i.buy_price, 0)
FROM inventory i
WHERE sup.inventory_id = i.id
  AND sup.selling_price_snapshot IS NULL;

ALTER TABLE proposals ADD COLUMN IF NOT EXISTS generated_service_ticket_id BIGINT;
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_tickets ADD COLUMN IF NOT EXISTS customer_signature_path VARCHAR(500);
CREATE UNIQUE INDEX IF NOT EXISTS uq_proposals_generated_service_ticket
    ON proposals(generated_service_ticket_id)
    WHERE generated_service_ticket_id IS NOT NULL;

-- Active barcodes are unique per tenant after trimming/case-folding.
-- Preserve every row: suffix only the later duplicates before enforcing uniqueness.
WITH duplicate_barcodes AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY company_id, lower(trim(barcode))
               ORDER BY id
           ) AS duplicate_number
    FROM inventory
    WHERE barcode IS NOT NULL AND trim(barcode) <> '' AND is_deleted = false
)
UPDATE inventory i
SET barcode = left(trim(i.barcode), 220) || '-DUP-' || i.id
FROM duplicate_barcodes d
WHERE i.id = d.id AND d.duplicate_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_company_barcode_normalized_active
    ON inventory(company_id, lower(trim(barcode)))
    WHERE barcode IS NOT NULL AND trim(barcode) <> '' AND is_deleted = false;
