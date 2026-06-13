-- Backfill missing org codes for legacy companies.
-- Keeps existing codes intact and uses a deterministic unique format from company id.

UPDATE companies
SET org_code = 'PUS-' || LPAD(CAST(id AS TEXT), 6, '0')
WHERE org_code IS NULL OR TRIM(org_code) = '';
