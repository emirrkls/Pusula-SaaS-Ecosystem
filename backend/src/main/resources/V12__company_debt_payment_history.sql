ALTER TABLE company_debts
    ADD COLUMN IF NOT EXISTS expense_category VARCHAR(32),
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE company_debts
SET expense_category = 'OTHER'
WHERE expense_category IS NULL;

UPDATE company_debts
SET version = 0
WHERE version IS NULL;

ALTER TABLE company_debts
    ALTER COLUMN expense_category SET NOT NULL,
    ALTER COLUMN version SET NOT NULL;

CREATE TABLE IF NOT EXISTS company_debt_payments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    debt_id BIGINT NOT NULL,
    expense_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    expense_category VARCHAR(32) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_company_debt_payment_debt
        FOREIGN KEY (debt_id) REFERENCES company_debts(id),
    CONSTRAINT fk_company_debt_payment_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_company_debt_payments_debt_date
    ON company_debt_payments(company_id, debt_id, payment_date);
