ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMP;
