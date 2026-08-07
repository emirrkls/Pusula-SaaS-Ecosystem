CREATE TABLE IF NOT EXISTS business_assets (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    asset_condition VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (asset_condition IN ('ACTIVE', 'MAINTENANCE', 'BROKEN', 'RETIRED')),
    serial_number VARCHAR(150),
    location VARCHAR(200),
    assigned_to VARCHAR(200),
    purchase_date DATE,
    purchase_price NUMERIC(12, 2) CHECK (purchase_price >= 0),
    notes VARCHAR(1000),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_business_assets_company_active
    ON business_assets(company_id, is_deleted, asset_name);
