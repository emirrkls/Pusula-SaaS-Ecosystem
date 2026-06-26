-- Pusula Servis - Google Play reviewer/demo tenant seed
--
-- Safe scope:
--   This script deletes and recreates data ONLY for org_code = 'PUS-DEMO'.
--   It does not touch real tenant data.
--
-- Reviewer login:
--   Org code : PUS-DEMO
--   Username : reviewer.admin
--   Password : PusulaDemo2026!
--
-- Technician logins use the same password:
--   tech.ayse, tech.mehmet, tech.deniz
--
-- Notes:
--   - Requires PostgreSQL pgcrypto extension for BCrypt-compatible password hashes.
--   - Service photos and signatures are intentionally not seeded here.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_company_id BIGINT;
    v_admin_id BIGINT;
    v_ayse_id BIGINT;
    v_mehmet_id BIGINT;
    v_deniz_id BIGINT;
    v_customer_altunkum BIGINT;
    v_customer_mavisehir BIGINT;
    v_customer_akbuk BIGINT;
    v_customer_didim BIGINT;
    v_customer_ege BIGINT;
    v_customer_altinkum BIGINT;
    v_customer_liman BIGINT;
    v_customer_marina BIGINT;
    v_inv_filter BIGINT;
    v_inv_gas BIGINT;
    v_inv_sensor BIGINT;
    v_inv_motor BIGINT;
    v_inv_remote BIGINT;
    v_inv_pump BIGINT;
    v_vehicle_1 BIGINT;
    v_vehicle_2 BIGINT;
    v_ticket_1 BIGINT;
    v_ticket_2 BIGINT;
    v_ticket_3 BIGINT;
    v_ticket_4 BIGINT;
    v_ticket_5 BIGINT;
    v_ticket_6 BIGINT;
    v_proposal_1 BIGINT;
    v_proposal_2 BIGINT;
    v_device_type_split BIGINT;
