CREATE TABLE service_ticket_notes (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    service_ticket_id BIGINT NOT NULL REFERENCES service_tickets(id),
    author_user_id BIGINT,
    author_name VARCHAR(255) NOT NULL,
    note_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_service_ticket_note_type CHECK (note_type IN ('WORK_LOG', 'CLOSURE'))
);

CREATE INDEX idx_service_ticket_notes_ticket_company
    ON service_ticket_notes(service_ticket_id, company_id, created_at);

ALTER TABLE service_tickets
    DROP CONSTRAINT IF EXISTS service_tickets_payment_method_check;

ALTER TABLE service_tickets
    ADD CONSTRAINT service_tickets_payment_method_check
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CREDIT_CARD', 'CURRENT_ACCOUNT', 'WARRANTY'));
