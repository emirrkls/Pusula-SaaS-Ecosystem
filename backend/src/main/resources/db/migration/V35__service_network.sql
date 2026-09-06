-- Opt-in network entitlement; no existing customer subscription is changed.
CREATE TABLE service_network_policies (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id), enabled BOOLEAN NOT NULL DEFAULT FALSE,
    max_members INTEGER NOT NULL DEFAULT 0 CHECK (max_members BETWEEN 0 AND 10000),
    max_monthly_orders INTEGER NOT NULL DEFAULT 0 CHECK (max_monthly_orders BETWEEN 0 AND 1000000),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE service_network_memberships (
    id BIGSERIAL PRIMARY KEY, parent_company_id BIGINT NOT NULL REFERENCES companies(id),
    child_company_id BIGINT NOT NULL REFERENCES companies(id),
    parent_name VARCHAR(255) NOT NULL, child_name VARCHAR(255) NOT NULL, region VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('INVITED','ACTIVE','DECLINED','CLOSED')),
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CHECK (parent_company_id <> child_company_id)
);
CREATE UNIQUE INDEX uq_network_child_live ON service_network_memberships(child_company_id)
    WHERE status IN ('INVITED','ACTIVE');
CREATE INDEX ix_network_parent_status ON service_network_memberships(parent_company_id,status,id DESC);
CREATE INDEX ix_network_child_status ON service_network_memberships(child_company_id,status,id DESC);
CREATE TABLE service_network_orders (
    id BIGSERIAL PRIMARY KEY, membership_id BIGINT NOT NULL REFERENCES service_network_memberships(id),
    parent_company_id BIGINT NOT NULL REFERENCES companies(id), child_company_id BIGINT NOT NULL REFERENCES companies(id),
    parent_name VARCHAR(255) NOT NULL, child_name VARCHAR(255) NOT NULL, request_key VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL, customer_name VARCHAR(255) NOT NULL, customer_phone VARCHAR(255), customer_address VARCHAR(255),
    instruction VARCHAR(2000), scheduled_date TIMESTAMP NOT NULL, scheduled_end_date TIMESTAMP,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENT','ACCEPTED','REJECTED','CANCELLED')),
    accepted_ticket_id BIGINT UNIQUE REFERENCES service_tickets(id), resolution_note VARCHAR(1000),
    reported_ticket_status VARCHAR(255), reported_scheduled_date TIMESTAMP, reported_scheduled_end_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(parent_company_id,request_key), CHECK(parent_company_id <> child_company_id),
    CHECK(scheduled_end_date IS NULL OR scheduled_end_date > scheduled_date),
    CHECK((status = 'ACCEPTED') = (accepted_ticket_id IS NOT NULL))
);
CREATE INDEX ix_network_order_parent_date ON service_network_orders(parent_company_id,scheduled_date DESC,id DESC);
CREATE INDEX ix_network_order_child_date ON service_network_orders(child_company_id,scheduled_date DESC,id DESC);
CREATE INDEX ix_network_order_usage ON service_network_orders(parent_company_id,created_at);
CREATE INDEX ix_network_order_membership ON service_network_orders(membership_id,status);
CREATE TABLE service_network_order_events (
    id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES service_network_orders(id),
    actor_company_id BIGINT NOT NULL REFERENCES companies(id), actor_user_id BIGINT REFERENCES users(id),
    action VARCHAR(40) NOT NULL, note VARCHAR(1000), created_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_network_order_events ON service_network_order_events(order_id,id DESC);
