ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS financial_treatment VARCHAR(32);

UPDATE expenses
SET financial_treatment = 'OPERATING_EXPENSE'
WHERE financial_treatment IS NULL;

UPDATE expenses e
SET financial_treatment = 'SERVICE_DIRECT_EXPENSE'
WHERE EXISTS (
    SELECT 1
    FROM service_ticket_expenses ste
    WHERE ste.finance_expense_id = e.id
);

UPDATE expenses e
SET financial_treatment = 'CASH_ONLY'
WHERE EXISTS (
    SELECT 1
    FROM company_debt_payments cdp
    WHERE cdp.expense_id = e.id
)
OR LOWER(e.description) LIKE LOWER('Borç Ödemesi:%');

ALTER TABLE expenses
    ALTER COLUMN financial_treatment SET DEFAULT 'OPERATING_EXPENSE',
    ALTER COLUMN financial_treatment SET NOT NULL;

ALTER TABLE expenses
    DROP CONSTRAINT IF EXISTS chk_expenses_financial_treatment;

ALTER TABLE expenses
    ADD CONSTRAINT chk_expenses_financial_treatment
    CHECK (financial_treatment IN ('OPERATING_EXPENSE', 'SERVICE_DIRECT_EXPENSE', 'CASH_ONLY'));
