ALTER TABLE service_tickets
    ALTER COLUMN description TYPE TEXT;

ALTER TABLE service_tickets
    ALTER COLUMN notes TYPE TEXT;

ALTER TABLE proposal_items
    ADD COLUMN IF NOT EXISTS inventory_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_proposal_items_inventory_id
    ON proposal_items (inventory_id);
