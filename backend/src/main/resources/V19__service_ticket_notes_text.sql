-- Technician/service notes must not be constrained by PostgreSQL VARCHAR(255).
ALTER TABLE service_tickets
    ALTER COLUMN notes TYPE TEXT;
