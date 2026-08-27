ALTER TABLE service_tickets
    ADD COLUMN IF NOT EXISTS technician_private_note TEXT;

COMMENT ON COLUMN service_tickets.technician_private_note IS
    'Internal assignment instruction visible only to company admins and the currently assigned technician.';
