ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS work_progress_reason VARCHAR(40),
    ADD COLUMN IF NOT EXISTS work_progress_note TEXT,
    ADD COLUMN IF NOT EXISTS last_rescheduled_at TIMESTAMP;

ALTER TABLE service_tickets DROP CONSTRAINT IF EXISTS chk_service_ticket_progress_reason;
ALTER TABLE service_tickets ADD CONSTRAINT chk_service_ticket_progress_reason CHECK (
    work_progress_reason IS NULL OR work_progress_reason IN
    ('PART_PENDING','CUSTOMER_AVAILABILITY','CUSTOMER_APPROVAL','EXTERNAL_SUPPORT','RESCHEDULED','OTHER')
);

CREATE TABLE IF NOT EXISTS service_ticket_reschedules (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    service_ticket_id BIGINT NOT NULL REFERENCES service_tickets(id) ON DELETE CASCADE,
    old_scheduled_date TIMESTAMP,
    old_scheduled_end_date TIMESTAMP,
    new_scheduled_date TIMESTAMP NOT NULL,
    new_scheduled_end_date TIMESTAMP,
    reason VARCHAR(40) NOT NULL,
    note TEXT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_by_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reschedule_reason CHECK (reason IN
      ('PART_PENDING','CUSTOMER_AVAILABILITY','CUSTOMER_APPROVAL','EXTERNAL_SUPPORT','RESCHEDULED','OTHER')),
    CONSTRAINT chk_reschedule_window CHECK (new_scheduled_end_date IS NULL OR new_scheduled_end_date > new_scheduled_date)
);
CREATE INDEX IF NOT EXISTS idx_ticket_reschedules_ticket_date
    ON service_ticket_reschedules(service_ticket_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ticket_reschedules_company_date
    ON service_ticket_reschedules(company_id, created_at DESC);
