-- VPS: ddl-auto=none kullanıldığı için bu tablo elle oluşturulmalıdır.
-- psql -h localhost -U pusula_db -d pusula_db -f create_service_photos_if_missing.sql

CREATE TABLE IF NOT EXISTS service_photos (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    url TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photos_ticket FOREIGN KEY (ticket_id) REFERENCES service_tickets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_service_photos_ticket ON service_photos(ticket_id);
