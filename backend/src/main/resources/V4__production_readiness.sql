-- =====================================================
--  PUSULA SaaS MIGRATION V4: Production Readiness
--  - isReadOnly field for expired subscriptions
--  - Seed plans set, default company plan update
--  - Performance indexes
-- =====================================================

-- 1. Add isReadOnly to companies (subscription cutoff)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='companies' AND column_name='is_read_only') THEN
        ALTER TABLE companies ADD COLUMN is_read_only BOOLEAN DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='companies' AND column_name='subscription_expires_at') THEN
        ALTER TABLE companies ADD COLUMN subscription_expires_at TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='companies' AND column_name='iyzico_subscription_id') THEN
        ALTER TABLE companies ADD COLUMN iyzico_subscription_id VARCHAR(100);
    END IF;
END $$;

-- 2. Set the existing company to PATRON plan (production bootstrap)
UPDATE companies SET plan_type = 'PATRON', is_read_only = false
WHERE plan_type IS NULL OR plan_type = 'CIRAK';

-- 3. Performance indexes for admin dashboard aggregation
CREATE INDEX IF NOT EXISTS idx_tickets_status_company
    ON service_tickets(company_id, status);

CREATE INDEX IF NOT EXISTS idx_tickets_assigned_tech
    ON service_tickets(assigned_technician_id) WHERE assigned_technician_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tickets_updated_at
    ON service_tickets(updated_at DESC) WHERE status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_expenses_company_date
    ON expenses(company_id, date);

CREATE INDEX IF NOT EXISTS idx_current_accounts_company_balance
    ON current_accounts(company_id) WHERE balance > 0;
