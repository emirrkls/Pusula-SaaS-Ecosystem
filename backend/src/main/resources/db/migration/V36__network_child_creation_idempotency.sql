-- Persist account-creation receipts without retaining passwords or password fingerprints.
ALTER TABLE service_network_memberships ADD COLUMN creation_request_key VARCHAR(64);
ALTER TABLE service_network_memberships ADD COLUMN creation_admin_name VARCHAR(100);
ALTER TABLE service_network_memberships ADD COLUMN creation_username VARCHAR(100);
ALTER TABLE service_network_memberships ADD CONSTRAINT uq_network_child_creation
    UNIQUE (parent_company_id, creation_request_key);
