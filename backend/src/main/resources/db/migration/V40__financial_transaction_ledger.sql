CREATE TABLE financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    category VARCHAR(64) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    effective_date DATE NOT NULL,
    payment_method VARCHAR(32),
    party_id BIGINT REFERENCES account_parties(id),
    current_account_id BIGINT REFERENCES current_accounts(id),
    counterparty_name VARCHAR(255) NOT NULL,
    description TEXT,
    source_type VARCHAR(64),
    source_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    reversal_of_id BIGINT REFERENCES financial_transactions(id),
    idempotency_key VARCHAR(128),
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_financial_transaction_direction CHECK (direction IN ('INCOME','EXPENSE')),
    CONSTRAINT ck_financial_transaction_category CHECK (category IN ('CURRENT_ACCOUNT_COLLECTION')),
    CONSTRAINT ck_financial_transaction_status CHECK (status IN ('POSTED','REVERSED')),
    CONSTRAINT ck_financial_transaction_amount CHECK (amount > 0)
);

CREATE INDEX idx_financial_transactions_company_date
    ON financial_transactions(company_id, effective_date DESC, id DESC);
CREATE INDEX idx_financial_transactions_current_account
    ON financial_transactions(company_id, current_account_id, effective_date DESC);
CREATE UNIQUE INDEX uq_financial_transactions_idempotency
    ON financial_transactions(company_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_financial_transactions_source
    ON financial_transactions(company_id, source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL
      AND source_type = 'CURRENT_ACCOUNT_TRANSACTION';

-- Move successful historical current-account collections out of the synthetic
-- service-ticket model. The old rows remain for traceability but reports switch
-- to this immutable money ledger, preventing duplicate revenue recognition.
INSERT INTO financial_transactions (
    company_id, direction, category, amount, effective_date, payment_method,
    party_id, current_account_id, counterparty_name, description,
    source_type, source_id, status, created_at
)
SELECT ct.company_id,
       'INCOME',
       'CURRENT_ACCOUNT_COLLECTION',
       ABS(ct.amount),
       ct.effective_date,
       ct.payment_method,
       ct.party_id,
       ct.current_account_id,
       p.display_name,
       COALESCE(NULLIF(ct.description, ''), 'Cari hesap tahsilatı'),
       'CURRENT_ACCOUNT_TRANSACTION',
       ct.id,
       'POSTED',
       ct.created_at
FROM current_account_transactions ct
JOIN account_parties p ON p.id = ct.party_id AND p.company_id = ct.company_id
WHERE ct.transaction_type = 'PAYMENT'
  AND ct.source_type = 'CURRENT_ACCOUNT_PAYMENT'
  AND ct.amount < 0
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft
      WHERE ft.company_id = ct.company_id
        AND ft.source_type = 'CURRENT_ACCOUNT_TRANSACTION'
        AND ft.source_id = ct.id
  );
