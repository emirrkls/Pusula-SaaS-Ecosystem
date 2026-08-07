CREATE TABLE IF NOT EXISTS company_debt_additions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    debt_id BIGINT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    addition_date DATE NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_company_debt_addition_debt
        FOREIGN KEY (debt_id) REFERENCES company_debts(id)
);

CREATE INDEX IF NOT EXISTS idx_company_debt_additions_debt_date
    ON company_debt_additions(company_id, debt_id, addition_date, id);
