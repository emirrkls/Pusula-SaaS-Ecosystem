ALTER TABLE service_used_parts
    ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_used_part_company_client_request
    ON service_used_parts (company_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
