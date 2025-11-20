-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Companies Table (Tenants)
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    subscription_status VARCHAR(50) NOT NULL, -- e.g., ACTIVE, SUSPENDED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL, -- SUPER_ADMIN, COMPANY_ADMIN, TECHNICIAN
    full_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT uq_users_username_company UNIQUE (username, company_id) -- Username unique per company or globally? Usually globally or per company. Assuming per company for now, but email is better globally. Prompt says username.
);

-- Device Types Table
CREATE TABLE device_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_devicetypes_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Customers Table
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    coordinates VARCHAR(100), -- Format: "lat,long"
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Inventory Table
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL,
    part_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    buy_price DECIMAL(10, 2),
    sell_price DECIMAL(10, 2),
    critical_level INTEGER DEFAULT 5,
    CONSTRAINT fk_inventory_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Service Tickets Table
CREATE TABLE service_tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    assigned_technician_id UUID,
    status VARCHAR(50) NOT NULL, -- PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
    scheduled_date TIMESTAMP WITH TIME ZONE,
    description TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_technician FOREIGN KEY (assigned_technician_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Service Photos Table
CREATE TABLE service_photos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id UUID NOT NULL,
    url TEXT NOT NULL,
    type VARCHAR(50) NOT NULL, -- BEFORE, AFTER
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photos_ticket FOREIGN KEY (ticket_id) REFERENCES service_tickets(id) ON DELETE CASCADE
);

-- Indexes for performance on company_id (Multi-tenancy)
CREATE INDEX idx_users_company ON users(company_id);
CREATE INDEX idx_device_types_company ON device_types(company_id);
CREATE INDEX idx_customers_company ON customers(company_id);
CREATE INDEX idx_inventory_company ON inventory(company_id);
CREATE INDEX idx_service_tickets_company ON service_tickets(company_id);
