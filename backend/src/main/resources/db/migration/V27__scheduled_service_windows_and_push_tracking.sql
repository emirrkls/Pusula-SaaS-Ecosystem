ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS scheduled_end_date TIMESTAMP,
    ADD COLUMN IF NOT EXISTS assignment_notification_sent_at TIMESTAMP;

-- Do not send a burst for already-due assignments when this migration is deployed.
-- Existing assignments more than 24 hours away remain eligible for the new reminder.
UPDATE service_tickets
SET assignment_notification_sent_at = CURRENT_TIMESTAMP
WHERE assigned_technician_id IS NOT NULL
  AND status IN ('ASSIGNED', 'IN_PROGRESS')
  AND (scheduled_date IS NULL OR scheduled_date <= CURRENT_TIMESTAMP + INTERVAL '24 hours');

CREATE INDEX IF NOT EXISTS idx_service_tickets_assignment_notification_due
    ON service_tickets (scheduled_date)
    WHERE assigned_technician_id IS NOT NULL
      AND assignment_notification_sent_at IS NULL
      AND status IN ('ASSIGNED', 'IN_PROGRESS');
