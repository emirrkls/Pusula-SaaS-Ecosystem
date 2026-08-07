UPDATE inventory
SET critical_level = 0
WHERE critical_level IS NULL;

ALTER TABLE inventory
    ALTER COLUMN critical_level SET DEFAULT 0,
    ALTER COLUMN critical_level SET NOT NULL;
