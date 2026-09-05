ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS category VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS reference_id BIGINT;

ALTER TABLE notifications ALTER COLUMN message TYPE TEXT;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_category;
ALTER TABLE notifications ADD CONSTRAINT chk_notification_category CHECK (category IN
    ('NEW_SERVICE','SERVICE_RESCHEDULED','SERVICE_COMPLETED','CRITICAL_STOCK','IMPORTANT_NOTE','GENERAL'));

ALTER TABLE service_ticket_notes
    ADD COLUMN IF NOT EXISTS important BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_created
    ON notifications(company_id, user_id, is_read, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON notifications(company_id, reference_type, reference_id) WHERE is_deleted = FALSE;
