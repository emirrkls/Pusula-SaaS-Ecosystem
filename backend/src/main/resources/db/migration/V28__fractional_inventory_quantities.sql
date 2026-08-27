ALTER TABLE inventory
    ALTER COLUMN quantity TYPE NUMERIC(14, 3) USING quantity::NUMERIC,
    ALTER COLUMN quantity SET DEFAULT 0,
    ALTER COLUMN critical_level TYPE NUMERIC(14, 3) USING critical_level::NUMERIC,
    ALTER COLUMN critical_level SET DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(16) NOT NULL DEFAULT 'ADET';

ALTER TABLE vehicle_stocks
    ALTER COLUMN quantity TYPE NUMERIC(14, 3) USING quantity::NUMERIC;

ALTER TABLE service_used_parts
    ALTER COLUMN quantity_used TYPE NUMERIC(14, 3) USING quantity_used::NUMERIC,
    ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(16) NOT NULL DEFAULT 'ADET';

UPDATE service_used_parts used_part
SET unit_of_measure = inventory.unit_of_measure
FROM inventory
WHERE used_part.inventory_id = inventory.id;

ALTER TABLE inventory
    ADD CONSTRAINT ck_inventory_unit_of_measure
    CHECK (unit_of_measure IN ('ADET', 'KG', 'GRAM', 'METRE', 'LITRE'));

ALTER TABLE service_used_parts
    ADD CONSTRAINT ck_service_used_parts_unit_of_measure
    CHECK (unit_of_measure IN ('ADET', 'KG', 'GRAM', 'METRE', 'LITRE'));
