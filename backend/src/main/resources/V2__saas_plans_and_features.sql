-- =====================================================
--  PUSULA SaaS MIGRATION: Plans, Features & Usage Tracking
--  Version: V2__saas_plans_and_features.sql
-- =====================================================

-- Paket Tanımları
CREATE TABLE IF NOT EXISTS plans (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    price_monthly DECIMAL(10,2),
    price_yearly DECIMAL(10,2),
    max_technicians INTEGER,
    max_customers INTEGER,
    max_monthly_tickets INTEGER,
    max_monthly_proposals INTEGER,
    max_inventory_items INTEGER,
    storage_limit_mb INTEGER,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Plan'a bağlı özellik anahtarları (Feature Flags)
CREATE TABLE IF NOT EXISTS plan_features (
    id SERIAL PRIMARY KEY,
    plan_id INTEGER REFERENCES plans(id),
    feature_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT false,
    metadata JSONB DEFAULT '{}',
    UNIQUE(plan_id, feature_key)
);

-- Kullanım Takibi (Aylık Kota Sayacı)
CREATE TABLE IF NOT EXISTS usage_tracking (
    id SERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    usage_type VARCHAR(50) NOT NULL,
    current_count INTEGER DEFAULT 0,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(company_id, usage_type, period_start)
);

-- Şirket tablosuna SaaS alanları ekle (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='companies' AND column_name='plan_type') THEN
        ALTER TABLE companies ADD COLUMN plan_type VARCHAR(20) DEFAULT 'CIRAK';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='companies' AND column_name='org_code') THEN
        ALTER TABLE companies ADD COLUMN org_code VARCHAR(20) UNIQUE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='companies' AND column_name='trial_ends_at') THEN
        ALTER TABLE companies ADD COLUMN trial_ends_at TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='companies' AND column_name='billing_email') THEN
        ALTER TABLE companies ADD COLUMN billing_email VARCHAR(255);
    END IF;
END $$;

-- =====================================================
--  SEED DATA: Paket Tanımları
-- =====================================================
INSERT INTO plans (name, display_name, price_monthly, price_yearly, max_technicians, max_customers, max_monthly_tickets, max_monthly_proposals, max_inventory_items, storage_limit_mb)
VALUES
    ('CIRAK',  'Çırak Paketi',   0,     0,     1,  50,  30,  10,  50,   100),
    ('USTA',   'Usta Paketi',    299,   2999,  5,  500, 300, 100, 500,  1000),
    ('PATRON', 'Patron Paketi',  799,   7999,  -1, -1,  -1,  -1,  -1,   10000)
ON CONFLICT DO NOTHING;

-- =====================================================
--  SEED DATA: Feature Flags per Plan
-- =====================================================
-- Çırak Paketi (plan_id=1)
INSERT INTO plan_features (plan_id, feature_key, enabled) VALUES
    (1, 'SERVICE_TICKETS',       true),
    (1, 'CUSTOMER_MANAGEMENT',   true),
    (1, 'BASIC_INVENTORY',       true),
    (1, 'FINANCE_MODULE',        false),
    (1, 'PDF_EXPORT',            false),
    (1, 'PROPOSAL_MODULE',       false),
    (1, 'VEHICLE_TRACKING',      false),
    (1, 'AUDIT_LOGS',            false),
    (1, 'WHATSAPP_INTEGRATION',  false),
    (1, 'MULTI_TECHNICIAN',      false),
    (1, 'COMMERCIAL_DEVICES',    false),
    (1, 'COMPANY_DEBT_TRACKING', false),
    (1, 'DAILY_CLOSING',         false),
    (1, 'CUSTOM_BRANDING',       false)
ON CONFLICT DO NOTHING;

-- Usta Paketi (plan_id=2)
INSERT INTO plan_features (plan_id, feature_key, enabled) VALUES
    (2, 'SERVICE_TICKETS',       true),
    (2, 'CUSTOMER_MANAGEMENT',   true),
    (2, 'BASIC_INVENTORY',       true),
    (2, 'FINANCE_MODULE',        true),
    (2, 'PDF_EXPORT',            true),
    (2, 'PROPOSAL_MODULE',       true),
    (2, 'VEHICLE_TRACKING',      true),
    (2, 'AUDIT_LOGS',            true),
    (2, 'WHATSAPP_INTEGRATION',  true),
    (2, 'MULTI_TECHNICIAN',      true),
    (2, 'COMMERCIAL_DEVICES',    false),
    (2, 'COMPANY_DEBT_TRACKING', false),
    (2, 'DAILY_CLOSING',         true),
    (2, 'CUSTOM_BRANDING',       false)
ON CONFLICT DO NOTHING;

-- Patron Paketi (plan_id=3)
INSERT INTO plan_features (plan_id, feature_key, enabled) VALUES
    (3, 'SERVICE_TICKETS',       true),
    (3, 'CUSTOMER_MANAGEMENT',   true),
    (3, 'BASIC_INVENTORY',       true),
    (3, 'FINANCE_MODULE',        true),
    (3, 'PDF_EXPORT',            true),
    (3, 'PROPOSAL_MODULE',       true),
    (3, 'VEHICLE_TRACKING',      true),
    (3, 'AUDIT_LOGS',            true),
    (3, 'WHATSAPP_INTEGRATION',  true),
    (3, 'MULTI_TECHNICIAN',      true),
    (3, 'COMMERCIAL_DEVICES',    true),
    (3, 'COMPANY_DEBT_TRACKING', true),
    (3, 'DAILY_CLOSING',         true),
    (3, 'CUSTOM_BRANDING',       true)
ON CONFLICT DO NOTHING;

-- Index for usage_tracking queries
CREATE INDEX IF NOT EXISTS idx_usage_tracking_company_period
    ON usage_tracking(company_id, usage_type, period_start);
