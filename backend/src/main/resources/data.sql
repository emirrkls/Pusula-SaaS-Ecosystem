-- Seed Data for Pusula Service Ecosystem

-- 1. Create a Company (Tenant)
INSERT INTO companies (id, name, subscription_status)
VALUES ('11111111-1111-1111-1111-111111111111', 'Acme HVAC Services', 'ACTIVE');

-- 2. Create Users
-- Password is 'password' (hashed with BCrypt)
INSERT INTO users (id, company_id, username, password_hash, role, full_name)
VALUES 
('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'COMPANY_ADMIN', 'Alice Admin'),
('22222222-2222-2222-2222-333333333333', '11111111-1111-1111-1111-111111111111', 'tech1', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'TECHNICIAN', 'Bob Technician');

-- 3. Create Customers
INSERT INTO customers (id, company_id, name, phone, address)
VALUES 
('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'John Doe', '555-0101', '123 Maple St, Springfield'),
('33333333-3333-3333-3333-444444444444', '11111111-1111-1111-1111-111111111111', 'Jane Smith', '555-0102', '456 Oak Ave, Springfield');

-- 4. Create Inventory
INSERT INTO inventory (id, company_id, part_name, quantity, buy_price, sell_price, critical_level)
VALUES 
('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Compressor Model X', 5, 200.00, 450.00, 2),
('44444444-4444-4444-4444-555555555555', '11111111-1111-1111-1111-111111111111', 'Thermostat V2', 20, 30.00, 85.00, 5);

-- 5. Create Service Tickets
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, description, scheduled_date)
VALUES 
('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-333333333333', 'ASSIGNED', 'AC not cooling', DATEADD('DAY', 1, CURRENT_TIMESTAMP));
