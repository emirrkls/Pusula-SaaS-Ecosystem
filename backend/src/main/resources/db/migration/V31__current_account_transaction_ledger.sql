CREATE TABLE current_account_transactions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    current_account_id BIGINT NOT NULL REFERENCES current_accounts(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    effective_date DATE NOT NULL,
    description TEXT,
    payment_method VARCHAR(32),
    source_type VARCHAR(64),
    source_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_current_account_transaction_type CHECK (transaction_type IN (
        'CHARGE', 'PAYMENT', 'DISCOUNT', 'ADJUSTMENT', 'REVERSAL'
    )),
    CONSTRAINT ck_current_account_transaction_amount CHECK (amount <> 0)
);

CREATE INDEX idx_current_account_transactions_account_date
    ON current_account_transactions(current_account_id, effective_date DESC, created_at DESC, id DESC);

CREATE INDEX idx_current_account_transactions_company_customer
    ON current_account_transactions(company_id, customer_id, effective_date DESC);

-- Reconstruct historical charges from completed service tickets.
INSERT INTO current_account_transactions (
    company_id, current_account_id, customer_id, transaction_type, amount,
    effective_date, description, payment_method, source_type, source_id, created_at
)
SELECT a.company_id,
       a.id,
       a.customer_id,
       'CHARGE',
       CASE
           WHEN COALESCE(t.outstanding_amount, 0) > 0 THEN t.outstanding_amount
           WHEN t.payment_method = 'CURRENT_ACCOUNT' AND COALESCE(t.current_account_payment, false) = false
               THEN COALESCE(t.invoice_total, t.collected_amount, 0)
           ELSE 0
       END,
       COALESCE(t.completed_at, t.updated_at, t.created_at, NOW())::date,
       'Servis fişi #' || t.id || COALESCE(' - ' || NULLIF(t.description, ''), ''),
       t.payment_method,
       'SERVICE_TICKET',
       t.id,
       COALESCE(t.completed_at, t.updated_at, t.created_at, NOW())
FROM current_accounts a
JOIN service_tickets t
  ON t.company_id = a.company_id
 AND t.customer_id = a.customer_id
WHERE COALESCE(t.is_deleted, false) = false
  AND COALESCE(t.current_account_payment, false) = false
  AND (
      COALESCE(t.outstanding_amount, 0) > 0
      OR (t.payment_method = 'CURRENT_ACCOUNT' AND COALESCE(t.invoice_total, t.collected_amount, 0) > 0)
  )
;

-- A reopened ticket has already removed its former receivable from the live
-- balance. Preserve that fact as a dated reversal in the reconstructed ledger.
INSERT INTO current_account_transactions (
    company_id, current_account_id, customer_id, transaction_type, amount,
    effective_date, description, source_type, source_id, created_at
)
SELECT a.company_id,
       a.id,
       a.customer_id,
       'REVERSAL',
       -ABS(CASE
           WHEN COALESCE(t.outstanding_amount, 0) > 0 THEN t.outstanding_amount
           WHEN t.payment_method = 'CURRENT_ACCOUNT' THEN COALESCE(t.invoice_total, t.collected_amount, 0)
           ELSE 0
       END),
       t.reopened_at::date,
       'Yeniden açılan servis fişi #' || t.id,
       'SERVICE_TICKET_REOPEN',
       t.id,
       t.reopened_at
FROM current_accounts a
JOIN service_tickets t
  ON t.company_id = a.company_id
 AND t.customer_id = a.customer_id
WHERE COALESCE(t.is_deleted, false) = false
  AND t.reopened_at IS NOT NULL
  AND t.status <> 'COMPLETED'
  AND (
      COALESCE(t.outstanding_amount, 0) > 0
      OR (t.payment_method = 'CURRENT_ACCOUNT' AND COALESCE(t.invoice_total, t.collected_amount, 0) > 0)
  )
;

-- Reconstruct historical cash/card collections made against a current account.
INSERT INTO current_account_transactions (
    company_id, current_account_id, customer_id, transaction_type, amount,
    effective_date, description, payment_method, source_type, source_id, created_at
)
SELECT a.company_id,
       a.id,
       a.customer_id,
       'PAYMENT',
       -ABS(t.collected_amount),
       COALESCE(t.collection_date, t.completed_at::date, t.updated_at::date, t.created_at::date, CURRENT_DATE),
       COALESCE(NULLIF(t.description, ''), 'Cari hesap tahsilatı'),
       t.payment_method,
       'CURRENT_ACCOUNT_PAYMENT',
       t.id,
       COALESCE(t.completed_at, t.updated_at, t.created_at, NOW())
FROM current_accounts a
JOIN service_tickets t
  ON t.company_id = a.company_id
 AND t.customer_id = a.customer_id
WHERE COALESCE(t.is_deleted, false) = false
  AND COALESCE(t.current_account_payment, false) = true
  AND COALESCE(t.collected_amount, 0) > 0
;

-- Preserve the authoritative current balance. Any legacy discount/manual edit that
-- cannot be reconstructed is represented explicitly instead of silently disappearing.
INSERT INTO current_account_transactions (
    company_id, current_account_id, customer_id, transaction_type, amount,
    effective_date, description, source_type, source_id, created_at
)
SELECT a.company_id,
       a.id,
       a.customer_id,
       'ADJUSTMENT',
       a.balance - COALESCE(s.ledger_balance, 0),
       COALESCE(a.last_updated::date, CURRENT_DATE),
       'Geçmiş bakiye mutabakatı',
       'MIGRATION_RECONCILIATION',
       a.id,
       COALESCE(a.last_updated, NOW())
FROM current_accounts a
LEFT JOIN (
    SELECT current_account_id, SUM(amount) AS ledger_balance
    FROM current_account_transactions
    GROUP BY current_account_id
) s ON s.current_account_id = a.id
WHERE a.balance <> COALESCE(s.ledger_balance, 0)
;
