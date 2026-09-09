CREATE TABLE account_parties (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    party_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    tax_number VARCHAR(32),
    tax_office VARCHAR(120),
    phone VARCHAR(64),
    email VARCHAR(255),
    address VARCHAR(500),
    contact_person VARCHAR(255),
    payment_term_days INTEGER NOT NULL DEFAULT 0,
    customer_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_party_type CHECK (party_type IN ('CUSTOMER','ORGANIZATION','SUPPLIER','OTHER')),
    CONSTRAINT ck_account_party_payment_term CHECK (payment_term_days >= 0),
    CONSTRAINT fk_account_party_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT uq_account_party_customer UNIQUE (company_id, customer_id),
    CONSTRAINT uq_account_party_company_type_name UNIQUE (company_id, party_type, normalized_name)
);

CREATE INDEX idx_account_parties_company_type_active
    ON account_parties(company_id, party_type, active, display_name);

INSERT INTO account_parties
    (company_id, party_type, display_name, normalized_name, phone, address, customer_id, active, created_at, updated_at, version)
SELECT c.company_id, 'CUSTOMER', c.name,
       'customer-' || c.id,
       c.phone, c.address, c.id, NOT c.is_deleted,
       COALESCE(c.created_at, CURRENT_TIMESTAMP), c.updated_at, 0
FROM customers c;

INSERT INTO account_parties
    (company_id, party_type, display_name, normalized_name, phone, active, created_at, version)
SELECT d.company_id, 'SUPPLIER', MIN(TRIM(d.creditor_name)),
       LOWER(TRANSLATE(REGEXP_REPLACE(TRIM(d.creditor_name), '\s+', ' ', 'g'), 'Iİ', 'ıi')),
       MIN(NULLIF(TRIM(d.creditor_phone), '')), TRUE, CURRENT_TIMESTAMP, 0
FROM company_debts d
WHERE d.deleted = FALSE
GROUP BY d.company_id, LOWER(TRANSLATE(REGEXP_REPLACE(TRIM(d.creditor_name), '\s+', ' ', 'g'), 'Iİ', 'ıi'));

ALTER TABLE current_accounts ADD COLUMN party_id BIGINT;
UPDATE current_accounts a
SET party_id = p.id
FROM account_parties p
WHERE p.company_id = a.company_id AND p.customer_id = a.customer_id;
ALTER TABLE current_accounts ALTER COLUMN party_id SET NOT NULL;
ALTER TABLE current_accounts ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE current_accounts ADD CONSTRAINT fk_current_account_party FOREIGN KEY (party_id) REFERENCES account_parties(id);
ALTER TABLE current_accounts ADD CONSTRAINT uq_current_account_party UNIQUE (company_id, party_id);

ALTER TABLE current_account_transactions ADD COLUMN party_id BIGINT;
UPDATE current_account_transactions t
SET party_id = a.party_id
FROM current_accounts a
WHERE a.id = t.current_account_id;
ALTER TABLE current_account_transactions ALTER COLUMN party_id SET NOT NULL;
ALTER TABLE current_account_transactions ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE current_account_transactions ADD CONSTRAINT fk_current_account_transaction_party FOREIGN KEY (party_id) REFERENCES account_parties(id);
CREATE INDEX idx_current_account_transactions_party ON current_account_transactions(company_id, party_id, effective_date);

ALTER TABLE company_debts ADD COLUMN party_id BIGINT;
UPDATE company_debts d
SET party_id = p.id
FROM account_parties p
WHERE p.company_id = d.company_id
  AND p.party_type = 'SUPPLIER'
  AND p.normalized_name = LOWER(TRANSLATE(REGEXP_REPLACE(TRIM(d.creditor_name), '\s+', ' ', 'g'), 'Iİ', 'ıi'));
ALTER TABLE company_debts ADD CONSTRAINT fk_company_debt_party FOREIGN KEY (party_id) REFERENCES account_parties(id);
CREATE INDEX idx_company_debts_party ON company_debts(company_id, party_id, deleted);

ALTER TABLE service_tickets ADD COLUMN billing_party_id BIGINT;
ALTER TABLE service_tickets ADD COLUMN billing_responsibility VARCHAR(32);
ALTER TABLE service_tickets ADD CONSTRAINT fk_service_ticket_billing_party FOREIGN KEY (billing_party_id) REFERENCES account_parties(id);
ALTER TABLE service_tickets ADD CONSTRAINT ck_service_ticket_billing_responsibility
    CHECK (billing_responsibility IS NULL OR billing_responsibility IN ('CUSTOMER','ORGANIZATION','INTERNAL'));
CREATE INDEX idx_service_tickets_billing_party ON service_tickets(company_id, billing_party_id);
