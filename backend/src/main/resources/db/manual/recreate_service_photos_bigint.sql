-- VPS: Mevcut service_photos tablosu UUID ile oluşturulmuşsa backend (Long/BIGINT) ile uyuşmaz → HTTP 500.
-- Bu script tabloyu doğru şemayla yeniden oluşturur (mevcut fotoğraf kaydı yoksa güvenlidir).

DROP TABLE IF EXISTS service_photos;

CREATE TABLE service_photos (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES service_tickets(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('BEFORE', 'AFTER')),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_photos_ticket ON service_photos(ticket_id);
