-- Additive: existing published clients and historical tickets retain zero-discount semantics.
ALTER TABLE service_tickets ADD COLUMN IF NOT EXISTS discount_type VARCHAR(16);
ALTER TABLE service_tickets ADD COLUMN IF NOT EXISTS discount_value NUMERIC(14, 2);
ALTER TABLE service_tickets ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14, 2);
ALTER TABLE service_tickets ADD CONSTRAINT ck_service_ticket_discount_type
    CHECK (discount_type IS NULL OR discount_type IN ('AMOUNT', 'PERCENT'));
ALTER TABLE service_tickets ADD CONSTRAINT ck_service_ticket_discount_nonnegative
    CHECK (COALESCE(discount_value, 0) >= 0 AND COALESCE(discount_amount, 0) >= 0
        AND (discount_type <> 'PERCENT' OR discount_value <= 100));
