-- Pusula Sample Data for VPS Testing (PostgreSQL)
-- Use this for staging/testing, then run cleanup_sample_data.sql before going live

-- 1. Create a Sample Company (Tenant)
INSERT INTO companies (id, name, subscription_status, created_at)
VALUES (
    '11111111-1111-1111-1111-111111111111', 
    'Acme HVAC Services', 
    'ACTIVE',
    CURRENT_TIMESTAMP
);

-- 2. Create Sample Users
-- Password is 'password' for all users (hashed with BCrypt)
INSERT INTO users (id, company_id, username, password_hash, role, full_name, created_at)
VALUES 
(
    '22222222-2222-2222-2222-222222222222', 
    '11111111-1111-1111-1111-111111111111', 
    'admin', 
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 
    'COMPANY_ADMIN', 
    'Alice Admin',
    CURRENT_TIMESTAMP
),
(
    '22222222-2222-2222-2222-333333333333', 
    '11111111-1111-1111-1111-111111111111', 
    'tech1', 
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 
    'TECHNICIAN', 
    'Bob Technician',
    CURRENT_TIMESTAMP
);

-- 3. Create Sample Customers
INSERT INTO customers (id, company_id, name, phone, address, created_at)
VALUES 
(
    '33333333-3333-3333-3333-333333333333', 
    '11111111-1111-1111-1111-111111111111', 
    'John Doe', 
    '555-0101', 
    '123 Maple St, Springfield',
    CURRENT_TIMESTAMP
),
(
    '33333333-3333-3333-3333-444444444444', 
    '11111111-1111-1111-1111-111111111111', 
    'Jane Smith', 
    '555-0102', 
    '456 Oak Ave, Springfield',
    CURRENT_TIMESTAMP
);

-- 4. Create Sample Inventory
INSERT INTO inventory (id, company_id, part_name, quantity, buy_price, sell_price, critical_level)
VALUES 
(
    '44444444-4444-4444-4444-444444444444', 
    '11111111-1111-1111-1111-111111111111', 
    'Compressor Model X', 
    5, 
    200.00, 
    450.00, 
    2
),
(
    '44444444-4444-4444-4444-555555555555', 
    '11111111-1111-1111-1111-111111111111', 
    'Thermostat V2', 
    20, 
    30.00, 
    85.00, 
    5
);

-- 5. Create Sample Service Tickets
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, description, scheduled_date, created_at)
VALUES 
(
    '55555555-5555-5555-5555-555555555555', 
    '11111111-1111-1111-1111-111111111111', 
    '33333333-3333-3333-3333-333333333333', 
    '22222222-2222-2222-2222-333333333333', 
    'ASSIGNED', 
    'AC not cooling', 
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    CURRENT_TIMESTAMP
),
(
    '55555555-5555-5555-5555-666666666666', 
    '11111111-1111-1111-1111-111111111111', 
    '33333333-3333-3333-3333-444444444444', 
    NULL, 
    'PENDING', 
    'Heater maintenance check', 
    CURRENT_TIMESTAMP + INTERVAL '3 days',
    CURRENT_TIMESTAMP
);

-- 6. Create Sample Fixed Expenses
INSERT INTO fixed_expenses (id, company_id, name, amount, due_day, created_at)
VALUES 
(
    '66666666-6666-6666-6666-666666666666', 
    '11111111-1111-1111-1111-111111111111', 
    'Office Rent', 
    1500.00, 
    1,
    CURRENT_TIMESTAMP
),
(
    '66666666-6666-6666-6666-777777777777', 
    '11111111-1111-1111-1111-111111111111', 
    'Vehicle Insurance', 
    350.00, 
    15,
    CURRENT_TIMESTAMP
);

-- 7. Create Sample One-time Expenses
INSERT INTO expenses (id, company_id, category, description, amount, date, created_at)
VALUES 
(
    '77777777-7777-7777-7777-777777777777', 
    '11111111-1111-1111-1111-111111111111', 
    'Tools', 
    'New wrench set', 
    125.50, 
    CURRENT_DATE - INTERVAL '2 days',
    CURRENT_TIMESTAMP
),
(
    '77777777-7777-7777-7777-888888888888', 
    '11111111-1111-1111-1111-111111111111', 
    'Fuel', 
    'Gas for service van', 
    75.00, 
    CURRENT_DATE - INTERVAL '1 day',
    CURRENT_TIMESTAMP
);

-- Verification Query
SELECT 'Sample data loaded successfully!' as status;
SELECT COUNT(*) as company_count FROM companies;
SELECT COUNT(*) as user_count FROM users;
SELECT COUNT(*) as customer_count FROM customers;
SELECT COUNT(*) as inventory_count FROM inventory;
SELECT COUNT(*) as ticket_count FROM service_tickets;
SELECT COUNT(*) as expense_count FROM expenses;
SELECT COUNT(*) as fixed_expense_count FROM fixed_expenses;
