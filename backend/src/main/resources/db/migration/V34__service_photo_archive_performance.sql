CREATE INDEX IF NOT EXISTS idx_service_photos_type_ticket
    ON service_photos(type, ticket_id);

CREATE INDEX IF NOT EXISTS idx_service_tickets_company_photo_date
    ON service_tickets(company_id,
        (COALESCE(completed_at, updated_at, scheduled_date, created_at)) DESC)
    WHERE is_deleted = false;
