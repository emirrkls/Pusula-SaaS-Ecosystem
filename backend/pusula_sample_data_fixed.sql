-- Pusula Sample Data for VPS Testing (PostgreSQL with BIGSERIAL IDs)
-- Compatible with auto-generated schema

-- 1. Create Sample Company (using auto-generated ID)
INSERT INTO companies (name, subscription_status, phone, address, email)
VALUES ('Acme HVAC Services', 'ACTIVE', '555-0100', '123 Business Park', 'info@acmehvac.com');

-- Get the company ID for reference (it will be 1 if this is first insert)
-- We'll use 1 for all foreign keys below

-- 2. Create Sample Users (password: 'password' for all)
INSERT INTO users (company_id, username, password_hash, role, full_name)
VALUES 
(1, 'testadmin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'COMPANY_ADMIN', 'Test Admin'),
(1, 'testtech', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'TECHNICIAN', 'Test Technician');

-- 3. Create Sample Customers
INSERT INTO customers (company_id, name, phone, address)
VALUES 
(1, 'John Doe', '555-0101', '123 Maple St, Springfield'),
(1, 'Jane Smith', '555-0102', '456 Oak Ave, Springfield'),
(1, 'Bob Johnson', '555-0103', '789 Pine Rd, Springfield');

-- 4. Create Sample Inventory
INSERT INTO inventory (company_id, part_name, quantity, buy_price, sell_price, critical_level)
VALUES 
(1, 'Compressor Model X', 5, 200.00, 450.00, 2),
(1, 'Thermostat V2', 20, 30.00, 85.00, 5),
(1, 'Air Filter Premium', 15, 5.00, 25.00, 10);

-- 5. Create Sample Service Tickets
-- Get user IDs (assuming testadmin=2, testtech=3)
INSERT INTO service_tickets (company_id, customer_id, assigned_technician_id, status, description, collected_amount, scheduled_date)
VALUES 
(1, 1, 3, 'ASSIGNED', 'AC not cooling properly', NULL, CURRENT_TIMESTAMP + INTERVAL '1 day'),
(1, 2, NULL, 'PENDING', 'Heater maintenance check', NULL, CURRENT_TIMESTAMP + INTERVAL '3 days'),
(1, 3, 3, 'COMPLETED', 'Filter replacement', 85.00, CURRENT_TIMESTAMP - INTERVAL '2 days');

-- 6. Create Sample Expenses (if table exists)
INSERT INTO expenses (company_id, category, description, amount, date)
VALUES 
(1, 'Tools', 'New wrench set', 125.50, CURRENT_DATE - INTERVAL '2 days'),
(1, 'Fuel', 'Gas for service van', 75.00, CURRENT_DATE - INTERVAL '1 day'),
(1, 'Marketing', 'Flyers printing', 50.00, CURRENT_DATE);

-- 7. Create Sample Fixed Expenses (if table exists with correct schema)
-- Check if this table exists first
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'fixed_expenses') THEN
        INSERT INTO fixed_expenses (company_id, name, amount, due_day)
        VALUES 
        (1, 'Office Rent', 1500.00, 1),
        (1, 'Vehicle Insurance', 350.00, 15);
    END IF;
END $$;

-- Verification
SELECT 'Sample data loaded!' as status;
SELECT COUNT(*) as companies FROM companies WHERE name = 'Acme HVAC Services';
SELECT COUNT(*) as users FROM users WHERE company_id = 1;
SELECT COUNT(*) as customers FROM customers WHERE company_id = 1;
SELECT COUNT(*) as inventory FROM inventory WHERE company_id = 1;
SELECT COUNT(*) as tickets FROM service_tickets WHERE company_id = 1;
SELECT COUNT(*) as expenses FROM expenses WHERE company_id = 1;