BEGIN
    SELECT id INTO v_company_id
    FROM companies
    WHERE org_code = 'PUS-DEMO'
    LIMIT 1;

    IF v_company_id IS NOT NULL THEN
        DELETE FROM proposal_items
        WHERE proposal_id IN (SELECT id FROM proposals WHERE company_id = v_company_id);

        DELETE FROM proposals WHERE company_id = v_company_id;

        DELETE FROM service_used_parts
        WHERE company_id = v_company_id
           OR ticket_id IN (SELECT id FROM service_tickets WHERE company_id = v_company_id);

        DELETE FROM service_ticket_expenses WHERE company_id = v_company_id;
        DELETE FROM service_photos
        WHERE ticket_id IN (SELECT id FROM service_tickets WHERE company_id = v_company_id);

        DELETE FROM service_tickets WHERE company_id = v_company_id;
        DELETE FROM current_accounts WHERE company_id = v_company_id;
        DELETE FROM vehicle_stocks WHERE company_id = v_company_id;
        DELETE FROM inventory WHERE company_id = v_company_id;
        DELETE FROM expenses WHERE company_id = v_company_id;
        DELETE FROM commercial_devices WHERE company_id = v_company_id;
        DELETE FROM device_types WHERE company_id = v_company_id;
        DELETE FROM vehicles WHERE company_id = v_company_id;
        DELETE FROM customers WHERE company_id = v_company_id;
        DELETE FROM users WHERE company_id = v_company_id;
        DELETE FROM usage_tracking WHERE company_id = v_company_id;
    ELSE
        INSERT INTO companies (
            name, subscription_status, phone, address, email,
            plan_type, org_code, trial_ends_at, billing_email,
            is_read_only, subscription_expires_at, created_at, updated_at, is_deleted
        )
        VALUES (
            'Pusula Demo Servis', 'ACTIVE', '+90 555 010 20 30',
            'Didim, Aydin', 'demo@pusulaservis.com',
            'PATRON', 'PUS-DEMO', NOW() + INTERVAL '90 days', 'billing-demo@pusulaservis.com',
            false, NOW() + INTERVAL '90 days', NOW(), NOW(), false
        )
        RETURNING id INTO v_company_id;
    END IF;

    UPDATE companies
    SET name = 'Pusula Demo Servis',
        subscription_status = 'ACTIVE',
        phone = '+90 555 010 20 30',
        address = 'Didim, Aydin',
        email = 'demo@pusulaservis.com',
        plan_type = 'PATRON',
        org_code = 'PUS-DEMO',
        trial_ends_at = NOW() + INTERVAL '90 days',
        billing_email = 'billing-demo@pusulaservis.com',
        is_read_only = false,
        subscription_expires_at = NOW() + INTERVAL '90 days',
        updated_at = NOW(),
        is_deleted = false
    WHERE id = v_company_id;

    INSERT INTO users (company_id, username, password_hash, role, full_name, signature_path, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, 'reviewer.admin', crypt('PusulaDemo2026!', gen_salt('bf', 10)), 'COMPANY_ADMIN', 'Google Play Reviewer', NULL, NOW(), NOW(), false),
        (v_company_id, 'tech.ayse', crypt('PusulaDemo2026!', gen_salt('bf', 10)), 'TECHNICIAN', 'Ayse Teknik', NULL, NOW(), NOW(), false),
        (v_company_id, 'tech.mehmet', crypt('PusulaDemo2026!', gen_salt('bf', 10)), 'TECHNICIAN', 'Mehmet Usta', NULL, NOW(), NOW(), false),
        (v_company_id, 'tech.deniz', crypt('PusulaDemo2026!', gen_salt('bf', 10)), 'TECHNICIAN', 'Deniz Servis', NULL, NOW(), NOW(), false);

    SELECT id INTO v_admin_id FROM users WHERE company_id = v_company_id AND username = 'reviewer.admin';
    SELECT id INTO v_ayse_id FROM users WHERE company_id = v_company_id AND username = 'tech.ayse';
    SELECT id INTO v_mehmet_id FROM users WHERE company_id = v_company_id AND username = 'tech.mehmet';
    SELECT id INTO v_deniz_id FROM users WHERE company_id = v_company_id AND username = 'tech.deniz';

    INSERT INTO customers (company_id, name, phone, address, coordinates, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, 'Altunkum Otel', '+90 555 100 10 01', 'Altinkum Mah. Sahil Cad. No:12 Didim', '37.3586,27.2787', NOW() - INTERVAL '20 days', NOW(), false),
        (v_company_id, 'Mavisehir Villa', '+90 555 100 10 02', 'Mavisehir Mah. 3020 Sok. No:7 Didim', '37.3914,27.2562', NOW() - INTERVAL '18 days', NOW(), false),
        (v_company_id, 'Akbuk Market', '+90 555 100 10 03', 'Akbuk Mah. Liman Cad. No:44 Didim', '37.3938,27.4310', NOW() - INTERVAL '16 days', NOW(), false),
        (v_company_id, 'Didim Plaza', '+90 555 100 10 04', 'Yeni Mah. Ataturk Bulv. No:85 Didim', '37.3751,27.2679', NOW() - INTERVAL '14 days', NOW(), false),
        (v_company_id, 'Ege Market', '+90 555 100 10 05', 'Cumhuriyet Mah. Inonu Cad. No:9 Didim', '37.3822,27.2694', NOW() - INTERVAL '12 days', NOW(), false),
        (v_company_id, 'Altinkum Evleri', '+90 555 100 10 06', 'Altinkum Mah. 5. Sok. No:18 Didim', '37.3567,27.2850', NOW() - INTERVAL '10 days', NOW(), false),
        (v_company_id, 'Liman Kafe', '+90 555 100 10 07', 'Didim Marina Yolu No:21 Didim', '37.3430,27.2555', NOW() - INTERVAL '8 days', NOW(), false),
        (v_company_id, 'Marina Ofisleri', '+90 555 100 10 08', 'Camlik Mah. Marina Karsisi No:4 Didim', '37.3458,27.2588', NOW() - INTERVAL '6 days', NOW(), false);

    SELECT id INTO v_customer_altunkum FROM customers WHERE company_id = v_company_id AND name = 'Altunkum Otel';
    SELECT id INTO v_customer_mavisehir FROM customers WHERE company_id = v_company_id AND name = 'Mavisehir Villa';
    SELECT id INTO v_customer_akbuk FROM customers WHERE company_id = v_company_id AND name = 'Akbuk Market';
    SELECT id INTO v_customer_didim FROM customers WHERE company_id = v_company_id AND name = 'Didim Plaza';
    SELECT id INTO v_customer_ege FROM customers WHERE company_id = v_company_id AND name = 'Ege Market';
    SELECT id INTO v_customer_altinkum FROM customers WHERE company_id = v_company_id AND name = 'Altinkum Evleri';
    SELECT id INTO v_customer_liman FROM customers WHERE company_id = v_company_id AND name = 'Liman Kafe';
    SELECT id INTO v_customer_marina FROM customers WHERE company_id = v_company_id AND name = 'Marina Ofisleri';

    INSERT INTO inventory (company_id, part_name, quantity, buy_price, sell_price, critical_level, brand, category, barcode, location, vehicle_id, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, 'Split klima filtresi', 48, 110.00, 220.00, 10, 'Pusula', 'Filtre', '8690001000011', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'R32 klima gazi 5kg', 12, 950.00, 1650.00, 3, 'CoolGas', 'Gaz', '8690001000028', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Sicaklik sensoru', 24, 180.00, 420.00, 6, 'ThermoPro', 'Elektronik', '8690001000035', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Fan motoru 12K BTU', 8, 1450.00, 2650.00, 2, 'AirMotion', 'Motor', '8690001000042', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Universal kumanda', 30, 95.00, 250.00, 8, 'UniControl', 'Aksesuar', '8690001000059', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Drenaj pompasi', 9, 720.00, 1250.00, 3, 'DrainTech', 'Pompa', '8690001000066', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Bakir boru seti', 18, 380.00, 750.00, 5, 'CopperLine', 'Montaj', '8690001000073', 'DEPO', NULL, NOW(), NOW(), false),
        (v_company_id, 'Montaj ayak takimi', 35, 160.00, 360.00, 8, 'MountFix', 'Montaj', '8690001000080', 'DEPO', NULL, NOW(), NOW(), false);

    SELECT id INTO v_inv_filter FROM inventory WHERE company_id = v_company_id AND part_name = 'Split klima filtresi';
    SELECT id INTO v_inv_gas FROM inventory WHERE company_id = v_company_id AND part_name = 'R32 klima gazi 5kg';
    SELECT id INTO v_inv_sensor FROM inventory WHERE company_id = v_company_id AND part_name = 'Sicaklik sensoru';
    SELECT id INTO v_inv_motor FROM inventory WHERE company_id = v_company_id AND part_name = 'Fan motoru 12K BTU';
    SELECT id INTO v_inv_remote FROM inventory WHERE company_id = v_company_id AND part_name = 'Universal kumanda';
    SELECT id INTO v_inv_pump FROM inventory WHERE company_id = v_company_id AND part_name = 'Drenaj pompasi';

    INSERT INTO vehicles (company_id, license_plate, driver_name, is_active)
    VALUES
        (v_company_id, '09 DEMO 01', 'Ayse Teknik', true),
        (v_company_id, '09 DEMO 02', 'Mehmet Usta', true);

    SELECT id INTO v_vehicle_1 FROM vehicles WHERE company_id = v_company_id AND license_plate = '09 DEMO 01';
    SELECT id INTO v_vehicle_2 FROM vehicles WHERE company_id = v_company_id AND license_plate = '09 DEMO 02';

    INSERT INTO vehicle_stocks (company_id, vehicle_id, inventory_id, quantity)
    VALUES
        (v_company_id, v_vehicle_1, v_inv_filter, 6),
        (v_company_id, v_vehicle_1, v_inv_sensor, 4),
        (v_company_id, v_vehicle_1, v_inv_remote, 3),
        (v_company_id, v_vehicle_2, v_inv_gas, 2),
        (v_company_id, v_vehicle_2, v_inv_motor, 1),
        (v_company_id, v_vehicle_2, v_inv_pump, 2);

    INSERT INTO service_tickets (
        company_id, customer_id, assigned_technician_id, status, scheduled_date,
        description, notes, collected_amount, parent_ticket_id, is_warranty_call,
        payment_method, created_at, updated_at, is_deleted
    )
    VALUES
        (v_company_id, v_customer_altunkum, v_ayse_id, 'IN_PROGRESS', NOW() + INTERVAL '2 hours',
         'Salon tipi klima bakimi ve filtre temizligi', 'Teknisyen sahada, filtre degisimi yapilacak.', 0, NULL, false, 'CASH', NOW() - INTERVAL '2 days', NOW(), false),
        (v_company_id, v_customer_mavisehir, v_mehmet_id, 'ASSIGNED', NOW() + INTERVAL '1 day',
         'Split klima sogutmuyor ariza tespiti', 'Gaz kacak kontrolu ve sensor testi planlandi.', 0, NULL, false, 'CURRENT_ACCOUNT', NOW() - INTERVAL '1 day', NOW(), false),
        (v_company_id, v_customer_akbuk, v_deniz_id, 'COMPLETED', NOW() - INTERVAL '1 day',
         'Market klima drenaj pompasi degisimi', 'Servis tamamlandi, PDF rapor hazir.', 2850.00, NULL, false, 'CREDIT_CARD', NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day', false),
        (v_company_id, v_customer_didim, v_ayse_id, 'COMPLETED', NOW() - INTERVAL '2 days',
         'Ofis klima periyodik bakim', 'Iki ic unite temizlendi, performans testi yapildi.', 1850.00, NULL, false, 'CASH', NOW() - INTERVAL '7 days', NOW() - INTERVAL '2 days', false),
        (v_company_id, v_customer_ege, v_mehmet_id, 'PENDING', NOW() + INTERVAL '2 days',
         'Kasap reyonu klima su damlatiyor', 'Randevu bekliyor, yogunluk nedeniyle ertelendi.', 0, NULL, false, 'CURRENT_ACCOUNT', NOW() - INTERVAL '3 hours', NOW(), false),
        (v_company_id, v_customer_altinkum, v_deniz_id, 'COMPLETED', NOW() - INTERVAL '4 days',
         'Yazlik klima montaj sonrasi kontrol', 'Montaj ayaklari ve bakir boru kontrol edildi.', 3200.00, NULL, false, 'CASH', NOW() - INTERVAL '8 days', NOW() - INTERVAL '4 days', false);

    SELECT id INTO v_ticket_1 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_altunkum AND description LIKE 'Salon tipi%' LIMIT 1;
    SELECT id INTO v_ticket_2 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_mavisehir LIMIT 1;
    SELECT id INTO v_ticket_3 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_akbuk LIMIT 1;
    SELECT id INTO v_ticket_4 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_didim LIMIT 1;
    SELECT id INTO v_ticket_5 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_ege LIMIT 1;
    SELECT id INTO v_ticket_6 FROM service_tickets WHERE company_id = v_company_id AND customer_id = v_customer_altinkum LIMIT 1;

    INSERT INTO service_used_parts (company_id, ticket_id, inventory_id, quantity_used, selling_price_snapshot, source_vehicle_id, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, v_ticket_3, v_inv_pump, 1, 1250.00, v_vehicle_2, NOW() - INTERVAL '1 day', NOW(), false),
        (v_company_id, v_ticket_3, v_inv_filter, 2, 220.00, NULL, NOW() - INTERVAL '1 day', NOW(), false),
        (v_company_id, v_ticket_4, v_inv_filter, 2, 220.00, v_vehicle_1, NOW() - INTERVAL '2 days', NOW(), false),
        (v_company_id, v_ticket_6, v_inv_gas, 1, 1650.00, v_vehicle_2, NOW() - INTERVAL '4 days', NOW(), false),
        (v_company_id, v_ticket_6, v_inv_remote, 1, 250.00, NULL, NOW() - INTERVAL '4 days', NOW(), false);

    INSERT INTO service_ticket_expenses (service_ticket_id, company_id, description, amount, supplier, notes, created_at)
    VALUES
        (v_ticket_3, v_company_id, 'Harici drenaj hortumu', 320.00, 'Didim Teknik Tedarik', 'Acil tedarik edildi.', NOW() - INTERVAL '1 day'),
        (v_ticket_6, v_company_id, 'Ek montaj sarf malzemesi', 210.00, 'Sahil Hirdavat', 'Montaj kontrolu icin kullanildi.', NOW() - INTERVAL '4 days');

    INSERT INTO current_accounts (company_id, customer_id, balance, last_updated)
    VALUES
        (v_company_id, v_customer_mavisehir, 1250.00, NOW()),
        (v_company_id, v_customer_ege, 3420.00, NOW()),
        (v_company_id, v_customer_liman, 780.00, NOW()),
        (v_company_id, v_customer_marina, 0.00, NOW());

    INSERT INTO expenses (company_id, amount, description, date, category, fixed_expense_id)
    VALUES
        (v_company_id, 1450.00, 'Servis araci yakit', CURRENT_DATE - 1, 'FUEL', NULL),
        (v_company_id, 3200.00, 'Teknisyen saha primi', CURRENT_DATE - 2, 'SALARY', NULL),
        (v_company_id, 890.00, 'Depo elektrik faturasi', CURRENT_DATE - 3, 'BILLS', NULL),
        (v_company_id, 1760.00, 'Montaj sarf malzemeleri', CURRENT_DATE - 4, 'MATERIAL', NULL);

    INSERT INTO device_types (company_id, name, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, 'Split Klima', NOW(), NOW(), false),
        (v_company_id, 'Salon Tipi Klima', NOW(), NOW(), false),
        (v_company_id, 'VRF Ic Unite', NOW(), NOW(), false);

    SELECT id INTO v_device_type_split FROM device_types WHERE company_id = v_company_id AND name = 'Split Klima' LIMIT 1;

    INSERT INTO commercial_devices (company_id, brand, model, btu, gas_type, device_type_id, quantity, buying_price, selling_price, created_at, is_deleted, updated_at)
    VALUES
        (v_company_id, 'Hisense', 'Eco Smart 12K', 12000, 'R32', v_device_type_split, 4, 14500.00, 21900.00, NOW(), false, NOW()),
        (v_company_id, 'Daikin', 'Sensira 18K', 18000, 'R32', v_device_type_split, 2, 25500.00, 36900.00, NOW(), false, NOW());

    INSERT INTO proposals (company_id, customer_id, prepared_by_id, total_price, status, valid_until, note, tax_rate, discount, title, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, v_customer_liman, v_admin_id, 18450.00, 'SENT', CURRENT_DATE + 15,
         'Liman Kafe icin sezon oncesi klima yenileme teklifi.', 20.00, 500.00, 'Sezon Oncesi Klima Yenileme', NOW() - INTERVAL '2 days', NOW(), false),
        (v_company_id, v_customer_marina, v_admin_id, 42750.00, 'DRAFT', CURRENT_DATE + 20,
         'Marina ofisleri icin coklu bakim ve montaj teklifi.', 20.00, 0.00, 'Ofis Klima Bakim ve Montaj', NOW() - INTERVAL '1 day', NOW(), false);

    SELECT id INTO v_proposal_1 FROM proposals WHERE company_id = v_company_id AND title = 'Sezon Oncesi Klima Yenileme' LIMIT 1;
    SELECT id INTO v_proposal_2 FROM proposals WHERE company_id = v_company_id AND title = 'Ofis Klima Bakim ve Montaj' LIMIT 1;

    INSERT INTO proposal_items (company_id, proposal_id, description, quantity, unit_cost, unit_price, total_price, created_at, updated_at, is_deleted)
    VALUES
        (v_company_id, v_proposal_1, 'Hisense Eco Smart 12K klima', 1, 14500.00, 21900.00, 21900.00, NOW(), NOW(), false),
        (v_company_id, v_proposal_1, 'Montaj ve devreye alma indirimi', 1, 0.00, -3450.00, -3450.00, NOW(), NOW(), false),
        (v_company_id, v_proposal_2, 'VRF ic unite bakim paketi', 6, 850.00, 1650.00, 9900.00, NOW(), NOW(), false),
        (v_company_id, v_proposal_2, 'Split klima montaj hizmeti', 3, 3500.00, 10950.00, 32850.00, NOW(), NOW(), false);

    INSERT INTO usage_tracking (company_id, usage_type, current_count, period_start, period_end, created_at)
    VALUES
        (v_company_id, 'TECHNICIANS', 3, date_trunc('month', CURRENT_DATE)::date, (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date, NOW()),
        (v_company_id, 'CUSTOMERS', 8, date_trunc('month', CURRENT_DATE)::date, (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date, NOW()),
        (v_company_id, 'MONTHLY_TICKETS', 6, date_trunc('month', CURRENT_DATE)::date, (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date, NOW()),
        (v_company_id, 'MONTHLY_PROPOSALS', 2, date_trunc('month', CURRENT_DATE)::date, (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date, NOW());

    RAISE NOTICE 'PUS-DEMO seeded. company_id=%, reviewer username=reviewer.admin, password=PusulaDemo2026!', v_company_id;
END $$;

SELECT
    c.id AS company_id,
    c.org_code,
    c.name AS company_name,
    c.plan_type,
    c.subscription_status,
    (SELECT COUNT(*) FROM users u WHERE u.company_id = c.id AND COALESCE(u.is_deleted, false) = false) AS users,
    (SELECT COUNT(*) FROM customers cu WHERE cu.company_id = c.id AND COALESCE(cu.is_deleted, false) = false) AS customers,
    (SELECT COUNT(*) FROM service_tickets st WHERE st.company_id = c.id AND COALESCE(st.is_deleted, false) = false) AS tickets,
    (SELECT COUNT(*) FROM inventory i WHERE i.company_id = c.id AND COALESCE(i.is_deleted, false) = false) AS inventory_items,
    (SELECT COUNT(*) FROM proposals p WHERE p.company_id = c.id AND COALESCE(p.is_deleted, false) = false) AS proposals
FROM companies c
WHERE c.org_code = 'PUS-DEMO';
