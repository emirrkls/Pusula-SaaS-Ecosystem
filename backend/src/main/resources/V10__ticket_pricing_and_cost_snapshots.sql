-- Separate what was sold from what was collected for newly completed services.
-- Existing completed rows intentionally remain NULL in the new pricing columns so
-- reports can keep their legacy financial semantics until they are reconciled.
ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS parts_total NUMERIC(38, 2),
    ADD COLUMN IF NOT EXISTS invoice_total NUMERIC(38, 2),
    ADD COLUMN IF NOT EXISTS outstanding_amount NUMERIC(38, 2);

-- The table already contains labor_cost from the original schema. The application
-- maps that legacy column as the customer-facing labor/service fee.

ALTER TABLE service_used_parts
    ADD COLUMN IF NOT EXISTS buying_price_snapshot NUMERIC(38, 2);

-- Best available historical baseline. New rows always capture the price at the
-- moment the part is attached to the service ticket.
UPDATE service_used_parts used_part
SET buying_price_snapshot = inventory.buy_price
FROM inventory
WHERE used_part.inventory_id = inventory.id
  AND used_part.buying_price_snapshot IS NULL;
