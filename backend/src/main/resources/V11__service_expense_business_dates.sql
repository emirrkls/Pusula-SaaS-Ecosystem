-- Keep service-ticket external expenses and finance-ledger rows linked and dated
-- to the business day of the service rather than the day of data entry.
ALTER TABLE service_ticket_expenses
    ADD COLUMN IF NOT EXISTS expense_date DATE,
    ADD COLUMN IF NOT EXISTS finance_expense_id BIGINT;

UPDATE service_ticket_expenses ste
SET expense_date = COALESCE(
        st.completed_at::date,
        st.scheduled_date::date,
        ste.created_at::date,
        CURRENT_DATE)
FROM service_tickets st
WHERE st.id = ste.service_ticket_id
  AND ste.expense_date IS NULL;

UPDATE service_ticket_expenses
SET expense_date = COALESCE(created_at::date, CURRENT_DATE)
WHERE expense_date IS NULL;

-- Existing rows were created in pairs but had no foreign key. Match them
-- deterministically by ticket-specific description, company, amount and order.
WITH service_rows AS (
    SELECT ste.id,
           ste.company_id,
           ste.amount,
           'Servis Gideri #' || ste.service_ticket_id || ': ' || ste.description AS finance_description,
           ROW_NUMBER() OVER (
               PARTITION BY ste.company_id, ste.service_ticket_id, ste.description, ste.amount
               ORDER BY ste.id
           ) AS row_number
    FROM service_ticket_expenses ste
    WHERE ste.finance_expense_id IS NULL
),
finance_rows AS (
    SELECT e.id,
           e.company_id,
           e.amount,
           e.description,
           ROW_NUMBER() OVER (
               PARTITION BY e.company_id, e.description, e.amount
               ORDER BY e.id
           ) AS row_number
    FROM expenses e
    WHERE e.category = 'MATERIAL'
      AND e.description LIKE 'Servis Gideri #%'
),
matched_rows AS (
    SELECT service_rows.id AS service_expense_id,
           finance_rows.id AS finance_expense_id
    FROM service_rows
    JOIN finance_rows
      ON finance_rows.company_id = service_rows.company_id
     AND finance_rows.description = service_rows.finance_description
     AND finance_rows.amount = service_rows.amount
     AND finance_rows.row_number = service_rows.row_number
)
UPDATE service_ticket_expenses ste
SET finance_expense_id = matched_rows.finance_expense_id
FROM matched_rows
WHERE ste.id = matched_rows.service_expense_id;

UPDATE expenses e
SET date = ste.expense_date
FROM service_ticket_expenses ste
WHERE ste.finance_expense_id = e.id
  AND e.date IS DISTINCT FROM ste.expense_date;

ALTER TABLE service_ticket_expenses
    ALTER COLUMN expense_date SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_service_ticket_expenses_finance_expense
    ON service_ticket_expenses(finance_expense_id)
    WHERE finance_expense_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_service_ticket_expenses_finance_expense'
    ) THEN
        ALTER TABLE service_ticket_expenses
            ADD CONSTRAINT fk_service_ticket_expenses_finance_expense
            FOREIGN KEY (finance_expense_id)
            REFERENCES expenses(id)
            ON DELETE SET NULL;
    END IF;
END $$;
