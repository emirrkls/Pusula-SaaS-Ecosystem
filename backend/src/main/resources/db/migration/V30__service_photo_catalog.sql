ALTER TABLE service_photos
    ADD COLUMN IF NOT EXISTS note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS uploaded_by_name VARCHAR(255);

ALTER TABLE service_photos
    DROP CONSTRAINT IF EXISTS service_photos_type_check;

ALTER TABLE service_photos
    ADD CONSTRAINT service_photos_type_check
        CHECK (type IN (
            'BEFORE',
            'AFTER',
            'INDOOR_UNIT_SERIAL',
            'OUTDOOR_UNIT_SERIAL',
            'DEVICE_LABEL',
            'FAULT_DETAIL',
            'INSTALLATION',
            'OTHER'
        ));

CREATE INDEX IF NOT EXISTS idx_service_photos_ticket_uploaded
    ON service_photos(ticket_id, uploaded_at DESC);
