ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS max_company_admins INTEGER,
    ADD COLUMN IF NOT EXISTS max_vehicles INTEGER,
    ADD COLUMN IF NOT EXISTS max_commercial_devices INTEGER,
    ADD COLUMN IF NOT EXISTS audit_retention_days INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS uq_plans_name ON plans(name);

UPDATE plans
SET max_company_admins = CASE name
        WHEN 'CIRAK' THEN 1
        WHEN 'USTA' THEN 2
        WHEN 'PATRON' THEN 5
    END,
    max_technicians = CASE name
        WHEN 'CIRAK' THEN 1
        WHEN 'USTA' THEN 5
        WHEN 'PATRON' THEN 20
    END,
    max_customers = CASE name
        WHEN 'CIRAK' THEN 100
        WHEN 'USTA' THEN 2500
        WHEN 'PATRON' THEN 10000
    END,
    max_monthly_tickets = CASE name
        WHEN 'CIRAK' THEN 50
        WHEN 'USTA' THEN 500
        WHEN 'PATRON' THEN 2000
    END,
    max_monthly_proposals = CASE name
        WHEN 'CIRAK' THEN 0
        WHEN 'USTA' THEN 100
        WHEN 'PATRON' THEN 500
    END,
    max_inventory_items = CASE name
        WHEN 'CIRAK' THEN 100
        WHEN 'USTA' THEN 2500
        WHEN 'PATRON' THEN 10000
    END,
    storage_limit_mb = CASE name
        WHEN 'CIRAK' THEN 250
        WHEN 'USTA' THEN 5120
        WHEN 'PATRON' THEN 25600
    END,
    max_vehicles = CASE name
        WHEN 'CIRAK' THEN 0
        WHEN 'USTA' THEN 5
        WHEN 'PATRON' THEN 25
    END,
    max_commercial_devices = CASE name
        WHEN 'CIRAK' THEN 0
        WHEN 'USTA' THEN 0
        WHEN 'PATRON' THEN 10000
    END,
    audit_retention_days = CASE name
        WHEN 'CIRAK' THEN 90
        WHEN 'USTA' THEN 730
        WHEN 'PATRON' THEN NULL
    END
WHERE name IN ('CIRAK', 'USTA', 'PATRON');

INSERT INTO plan_features (plan_id, feature_key, enabled)
SELECT p.id, feature.feature_key, feature.enabled
FROM plans p
CROSS JOIN LATERAL (
    VALUES
        ('SERVICE_PDF_EXPORT', TRUE),
        ('ADVANCED_REPORT_EXPORT', p.name IN ('USTA', 'PATRON')),
        ('BUSINESS_ASSETS', p.name IN ('USTA', 'PATRON'))
) AS feature(feature_key, enabled)
ON CONFLICT (plan_id, feature_key)
DO UPDATE SET enabled = EXCLUDED.enabled;

UPDATE plan_features pf
SET enabled = CASE
    WHEN pf.feature_key = 'PDF_EXPORT' THEN TRUE
    WHEN pf.feature_key = 'COMPANY_DEBT_TRACKING' THEN p.name IN ('USTA', 'PATRON')
    WHEN pf.feature_key = 'WHATSAPP_INTEGRATION' THEN p.name = 'PATRON'
    ELSE pf.enabled
END
FROM plans p
WHERE pf.plan_id = p.id
  AND pf.feature_key IN ('PDF_EXPORT', 'COMPANY_DEBT_TRACKING', 'WHATSAPP_INTEGRATION');
