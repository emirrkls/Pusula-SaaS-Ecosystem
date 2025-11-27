-- ============================================
-- 3-Month Financial Simulation Data
-- Period: September 1 - November 27, 2025
-- ============================================

-- Clean existing data
TRUNCATE TABLE daily_closings CASCADE;
TRUNCATE TABLE expenses CASCADE;
TRUNCATE TABLE fixed_expense_definitions CASCADE;
TRUNCATE TABLE service_tickets CASCADE;
TRUNCATE TABLE inventory CASCADE;
TRUNCATE TABLE device_types CASCADE;
TRUNCATE TABLE customers CASCADE;
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE companies CASCADE;

-- ============================================
-- 1. CORE DATA (Static)
-- ============================================

-- Company
INSERT INTO companies (id, name, subscription_status, phone, address, email, is_deleted, created_at, updated_at)
VALUES (1, 'Pusula Teknik', 'ACTIVE', '+90 212 555 0100', 'Maslak Mahallesi, Büyükdere Cad. No:255, Sarıyer/İstanbul', 'info@pusulateknik.com', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00');

-- Users (Admin + 2 Technicians)
INSERT INTO users (id, company_id, username, password_hash, role, full_name, is_deleted, created_at, updated_at)
VALUES 
(1, 1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'COMPANY_ADMIN', 'Admin User', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
(2, 1, 'ali.usta', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'TECHNICIAN', 'Ali Usta', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
(3, 1, 'veli.usta', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'TECHNICIAN', 'Veli Usta', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00');

-- Device Types
INSERT INTO device_types (id, company_id, name, is_deleted, created_at, updated_at)
VALUES 
(1, 1, 'Klima', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
(2, 1, 'Kombi', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
(3, 1, 'VRF', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
(4, 1, 'Beyaz Eşya', false, '2025-01-01 09:00:00', '2025-01-01 09:00:00');

-- Customers (15 Turkish names)
INSERT INTO customers (id, company_id, name, phone, address, coordinates, is_deleted, created_at, updated_at)
VALUES 
(1, 1, 'Ahmet Yılmaz', '+90 532 111 2233', 'Beşiktaş, İstanbul', '41.0422,29.0094', false, '2025-08-01 10:00:00', '2025-08-01 10:00:00'),
(2, 1, 'Ayşe Kaya', '+90 533 222 3344', 'Kadıköy, İstanbul', '40.9903,29.0306', false, '2025-08-02 11:00:00', '2025-08-02 11:00:00'),
(3, 1, 'Mehmet Demir', '+90 534 333 4455', 'Şişli, İstanbul', '41.0602,28.9887', false, '2025-08-03 12:00:00', '2025-08-03 12:00:00'),
(4, 1, 'Fatma Çelik', '+90 535 444 5566', 'Üsküdar, İstanbul', '41.0253,29.0138', false, '2025-08-04 13:00:00', '2025-08-04 13:00:00'),
(5, 1, 'Mustafa Arslan', '+90 536 555 6677', 'Bakırköy, İstanbul', '40.9807,28.8738', false, '2025-08-05 14:00:00', '2025-08-05 14:00:00'),
(6, 1, 'Zeynep Öztürk', '+90 537 666 7788', 'Ataşehir, İstanbul', '40.9829,29.1248', false, '2025-08-06 15:00:00', '2025-08-06 15:00:00'),
(7, 1, 'Can Şahin', '+90 538 777 8899', 'Beyoğlu, İstanbul', '41.0344,28.9778', false, '2025-08-07 16:00:00', '2025-08-07 16:00:00'),
(8, 1, 'Elif Aydın', '+90 539 888 9900', 'Maltepe, İstanbul', '40.9339,29.1425', false, '2025-08-08 17:00:00', '2025-08-08 17:00:00'),
(9, 1, 'Burak Koç', '+90 541 999 0011', 'Sarıyer, İstanbul', '41.1694,29.0522', false, '2025-08-09 18:00:00', '2025-08-09 18:00:00'),
(10, 1, 'Selin Yıldız', '+90 542 000 1122', 'Esenler, İstanbul', '41.0444,28.8831', false, '2025-08-10 09:00:00', '2025-08-10 09:00:00'),
(11, 1, 'Emre Avcı', '+90 543 111 2233', 'Kartal, İstanbul', '40.9081,29.1886', false, '2025-08-11 10:00:00', '2025-08-11 10:00:00'),
(12, 1, 'Deniz Kurt', '+90 544 222 3344', 'Pendik, İstanbul', '40.8783,29.2333', false, '2025-08-12 11:00:00', '2025-08-12 11:00:00'),
(13, 1, 'Cem Özdemir', '+90 545 333 4455', 'Beylikdüzü, İstanbul', '40.9903,28.6428', false, '2025-08-13 12:00:00', '2025-08-13 12:00:00'),
(14, 1, 'Merve Aksoy', '+90 546 444 5566', 'Çekmeköy, İstanbul', '41.0322,29.1267', false, '2025-08-14 13:00:00', '2025-08-14 13:00:00'),
(15, 1, 'Kerem Polat', '+90 547 555 6677', 'Esenyurt, İstanbul', '41.0314,28.6742', false, '2025-08-15 14:00:00', '2025-08-15 14:00:00');

-- Inventory (20 items, some below critical level)
INSERT INTO inventory (id, company_id, part_name, quantity, buy_price, sell_price, critical_level, is_deleted, created_at, updated_at)
VALUES 
(1, 1, 'R410A Gaz (1kg)', 45, 350.00, 450.00, 50, false, '2025-08-01 09:00:00', '2025-11-20 14:30:00'),
(2, 1, 'R32 Gaz (1kg)', 8, 380.00, 500.00, 20, false, '2025-08-01 09:00:00', '2025-11-22 11:15:00'),
(3, 1, 'Klima Filtresi (Universal)', 120, 25.00, 50.00, 30, false, '2025-08-01 09:00:00', '2025-11-21 16:00:00'),
(4, 1, 'Kompresör Yağı (1L)', 15, 120.00, 180.00, 10, false, '2025-08-01 09:00:00', '2025-11-23 10:00:00'),
(5, 1, 'Bakır Boru 1/4 (3m)', 35, 85.00, 150.00, 15, false, '2025-08-01 09:00:00', '2025-11-19 13:45:00'),
(6, 1, 'Bakır Boru 3/8 (3m)', 28, 95.00, 170.00, 15, false, '2025-08-01 09:00:00', '2025-11-20 09:20:00'),
(7, 1, 'Drenaj Hortumu (15m)', 22, 45.00, 80.00, 10, false, '2025-08-01 09:00:00', '2025-11-18 15:30:00'),
(8, 1, 'İzolasyon Malzemesi (5m)', 55, 30.00, 60.00, 25, false, '2025-08-01 09:00:00', '2025-11-21 12:00:00'),
(9, 1, 'Elektronik Kart (Universal)', 3, 850.00, 1200.00, 5, false, '2025-08-01 09:00:00', '2025-11-24 14:15:00'),
(10, 1, 'Fan Motoru', 7, 450.00, 650.00, 8, false, '2025-08-01 09:00:00', '2025-11-22 16:45:00'),
(11, 1, 'Termostat', 42, 180.00, 280.00, 20, false, '2025-08-01 09:00:00', '2025-11-20 11:30:00'),
(12, 1, 'Kapasitör (40uF)', 65, 35.00, 60.00, 30, false, '2025-08-01 09:00:00', '2025-11-23 13:00:00'),
(13, 1, 'Kontaktör (20A)', 18, 95.00, 150.00, 12, false, '2025-08-01 09:00:00', '2025-11-19 10:20:00'),
(14, 1, 'Basınç Sensörü', 12, 220.00, 350.00, 8, false, '2025-08-01 09:00:00', '2025-11-21 15:45:00'),
(15, 1, 'Genleşme Valfi', 9, 380.00, 550.00, 10, false, '2025-08-01 09:00:00', '2025-11-22 09:30:00'),
(16, 1, 'Kumanda (IR)', 25, 120.00, 200.00, 15, false, '2025-08-01 09:00:00', '2025-11-20 14:00:00'),
(17, 1, 'Alçak Basınç Anahtarı', 14, 165.00, 250.00, 10, false, '2025-08-01 09:00:00', '2025-11-23 11:45:00'),
(18, 1, 'Yüksek Basınç Anahtarı', 11, 175.00, 270.00, 10, false, '2025-08-01 09:00:00', '2025-11-24 10:30:00'),
(19, 1, 'Ekran Paneli', 4, 550.00, 800.00, 6, false, '2025-08-01 09:00:00', '2025-11-25 15:00:00'),
(20, 1, 'Sızdırmazlık Bandı (10m)', 88, 18.00, 35.00, 40, false, '2025-08-01 09:00:00', '2025-11-23 12:30:00');

-- Fixed Expense Definitions
INSERT INTO fixed_expense_definitions (id, company_id, name, default_amount, category, day_of_month, description)
VALUES 
(1, 1, 'Ofis Kirası', 25000.00, 'RENT', 1, 'Aylık ofis kira ödemesi'),
(2, 1, 'Maaşlar', 60000.00, 'SALARY', 15, 'Aylık personel maaşları (Admin + 2 Teknisyen)');

-- ============================================
-- 2. TRANSACTIONAL DATA (3 Months)
-- ============================================

-- September 2025 Expenses
INSERT INTO expenses (company_id, amount, description, date, category) VALUES
(1, 25000.00, 'Ofis Kirası - Eylül', '2025-09-01', 'RENT'),
(1, 450.00, 'Yol Yakıtı', '2025-09-01', 'FUEL'),
(1, 320.00, 'Öğle Yemeği', '2025-09-02', 'FOOD'),
(1, 1250.00, 'Klima Yedek Parça Alımı', '2025-09-03', 'MATERIAL'),
(1, 580.00, 'Yol Yakıtı', '2025-09-04', 'FUEL'),
(1, 280.00, 'Kahvaltı Malzemeleri', '2025-09-05', 'FOOD'),
(1, 1850.00, 'Bakır Boru ve İzolasyon', '2025-09-06', 'MATERIAL'),
(1, 420.00, 'Araç Yakıtı', '2025-09-08', 'FUEL'),
(1, 350.00, 'Personel Yemeği', '2025-09-09', 'FOOD'),
(1, 2100.00, 'Elektronik Kart Stok', '2025-09-10', 'MATERIAL'),
(1, 490.00, 'Yol Gideri', '2025-09-11', 'FUEL'),
(1, 60000.00, 'Maaşlar - Eylül', '2025-09-15', 'SALARY'),
(1, 860.00, 'Klima Gazı R410A', '2025-09-16', 'MATERIAL'),
(1, 380.00, 'Akaryakıt', '2025-09-17', 'FUEL'),
(1, 1450.00, 'Fan Motoru ve Kapasitör', '2025-09-18', 'MATERIAL'),
(1, 520.00, 'Yakıt', '2025-09-19', 'FUEL'),
(1, 310.00, 'Yemek Gideri', '2025-09-20', 'FOOD'),
(1, 750.00, 'Termostat ve Sensör', '2025-09-22', 'MATERIAL'),
(1, 440.00, 'Araç Yakıtı', '2025-09-23', 'FUEL'),
(1, 290.00, 'Çay Kahve Malzemesi', '2025-09-24', 'FOOD'),
(1, 1920.00, 'Kumanda ve Ekran', '2025-09-25', 'MATERIAL'),
(1, 560.00, 'Yol Yakıtı', '2025-09-26', 'FUEL'),
(1, 2350.00, 'Elektrik Faturası', '2025-09-27', 'BILLS'),
(1, 1180.00, 'Su Faturası', '2025-09-28', 'BILLS'),
(1, 670.00, 'İnternet + Telefon', '2025-09-29', 'BILLS'),
(1, 420.00, 'Yakıt', '2025-09-30', 'FUEL');

-- October 2025 Expenses  
INSERT INTO expenses (company_id, amount, description, date, category) VALUES
(1, 25000.00, 'Ofis Kirası - Ekim', '2025-10-01', 'RENT'),
(1, 510.00, 'Yol Yakıtı', '2025-10-01', 'FUEL'),
(1, 340.00, 'Personel Öğle Yemeği', '2025-10-02', 'FOOD'),
(1, 1680.00, 'R32 Gaz ve Filtre', '2025-10-03', 'MATERIAL'),
(1, 460.00, 'Akaryakıt', '2025-10-04', 'FUEL'),
(1, 395.00, 'Yemek Kartı', '2025-10-07', 'FOOD'),
(1, 2200.00, 'Kompresör Parçaları', '2025-10-08', 'MATERIAL'),
(1, 530.00, 'Araç Yakıtı', '2025-10-09', 'FUEL'),
(1, 320.00, 'Kahve Malzeme', '2025-10-10', 'FOOD'),
(1, 1540.00, 'Bakır Boru Stok', '2025-10-11', 'MATERIAL'),
(1, 60000.00, 'Maaşlar - Ekim', '2025-10-15', 'SALARY'),
(1, 480.00, 'Yol Gideri', '2025-10-16', 'FUEL'),
(1, 1890.00, 'İzolasyon ve Hortum', '2025-10-17', 'MATERIAL'),
(1, 550.00, 'Yakıt', '2025-10-18', 'FUEL'),
(1, 370.00, 'Personel Yemeği', '2025-10-21', 'FOOD'),
(1, 1220.00, 'Kapasitör ve Kontaktör', '2025-10-22', 'MATERIAL'),
(1, 490.00, 'Araç Yakıtı', '2025-10-23', 'FUEL'),
(1, 2150.00, 'Elektronik Kart', '2025-10-24', 'MATERIAL'),
(1, 520.00, 'Yol Yakıtı', '2025-10-25', 'FUEL'),
(1, 310.00, 'Yemek', '2025-10-28', 'FOOD'),
(1, 2420.00, 'Elektrik Faturası', '2025-10-29', 'BILLS'),
(1, 1150.00, 'Su Faturası', '2025-10-30', 'BILLS'),
(1, 690.00, 'İletişim Giderleri', '2025-10-31', 'BILLS');

-- November 2025 Expenses
INSERT INTO expenses (company_id, amount, description, date, category) VALUES
(1, 25000.00, 'Ofis Kirası - Kasım', '2025-11-01', 'RENT'),
(1, 540.00, 'Akaryakıt', '2025-11-01', 'FUEL'),
(1, 360.00, 'Öğle Yemeği', '2025-11-04', 'FOOD'),
(1, 1750.00, 'Klima Gazı R410A', '2025-11-05', 'MATERIAL'),
(1, 470.00, 'Yol Yakıtı', '2025-11-06', 'FUEL'),
(1, 2350.00, 'Fan Motoru ve Termostat', '2025-11-07', 'MATERIAL'),
(1, 510.00, 'Araç Yakıtı', '2025-11-08', 'FUEL'),
(1, 330.00, 'Personel Yemeği', '2025-11-11', 'FOOD'),
(1, 1640.00, 'Drenaj ve İzolasyon', '2025-11-12', 'MATERIAL'),
(1, 60000.00, 'Maaşlar - Kasım', '2025-11-15', 'SALARY'),
(1, 490.00, 'Yakıt', '2025-11-18', 'FUEL'),
(1, 1920.00, 'Basınç Sensörü ve Valf', '2025-11-19', 'MATERIAL'),
(1, 530.00, 'Yol Gideri', '2025-11-20', 'FUEL'),
(1, 340.00, 'Yemek Gideri', '2025-11-21', 'FOOD'),
(1, 1280.00, 'Kumanda ve Ekran', '2025-11-22', 'MATERIAL'),
(1, 460.00, 'Araç Yakıtı', '2025-11-25', 'FUEL'),
(1, 310.00, 'Çay Kahve', '2025-11-26', 'FOOD');

-- Service Tickets (Generated - 217 total)


INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (1, 1, 9, 3, 'COMPLETED', '2025-09-01 15:00:00', 'Periyodik bakım', 'Fan motoru değiştirildi', 1750, false, '2025-09-01 10:00:00', '2025-09-02 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (2, 1, 2, 3, 'COMPLETED', '2025-09-02 12:00:00', 'Su kaçağı kontrolü', 'Genel kontrol yapıldı', 3860, false, '2025-09-01 12:00:00', '2025-09-02 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (3, 1, 13, 3, 'COMPLETED', '2025-09-03 03:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 1369, false, '2025-09-02 13:00:00', '2025-09-03 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (4, 1, 10, 2, 'COMPLETED', '2025-09-02 16:00:00', 'Klima gaz dolumu', 'Kontrol devam ediyor', 2438, false, '2025-09-02 11:00:00', '2025-09-02 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (5, 1, 13, 2, 'COMPLETED', '2025-09-04 02:00:00', 'Periyodik bakım', 'Bakım tamamlandı', 2240, false, '2025-09-03 14:00:00', '2025-09-04 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (6, 1, 10, 3, 'COMPLETED', '2025-09-03 16:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 1994, false, '2025-09-03 15:00:00', '2025-09-05 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (7, 1, 11, 2, 'COMPLETED', '2025-09-04 18:00:00', 'Su kaçağı kontrolü', 'Genel kontrol yapıldı', 3696, false, '2025-09-04 12:00:00', '2025-09-06 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (8, 1, 4, 2, 'COMPLETED', '2025-09-05 08:00:00', 'Klima gaz dolumu', 'Parça sipariş edildi', 4938, false, '2025-09-04 14:00:00', '2025-09-05 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (9, 1, 4, 2, 'COMPLETED', '2025-09-05 21:00:00', 'Soğutma performans düşüklüğü', 'Filtre değişimi yapıldı', 3079, false, '2025-09-05 08:00:00', '2025-09-07 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (10, 1, 13, 2, 'COMPLETED', '2025-09-06 01:00:00', 'Soğutma performans düşüklüğü', 'Gaz eklendi', 3863, false, '2025-09-05 14:00:00', '2025-09-07 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (11, 1, 1, 2, 'COMPLETED', '2025-09-05 18:00:00', 'Klima gaz dolumu', 'Kontrol devam ediyor', 1029, false, '2025-09-05 14:00:00', '2025-09-05 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (12, 1, 8, 3, 'COMPLETED', '2025-09-06 17:00:00', 'Elektronik kart arızası', 'Genel kontrol yapıldı', 4207, false, '2025-09-06 15:00:00', '2025-09-07 06:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (13, 1, 15, 3, 'COMPLETED', '2025-09-07 06:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 3272, false, '2025-09-06 10:00:00', '2025-09-07 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (14, 1, 12, 3, 'COMPLETED', '2025-09-07 11:00:00', 'Klima ses problemi', 'Filtre değişimi yapıldı', 2941, false, '2025-09-06 16:00:00', '2025-09-08 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (15, 1, 10, 3, 'COMPLETED', '2025-09-07 23:00:00', 'Klima gaz dolumu', 'Kontrol devam ediyor', 1794, false, '2025-09-07 10:00:00', '2025-09-08 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (16, 1, 10, 2, 'COMPLETED', '2025-09-07 19:00:00', 'Klima ses problemi', 'Genel kontrol yapıldı', 3412, false, '2025-09-07 13:00:00', '2025-09-09 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (17, 1, 8, 2, 'COMPLETED', '2025-09-08 13:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 643, false, '2025-09-07 15:00:00', '2025-09-08 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (18, 1, 11, 2, 'COMPLETED', '2025-09-08 19:00:00', 'Elektronik kart arızası', 'Fan motoru değiştirildi', 3934, false, '2025-09-08 14:00:00', '2025-09-09 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (19, 1, 11, 2, 'COMPLETED', '2025-09-09 08:00:00', 'Soğutma performans düşüklüğü', 'Genel kontrol yapıldı', 1856, false, '2025-09-08 08:00:00', '2025-09-10 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (20, 1, 7, 3, 'COMPLETED', '2025-09-08 17:00:00', 'Klima gaz dolumu', 'Parça sipariş edildi', 4538, false, '2025-09-08 09:00:00', '2025-09-09 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (21, 1, 5, 3, 'COMPLETED', '2025-09-10 03:00:00', 'VRF sistem bakımı', 'Bakım tamamlandı', 2162, false, '2025-09-09 11:00:00', '2025-09-11 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (22, 1, 7, 3, 'COMPLETED', '2025-09-09 18:00:00', 'Soğutma performans düşüklüğü', 'Gaz eklendi', 4476, false, '2025-09-09 14:00:00', '2025-09-10 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (23, 1, 5, 2, 'COMPLETED', '2025-09-10 13:00:00', 'Isıtma problemi', 'Fan motoru değiştirildi', 3096, false, '2025-09-10 12:00:00', '2025-09-10 15:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (24, 1, 7, 2, 'COMPLETED', '2025-09-11 08:00:00', 'VRF sistem bakımı', 'Parça sipariş edildi', 1290, false, '2025-09-10 13:00:00', '2025-09-11 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (25, 1, 4, 2, 'COMPLETED', '2025-09-12 15:00:00', 'Elektronik kart arızası', 'Parça sipariş edildi', 1262, false, '2025-09-11 16:00:00', '2025-09-12 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (26, 1, 5, 2, 'COMPLETED', '2025-09-12 12:00:00', 'Kombi arıza kontrolü', 'Termostat değiştirildi', 3699, false, '2025-09-11 15:00:00', '2025-09-13 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (27, 1, 10, 3, 'COMPLETED', '2025-09-11 15:00:00', 'Isıtma problemi', 'Parça sipariş edildi', 1767, false, '2025-09-11 12:00:00', '2025-09-12 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (28, 1, 1, 3, 'COMPLETED', '2025-09-12 15:00:00', 'Isıtma problemi', 'Gaz eklendi', 1197, false, '2025-09-12 11:00:00', '2025-09-14 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (29, 1, 2, 3, 'COMPLETED', '2025-09-12 14:00:00', 'Su kaçağı kontrolü', 'Filtre değişimi yapıldı', 1750, false, '2025-09-12 13:00:00', '2025-09-14 03:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (30, 1, 7, 3, 'COMPLETED', '2025-09-13 19:00:00', 'Isıtma problemi', 'Gaz eklendi', 3200, false, '2025-09-13 15:00:00', '2025-09-15 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (31, 1, 11, 3, 'COMPLETED', '2025-09-13 15:00:00', 'Su kaçağı kontrolü', 'Fan motoru değiştirildi', 2820, false, '2025-09-13 10:00:00', '2025-09-13 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (32, 1, 7, 3, 'COMPLETED', '2025-09-15 04:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 1329, false, '2025-09-14 14:00:00', '2025-09-16 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (33, 1, 15, 3, 'COMPLETED', '2025-09-14 19:00:00', 'Klima ses problemi', 'Genel kontrol yapıldı', 4348, false, '2025-09-14 09:00:00', '2025-09-15 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (34, 1, 9, 3, 'COMPLETED', '2025-09-15 14:00:00', 'Periyodik bakım', 'Kontrol devam ediyor', 4538, false, '2025-09-15 10:00:00', '2025-09-16 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (35, 1, 11, 3, 'COMPLETED', '2025-09-15 13:00:00', 'Kombi arıza kontrolü', 'Parça sipariş edildi', 2205, false, '2025-09-15 08:00:00', '2025-09-17 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (36, 1, 7, 3, 'COMPLETED', '2025-09-16 18:00:00', 'Soğutma performans düşüklüğü', 'Bakım tamamlandı', 4903, false, '2025-09-16 08:00:00', '2025-09-16 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (37, 1, 15, 3, 'COMPLETED', '2025-09-17 09:00:00', 'Periyodik bakım', 'Parça sipariş edildi', 1797, false, '2025-09-16 10:00:00', '2025-09-17 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (38, 1, 12, 2, 'COMPLETED', '2025-09-17 14:00:00', 'Soğutma performans düşüklüğü', 'Parça sipariş edildi', 3343, false, '2025-09-16 14:00:00', '2025-09-18 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (39, 1, 13, 3, 'COMPLETED', '2025-09-17 15:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 1426, false, '2025-09-17 09:00:00', '2025-09-19 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (40, 1, 5, 3, 'COMPLETED', '2025-09-17 22:00:00', 'Periyodik bakım', 'Fan motoru değiştirildi', 1455, false, '2025-09-17 16:00:00', '2025-09-18 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (41, 1, 5, 2, 'COMPLETED', '2025-09-18 00:00:00', 'VRF sistem bakımı', 'Genel kontrol yapıldı', 4183, false, '2025-09-17 09:00:00', '2025-09-17 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (42, 1, 2, 3, 'COMPLETED', '2025-09-18 22:00:00', 'Klima ses problemi', 'Termostat değiştirildi', 1443, false, '2025-09-18 12:00:00', '2025-09-19 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (43, 1, 8, 2, 'COMPLETED', '2025-09-19 00:00:00', 'Klima bakım ve temizlik', 'Bakım tamamlandı', 3544, false, '2025-09-18 09:00:00', '2025-09-19 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (44, 1, 10, 3, 'COMPLETED', '2025-09-20 01:00:00', 'Elektronik kart arızası', 'Fan motoru değiştirildi', 4423, false, '2025-09-19 16:00:00', '2025-09-21 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (45, 1, 2, 3, 'COMPLETED', '2025-09-20 01:00:00', 'Kombi arıza kontrolü', 'Fan motoru değiştirildi', 1501, false, '2025-09-19 16:00:00', '2025-09-19 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (46, 1, 9, 2, 'COMPLETED', '2025-09-21 09:00:00', 'Klima ses problemi', 'Gaz eklendi', 2969, false, '2025-09-20 15:00:00', '2025-09-20 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (47, 1, 12, 3, 'COMPLETED', '2025-09-20 23:00:00', 'Soğutma performans düşüklüğü', 'Genel kontrol yapıldı', 1250, false, '2025-09-20 10:00:00', '2025-09-20 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (48, 1, 7, 2, 'COMPLETED', '2025-09-20 13:00:00', 'Klima gaz dolumu', 'Genel kontrol yapıldı', 3846, false, '2025-09-20 08:00:00', '2025-09-21 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (49, 1, 1, 2, 'COMPLETED', '2025-09-22 06:00:00', 'Soğutma performans düşüklüğü', 'Filtre değişimi yapıldı', 721, false, '2025-09-21 15:00:00', '2025-09-23 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (50, 1, 1, 3, 'COMPLETED', '2025-09-22 02:00:00', 'Elektronik kart arızası', 'Gaz eklendi', 2293, false, '2025-09-21 08:00:00', '2025-09-23 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (51, 1, 2, 3, 'COMPLETED', '2025-09-23 01:00:00', 'Elektronik kart arızası', 'Bakım tamamlandı', 2945, false, '2025-09-22 08:00:00', '2025-09-22 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (52, 1, 13, 3, 'COMPLETED', '2025-09-22 15:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 587, false, '2025-09-22 14:00:00', '2025-09-24 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (53, 1, 4, 3, 'COMPLETED', '2025-09-22 15:00:00', 'Su kaçağı kontrolü', 'Filtre değişimi yapıldı', 3597, false, '2025-09-22 13:00:00', '2025-09-23 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (54, 1, 5, 2, 'COMPLETED', '2025-09-24 11:00:00', 'Isıtma problemi', 'Filtre değişimi yapıldı', 1199, false, '2025-09-23 15:00:00', '2025-09-25 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (55, 1, 9, 3, 'COMPLETED', '2025-09-23 19:00:00', 'Klima gaz dolumu', 'Fan motoru değiştirildi', 1296, false, '2025-09-23 15:00:00', '2025-09-24 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (56, 1, 9, 3, 'COMPLETED', '2025-09-25 06:00:00', 'Elektronik kart arızası', 'Gaz eklendi', 4086, false, '2025-09-24 12:00:00', '2025-09-26 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (57, 1, 1, 2, 'COMPLETED', '2025-09-25 02:00:00', 'Elektronik kart arızası', 'Fan motoru değiştirildi', 2381, false, '2025-09-24 08:00:00', '2025-09-24 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (58, 1, 3, 3, 'COMPLETED', '2025-09-24 22:00:00', 'Klima gaz dolumu', 'Bakım tamamlandı', 4344, false, '2025-09-24 15:00:00', '2025-09-26 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (59, 1, 9, 3, 'COMPLETED', '2025-09-25 16:00:00', 'Su kaçağı kontrolü', 'Parça sipariş edildi', 1538, false, '2025-09-25 09:00:00', '2025-09-25 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (60, 1, 13, 3, 'COMPLETED', '2025-09-26 05:00:00', 'VRF sistem bakımı', 'Genel kontrol yapıldı', 4099, false, '2025-09-25 12:00:00', '2025-09-25 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (61, 1, 7, 2, 'COMPLETED', '2025-09-26 22:00:00', 'Periyodik bakım', 'Fan motoru değiştirildi', 1064, false, '2025-09-26 15:00:00', '2025-09-27 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (62, 1, 14, 2, 'COMPLETED', '2025-09-26 19:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 4170, false, '2025-09-26 11:00:00', '2025-09-26 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (63, 1, 10, 3, 'COMPLETED', '2025-09-27 02:00:00', 'Su kaçağı kontrolü', 'Filtre değişimi yapıldı', 4617, false, '2025-09-26 15:00:00', '2025-09-27 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (64, 1, 4, 3, 'COMPLETED', '2025-09-27 18:00:00', 'VRF sistem bakımı', 'Fan motoru değiştirildi', 598, false, '2025-09-27 08:00:00', '2025-09-28 03:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (65, 1, 13, 3, 'COMPLETED', '2025-09-28 03:00:00', 'Isıtma problemi', 'Genel kontrol yapıldı', 1237, false, '2025-09-27 11:00:00', '2025-09-29 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (66, 1, 12, 2, 'COMPLETED', '2025-09-27 17:00:00', 'VRF sistem bakımı', 'Parça sipariş edildi', 4848, false, '2025-09-27 14:00:00', '2025-09-28 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (67, 1, 1, 3, 'COMPLETED', '2025-09-28 22:00:00', 'Klima bakım ve temizlik', 'Termostat değiştirildi', 4300, false, '2025-09-28 10:00:00', '2025-09-29 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (68, 1, 15, 3, 'COMPLETED', '2025-09-29 01:00:00', 'Elektronik kart arızası', 'Parça sipariş edildi', 3425, false, '2025-09-28 08:00:00', '2025-09-29 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (69, 1, 14, 2, 'COMPLETED', '2025-09-29 22:00:00', 'Klima gaz dolumu', 'Kontrol devam ediyor', 1645, false, '2025-09-29 13:00:00', '2025-09-29 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (70, 1, 1, 2, 'COMPLETED', '2025-09-29 21:00:00', 'Soğutma performans düşüklüğü', 'Filtre değişimi yapıldı', 1605, false, '2025-09-29 14:00:00', '2025-09-30 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (71, 1, 6, 2, 'COMPLETED', '2025-10-01 08:00:00', 'Elektronik kart arızası', 'Termostat değiştirildi', 700, false, '2025-09-30 14:00:00', '2025-09-30 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (72, 1, 10, 2, 'COMPLETED', '2025-10-01 00:00:00', 'Kombi arıza kontrolü', 'Filtre değişimi yapıldı', 1133, false, '2025-09-30 11:00:00', '2025-10-01 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (73, 1, 6, 3, 'COMPLETED', '2025-09-30 19:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 3156, false, '2025-09-30 08:00:00', '2025-09-30 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (74, 1, 12, 2, 'COMPLETED', '2025-10-02 01:00:00', 'Klima bakım ve temizlik', 'Bakım tamamlandı', 2788, false, '2025-10-01 14:00:00', '2025-10-02 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (75, 1, 2, 3, 'COMPLETED', '2025-10-02 04:00:00', 'Kombi arıza kontrolü', 'Filtre değişimi yapıldı', 1159, false, '2025-10-01 12:00:00', '2025-10-02 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (76, 1, 8, 2, 'COMPLETED', '2025-10-03 06:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 3322, false, '2025-10-02 14:00:00', '2025-10-04 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (77, 1, 8, 2, 'COMPLETED', '2025-10-03 05:00:00', 'Klima bakım ve temizlik', 'Filtre değişimi yapıldı', 1129, false, '2025-10-02 13:00:00', '2025-10-03 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (78, 1, 8, 2, 'COMPLETED', '2025-10-03 21:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 798, false, '2025-10-03 14:00:00', '2025-10-05 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (79, 1, 14, 2, 'COMPLETED', '2025-10-04 02:00:00', 'Kombi arıza kontrolü', 'Bakım tamamlandı', 4499, false, '2025-10-03 15:00:00', '2025-10-05 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (80, 1, 8, 2, 'COMPLETED', '2025-10-03 16:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 4974, false, '2025-10-03 12:00:00', '2025-10-04 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (81, 1, 12, 3, 'COMPLETED', '2025-10-05 08:00:00', 'Elektronik kart arızası', 'Filtre değişimi yapıldı', 2158, false, '2025-10-04 11:00:00', '2025-10-04 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (82, 1, 2, 3, 'COMPLETED', '2025-10-04 16:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 4988, false, '2025-10-04 11:00:00', '2025-10-05 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (83, 1, 12, 3, 'COMPLETED', '2025-10-05 15:00:00', 'Klima ses problemi', 'Kontrol devam ediyor', 2522, false, '2025-10-04 16:00:00', '2025-10-05 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (84, 1, 5, 3, 'COMPLETED', '2025-10-05 22:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 1418, false, '2025-10-05 15:00:00', '2025-10-07 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (85, 1, 7, 2, 'COMPLETED', '2025-10-05 11:00:00', 'VRF sistem bakımı', 'Parça sipariş edildi', 1335, false, '2025-10-05 10:00:00', '2025-10-07 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (86, 1, 14, 3, 'COMPLETED', '2025-10-06 06:00:00', 'Kombi arıza kontrolü', 'Termostat değiştirildi', 3935, false, '2025-10-05 12:00:00', '2025-10-07 03:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (87, 1, 7, 3, 'COMPLETED', '2025-10-07 04:00:00', 'VRF sistem bakımı', 'Fan motoru değiştirildi', 4978, false, '2025-10-06 11:00:00', '2025-10-06 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (88, 1, 15, 3, 'COMPLETED', '2025-10-07 09:00:00', 'Klima bakım ve temizlik', 'Kontrol devam ediyor', 926, false, '2025-10-06 14:00:00', '2025-10-07 06:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (89, 1, 3, 3, 'COMPLETED', '2025-10-08 06:00:00', 'Klima ses problemi', 'Parça sipariş edildi', 3729, false, '2025-10-07 08:00:00', '2025-10-07 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (90, 1, 1, 2, 'COMPLETED', '2025-10-08 03:00:00', 'Periyodik bakım', 'Parça sipariş edildi', 2191, false, '2025-10-07 14:00:00', '2025-10-09 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (91, 1, 7, 2, 'COMPLETED', '2025-10-07 18:00:00', 'Klima bakım ve temizlik', 'Genel kontrol yapıldı', 608, false, '2025-10-07 11:00:00', '2025-10-08 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (92, 1, 14, 3, 'COMPLETED', '2025-10-09 07:00:00', 'Soğutma performans düşüklüğü', 'Genel kontrol yapıldı', 2280, false, '2025-10-08 15:00:00', '2025-10-08 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (93, 1, 11, 3, 'COMPLETED', '2025-10-09 04:00:00', 'Periyodik bakım', 'Kontrol devam ediyor', 1161, false, '2025-10-08 11:00:00', '2025-10-08 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (94, 1, 12, 3, 'COMPLETED', '2025-10-09 22:00:00', 'VRF sistem bakımı', 'Kontrol devam ediyor', 632, false, '2025-10-09 09:00:00', '2025-10-09 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (95, 1, 14, 3, 'COMPLETED', '2025-10-10 13:00:00', 'Su kaçağı kontrolü', 'Kontrol devam ediyor', 1558, false, '2025-10-09 13:00:00', '2025-10-11 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (96, 1, 13, 2, 'COMPLETED', '2025-10-11 01:00:00', 'Klima gaz dolumu', 'Gaz eklendi', 532, false, '2025-10-10 09:00:00', '2025-10-11 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (97, 1, 10, 2, 'COMPLETED', '2025-10-11 10:00:00', 'Su kaçağı kontrolü', 'Termostat değiştirildi', 4975, false, '2025-10-10 12:00:00', '2025-10-11 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (98, 1, 6, 3, 'COMPLETED', '2025-10-11 11:00:00', 'Klima ses problemi', 'Filtre değişimi yapıldı', 1188, false, '2025-10-11 10:00:00', '2025-10-13 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (99, 1, 2, 3, 'COMPLETED', '2025-10-12 14:00:00', 'Klima gaz dolumu', 'Filtre değişimi yapıldı', 3266, false, '2025-10-11 16:00:00', '2025-10-13 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (100, 1, 4, 2, 'COMPLETED', '2025-10-13 06:00:00', 'VRF sistem bakımı', 'Genel kontrol yapıldı', 3409, false, '2025-10-12 12:00:00', '2025-10-13 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (101, 1, 5, 3, 'COMPLETED', '2025-10-13 06:00:00', 'Soğutma performans düşüklüğü', 'Kontrol devam ediyor', 614, false, '2025-10-12 15:00:00', '2025-10-13 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (102, 1, 5, 2, 'COMPLETED', '2025-10-13 16:00:00', 'VRF sistem bakımı', 'Gaz eklendi', 4491, false, '2025-10-13 09:00:00', '2025-10-14 03:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (103, 1, 2, 2, 'COMPLETED', '2025-10-13 23:00:00', 'Isıtma problemi', 'Bakım tamamlandı', 1527, false, '2025-10-13 14:00:00', '2025-10-15 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (104, 1, 12, 3, 'COMPLETED', '2025-10-14 18:00:00', 'Isıtma problemi', 'Fan motoru değiştirildi', 2295, false, '2025-10-14 13:00:00', '2025-10-16 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (105, 1, 7, 3, 'COMPLETED', '2025-10-15 01:00:00', 'VRF sistem bakımı', 'Gaz eklendi', 2594, false, '2025-10-14 10:00:00', '2025-10-15 03:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (106, 1, 15, 3, 'COMPLETED', '2025-10-15 06:00:00', 'Klima ses problemi', 'Termostat değiştirildi', 3223, false, '2025-10-14 13:00:00', '2025-10-15 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (107, 1, 5, 2, 'COMPLETED', '2025-10-15 20:00:00', 'Periyodik bakım', 'Filtre değişimi yapıldı', 1314, false, '2025-10-15 15:00:00', '2025-10-16 06:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (108, 1, 9, 3, 'COMPLETED', '2025-10-16 04:00:00', 'Elektronik kart arızası', 'Kontrol devam ediyor', 901, false, '2025-10-15 14:00:00', '2025-10-15 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (109, 1, 8, 2, 'COMPLETED', '2025-10-15 12:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 3422, false, '2025-10-15 11:00:00', '2025-10-17 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (110, 1, 1, 2, 'COMPLETED', '2025-10-16 20:00:00', 'Su kaçağı kontrolü', 'Termostat değiştirildi', 3957, false, '2025-10-16 16:00:00', '2025-10-16 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (111, 1, 10, 3, 'COMPLETED', '2025-10-17 12:00:00', 'Isıtma problemi', 'Fan motoru değiştirildi', 2779, false, '2025-10-16 14:00:00', '2025-10-17 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (112, 1, 7, 3, 'COMPLETED', '2025-10-16 16:00:00', 'Periyodik bakım', 'Filtre değişimi yapıldı', 2718, false, '2025-10-16 08:00:00', '2025-10-17 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (113, 1, 3, 2, 'COMPLETED', '2025-10-18 06:00:00', 'Elektronik kart arızası', 'Gaz eklendi', 2079, false, '2025-10-17 13:00:00', '2025-10-18 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (114, 1, 12, 3, 'COMPLETED', '2025-10-17 13:00:00', 'Periyodik bakım', 'Filtre değişimi yapıldı', 659, false, '2025-10-17 11:00:00', '2025-10-18 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (115, 1, 2, 3, 'COMPLETED', '2025-10-19 08:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 700, false, '2025-10-18 16:00:00', '2025-10-18 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (116, 1, 8, 2, 'COMPLETED', '2025-10-18 20:00:00', 'VRF sistem bakımı', 'Bakım tamamlandı', 3831, false, '2025-10-18 14:00:00', '2025-10-19 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (117, 1, 13, 2, 'COMPLETED', '2025-10-18 17:00:00', 'Elektronik kart arızası', 'Genel kontrol yapıldı', 3848, false, '2025-10-18 08:00:00', '2025-10-19 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (118, 1, 1, 2, 'COMPLETED', '2025-10-20 12:00:00', 'Kombi arıza kontrolü', 'Bakım tamamlandı', 2893, false, '2025-10-19 12:00:00', '2025-10-20 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (119, 1, 12, 2, 'COMPLETED', '2025-10-20 08:00:00', 'Klima bakım ve temizlik', 'Parça sipariş edildi', 3305, false, '2025-10-19 13:00:00', '2025-10-20 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (120, 1, 2, 2, 'COMPLETED', '2025-10-20 20:00:00', 'Klima gaz dolumu', 'Parça sipariş edildi', 3367, false, '2025-10-20 10:00:00', '2025-10-20 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (121, 1, 4, 3, 'COMPLETED', '2025-10-21 07:00:00', 'Soğutma performans düşüklüğü', 'Fan motoru değiştirildi', 3517, false, '2025-10-20 12:00:00', '2025-10-20 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (122, 1, 7, 3, 'COMPLETED', '2025-10-20 13:00:00', 'Kombi arıza kontrolü', 'Genel kontrol yapıldı', 4411, false, '2025-10-20 11:00:00', '2025-10-21 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (123, 1, 8, 2, 'COMPLETED', '2025-10-21 14:00:00', 'Isıtma problemi', 'Bakım tamamlandı', 560, false, '2025-10-21 11:00:00', '2025-10-22 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (124, 1, 8, 2, 'COMPLETED', '2025-10-21 16:00:00', 'Periyodik bakım', 'Fan motoru değiştirildi', 588, false, '2025-10-21 15:00:00', '2025-10-23 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (125, 1, 13, 3, 'COMPLETED', '2025-10-23 08:00:00', 'Klima gaz dolumu', 'Termostat değiştirildi', 711, false, '2025-10-22 10:00:00', '2025-10-23 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (126, 1, 4, 2, 'COMPLETED', '2025-10-23 08:00:00', 'Klima bakım ve temizlik', 'Parça sipariş edildi', 925, false, '2025-10-22 11:00:00', '2025-10-24 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (127, 1, 11, 3, 'COMPLETED', '2025-10-24 01:00:00', 'Klima gaz dolumu', 'Parça sipariş edildi', 4915, false, '2025-10-23 10:00:00', '2025-10-23 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (128, 1, 2, 3, 'COMPLETED', '2025-10-23 14:00:00', 'Isıtma problemi', 'Parça sipariş edildi', 4872, false, '2025-10-23 13:00:00', '2025-10-23 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (129, 1, 12, 3, 'COMPLETED', '2025-10-24 21:00:00', 'Isıtma problemi', 'Genel kontrol yapıldı', 3959, false, '2025-10-24 08:00:00', '2025-10-25 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (130, 1, 8, 2, 'COMPLETED', '2025-10-24 12:00:00', 'Soğutma performans düşüklüğü', 'Fan motoru değiştirildi', 1357, false, '2025-10-24 10:00:00', '2025-10-24 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (131, 1, 8, 3, 'COMPLETED', '2025-10-24 15:00:00', 'VRF sistem bakımı', 'Genel kontrol yapıldı', 4225, false, '2025-10-24 11:00:00', '2025-10-25 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (132, 1, 10, 2, 'COMPLETED', '2025-10-25 23:00:00', 'Elektronik kart arızası', 'Filtre değişimi yapıldı', 753, false, '2025-10-25 12:00:00', '2025-10-26 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (133, 1, 8, 2, 'COMPLETED', '2025-10-26 00:00:00', 'Periyodik bakım', 'Bakım tamamlandı', 2621, false, '2025-10-25 08:00:00', '2025-10-25 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (134, 1, 2, 3, 'COMPLETED', '2025-10-26 20:00:00', 'Elektronik kart arızası', 'Parça sipariş edildi', 1824, false, '2025-10-26 10:00:00', '2025-10-28 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (135, 1, 8, 3, 'COMPLETED', '2025-10-26 16:00:00', 'Isıtma problemi', 'Fan motoru değiştirildi', 3929, false, '2025-10-26 11:00:00', '2025-10-27 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (136, 1, 2, 3, 'COMPLETED', '2025-10-27 17:00:00', 'Elektronik kart arızası', 'Termostat değiştirildi', 4024, false, '2025-10-27 14:00:00', '2025-10-28 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (137, 1, 9, 3, 'COMPLETED', '2025-10-28 11:00:00', 'Su kaçağı kontrolü', 'Kontrol devam ediyor', 833, false, '2025-10-27 14:00:00', '2025-10-29 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (138, 1, 10, 2, 'COMPLETED', '2025-10-27 19:00:00', 'Klima ses problemi', 'Kontrol devam ediyor', 4884, false, '2025-10-27 16:00:00', '2025-10-28 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (139, 1, 13, 2, 'COMPLETED', '2025-10-29 07:00:00', 'Klima ses problemi', 'Genel kontrol yapıldı', 1467, false, '2025-10-28 09:00:00', '2025-10-28 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (140, 1, 15, 3, 'COMPLETED', '2025-10-29 14:00:00', 'Klima gaz dolumu', 'Kontrol devam ediyor', 3915, false, '2025-10-28 15:00:00', '2025-10-30 15:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (141, 1, 7, 2, 'COMPLETED', '2025-10-28 13:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 2726, false, '2025-10-28 09:00:00', '2025-10-29 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (142, 1, 4, 2, 'COMPLETED', '2025-10-29 23:00:00', 'Periyodik bakım', 'Genel kontrol yapıldı', 3192, false, '2025-10-29 11:00:00', '2025-10-30 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (143, 1, 8, 2, 'COMPLETED', '2025-10-30 03:00:00', 'Klima ses problemi', 'Termostat değiştirildi', 1045, false, '2025-10-29 09:00:00', '2025-10-31 06:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (144, 1, 12, 3, 'COMPLETED', '2025-10-31 05:00:00', 'Soğutma performans düşüklüğü', 'Genel kontrol yapıldı', 1352, false, '2025-10-30 11:00:00', '2025-10-31 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (145, 1, 8, 3, 'COMPLETED', '2025-10-31 11:00:00', 'Periyodik bakım', 'Gaz eklendi', 3013, false, '2025-10-30 11:00:00', '2025-10-31 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (146, 1, 3, 2, 'COMPLETED', '2025-10-31 05:00:00', 'Klima ses problemi', 'Bakım tamamlandı', 1006, false, '2025-10-30 16:00:00', '2025-10-31 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (147, 1, 6, 2, 'COMPLETED', '2025-11-01 03:00:00', 'Isıtma problemi', 'Gaz eklendi', 4090, false, '2025-10-31 16:00:00', '2025-11-02 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (148, 1, 8, 3, 'COMPLETED', '2025-11-01 01:00:00', 'Klima bakım ve temizlik', 'Fan motoru değiştirildi', 4336, false, '2025-10-31 10:00:00', '2025-10-31 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (149, 1, 12, 2, 'COMPLETED', '2025-11-01 03:00:00', 'Klima gaz dolumu', 'Gaz eklendi', 695, false, '2025-10-31 08:00:00', '2025-11-01 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (150, 1, 3, 3, 'COMPLETED', '2025-11-02 13:00:00', 'Su kaçağı kontrolü', 'Bakım tamamlandı', 2157, false, '2025-11-01 14:00:00', '2025-11-02 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (151, 1, 6, 3, 'COMPLETED', '2025-11-02 09:00:00', 'Isıtma problemi', 'Fan motoru değiştirildi', 4563, false, '2025-11-01 09:00:00', '2025-11-01 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (152, 1, 13, 2, 'COMPLETED', '2025-11-03 06:00:00', 'Elektronik kart arızası', 'Kontrol devam ediyor', 986, false, '2025-11-02 16:00:00', '2025-11-04 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (153, 1, 9, 2, 'COMPLETED', '2025-11-03 08:00:00', 'Soğutma performans düşüklüğü', 'Genel kontrol yapıldı', 4964, false, '2025-11-02 12:00:00', '2025-11-03 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (154, 1, 8, 2, 'COMPLETED', '2025-11-03 10:00:00', 'Soğutma performans düşüklüğü', 'Termostat değiştirildi', 4215, false, '2025-11-03 09:00:00', '2025-11-05 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (155, 1, 12, 3, 'COMPLETED', '2025-11-04 12:00:00', 'Isıtma problemi', 'Gaz eklendi', 2372, false, '2025-11-03 16:00:00', '2025-11-05 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (156, 1, 14, 2, 'COMPLETED', '2025-11-05 09:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 892, false, '2025-11-04 12:00:00', '2025-11-06 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (157, 1, 4, 2, 'COMPLETED', '2025-11-05 06:00:00', 'Su kaçağı kontrolü', 'Parça sipariş edildi', 1365, false, '2025-11-04 09:00:00', '2025-11-05 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (158, 1, 14, 3, 'COMPLETED', '2025-11-06 06:00:00', 'Elektronik kart arızası', 'Genel kontrol yapıldı', 3519, false, '2025-11-05 16:00:00', '2025-11-06 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (159, 1, 15, 2, 'COMPLETED', '2025-11-05 18:00:00', 'Klima ses problemi', 'Gaz eklendi', 4574, false, '2025-11-05 09:00:00', '2025-11-06 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (160, 1, 15, 2, 'COMPLETED', '2025-11-07 05:00:00', 'Isıtma problemi', 'Parça sipariş edildi', 2327, false, '2025-11-06 12:00:00', '2025-11-07 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (161, 1, 3, 2, 'COMPLETED', '2025-11-07 06:00:00', 'Su kaçağı kontrolü', 'Bakım tamamlandı', 4080, false, '2025-11-06 10:00:00', '2025-11-07 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (162, 1, 6, 2, 'COMPLETED', '2025-11-07 11:00:00', 'Klima gaz dolumu', 'Gaz eklendi', 1766, false, '2025-11-06 11:00:00', '2025-11-08 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (163, 1, 12, 3, 'COMPLETED', '2025-11-08 07:00:00', 'Elektronik kart arızası', 'Termostat değiştirildi', 3103, false, '2025-11-07 09:00:00', '2025-11-09 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (164, 1, 12, 3, 'COMPLETED', '2025-11-08 07:00:00', 'Periyodik bakım', 'Gaz eklendi', 1359, false, '2025-11-07 16:00:00', '2025-11-09 06:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (165, 1, 14, 2, 'COMPLETED', '2025-11-09 10:00:00', 'Kombi arıza kontrolü', 'Kontrol devam ediyor', 4984, false, '2025-11-08 11:00:00', '2025-11-10 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (166, 1, 10, 2, 'COMPLETED', '2025-11-09 00:00:00', 'Periyodik bakım', 'Fan motoru değiştirildi', 1502, false, '2025-11-08 15:00:00', '2025-11-09 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (167, 1, 14, 3, 'COMPLETED', '2025-11-09 06:00:00', 'Kombi arıza kontrolü', 'Kontrol devam ediyor', 4653, false, '2025-11-08 09:00:00', '2025-11-09 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (168, 1, 13, 2, 'COMPLETED', '2025-11-10 09:00:00', 'Soğutma performans düşüklüğü', 'Kontrol devam ediyor', 616, false, '2025-11-09 16:00:00', '2025-11-11 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (169, 1, 3, 2, 'COMPLETED', '2025-11-09 16:00:00', 'Kombi arıza kontrolü', 'Gaz eklendi', 3389, false, '2025-11-09 15:00:00', '2025-11-10 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (170, 1, 2, 3, 'COMPLETED', '2025-11-10 03:00:00', 'Periyodik bakım', 'Gaz eklendi', 1147, false, '2025-11-09 09:00:00', '2025-11-10 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (171, 1, 12, 2, 'COMPLETED', '2025-11-11 05:00:00', 'Klima bakım ve temizlik', 'Termostat değiştirildi', 4927, false, '2025-11-10 11:00:00', '2025-11-11 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (172, 1, 11, 3, 'COMPLETED', '2025-11-10 13:00:00', 'Su kaçağı kontrolü', 'Filtre değişimi yapıldı', 3611, false, '2025-11-10 11:00:00', '2025-11-12 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (173, 1, 2, 3, 'COMPLETED', '2025-11-12 01:00:00', 'Periyodik bakım', 'Gaz eklendi', 3526, false, '2025-11-11 13:00:00', '2025-11-12 17:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (174, 1, 8, 3, 'COMPLETED', '2025-11-12 09:00:00', 'Klima bakım ve temizlik', 'Filtre değişimi yapıldı', 4646, false, '2025-11-11 12:00:00', '2025-11-13 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (175, 1, 1, 2, 'COMPLETED', '2025-11-12 05:00:00', 'VRF sistem bakımı', 'Parça sipariş edildi', 521, false, '2025-11-11 13:00:00', '2025-11-13 12:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (176, 1, 11, 3, 'COMPLETED', '2025-11-13 04:00:00', 'Klima bakım ve temizlik', 'Parça sipariş edildi', 2160, false, '2025-11-12 16:00:00', '2025-11-12 22:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (177, 1, 2, 3, 'COMPLETED', '2025-11-12 20:00:00', 'Klima gaz dolumu', 'Termostat değiştirildi', 1536, false, '2025-11-12 15:00:00', '2025-11-12 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (178, 1, 14, 3, 'COMPLETED', '2025-11-13 15:00:00', 'Su kaçağı kontrolü', 'Termostat değiştirildi', 1546, false, '2025-11-12 16:00:00', '2025-11-14 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (179, 1, 6, 3, 'COMPLETED', '2025-11-13 20:00:00', 'Soğutma performans düşüklüğü', 'Fan motoru değiştirildi', 2865, false, '2025-11-13 10:00:00', '2025-11-13 18:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (180, 1, 15, 2, 'COMPLETED', '2025-11-13 23:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 2649, false, '2025-11-13 15:00:00', '2025-11-14 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (181, 1, 1, 3, 'COMPLETED', '2025-11-14 08:00:00', 'Su kaçağı kontrolü', 'Termostat değiştirildi', 2300, false, '2025-11-13 09:00:00', '2025-11-14 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (182, 1, 11, 2, 'COMPLETED', '2025-11-14 17:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 3803, false, '2025-11-14 16:00:00', '2025-11-16 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (183, 1, 8, 2, 'COMPLETED', '2025-11-15 16:00:00', 'Su kaçağı kontrolü', 'Filtre değişimi yapıldı', 548, false, '2025-11-14 16:00:00', '2025-11-15 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (184, 1, 14, 3, 'COMPLETED', '2025-11-15 03:00:00', 'Soğutma performans düşüklüğü', 'Filtre değişimi yapıldı', 3806, false, '2025-11-14 10:00:00', '2025-11-16 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (185, 1, 10, 2, 'COMPLETED', '2025-11-16 04:00:00', 'Klima bakım ve temizlik', 'Fan motoru değiştirildi', 886, false, '2025-11-15 13:00:00', '2025-11-17 07:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (186, 1, 9, 3, 'COMPLETED', '2025-11-15 14:00:00', 'Su kaçağı kontrolü', 'Gaz eklendi', 1048, false, '2025-11-15 10:00:00', '2025-11-16 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (187, 1, 5, 3, 'COMPLETED', '2025-11-16 20:00:00', 'Klima bakım ve temizlik', 'Genel kontrol yapıldı', 4942, false, '2025-11-16 08:00:00', '2025-11-18 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (188, 1, 2, 2, 'COMPLETED', '2025-11-17 05:00:00', 'Klima bakım ve temizlik', 'Termostat değiştirildi', 1052, false, '2025-11-16 16:00:00', '2025-11-16 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (189, 1, 14, 3, 'COMPLETED', '2025-11-17 03:00:00', 'Su kaçağı kontrolü', 'Kontrol devam ediyor', 1199, false, '2025-11-16 13:00:00', '2025-11-18 02:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (190, 1, 9, 3, 'COMPLETED', '2025-11-18 03:00:00', 'Klima ses problemi', 'Parça sipariş edildi', 710, false, '2025-11-17 09:00:00', '2025-11-18 23:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (191, 1, 9, 2, 'COMPLETED', '2025-11-17 16:00:00', 'VRF sistem bakımı', 'Termostat değiştirildi', 3207, false, '2025-11-17 14:00:00', '2025-11-19 14:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (192, 1, 10, 2, 'COMPLETED', '2025-11-17 15:00:00', 'Periyodik bakım', 'Gaz eklendi', 662, false, '2025-11-17 14:00:00', '2025-11-19 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (193, 1, 14, 2, 'COMPLETED', '2025-11-19 05:00:00', 'Klima gaz dolumu', 'Parça sipariş edildi', 4933, false, '2025-11-18 10:00:00', '2025-11-20 04:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (194, 1, 1, 3, 'COMPLETED', '2025-11-18 22:00:00', 'Isıtma problemi', 'Kontrol devam ediyor', 4396, false, '2025-11-18 12:00:00', '2025-11-19 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (195, 1, 6, 2, 'COMPLETED', '2025-11-20 00:00:00', 'Klima ses problemi', 'Gaz eklendi', 1078, false, '2025-11-19 11:00:00', '2025-11-20 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (196, 1, 10, 3, 'COMPLETED', '2025-11-20 08:00:00', 'Klima bakım ve temizlik', 'Gaz eklendi', 4478, false, '2025-11-19 14:00:00', '2025-11-20 05:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (197, 1, 10, 3, 'COMPLETED', '2025-11-20 09:00:00', 'Elektronik kart arızası', 'Parça sipariş edildi', 2269, false, '2025-11-19 09:00:00', '2025-11-21 01:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (198, 1, 13, 2, 'COMPLETED', '2025-11-20 17:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 4559, false, '2025-11-20 10:00:00', '2025-11-21 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (199, 1, 14, 3, 'COMPLETED', '2025-11-21 04:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 3667, false, '2025-11-20 11:00:00', '2025-11-21 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (200, 1, 1, 2, 'COMPLETED', '2025-11-21 01:00:00', 'Periyodik bakım', '', 0, false, '2025-11-20 16:00:00', '2025-11-20 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (201, 1, 7, 2, 'COMPLETED', '2025-11-22 00:00:00', 'Isıtma problemi', 'Parça sipariş edildi', 3639, false, '2025-11-21 16:00:00', '2025-11-23 00:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (202, 1, 8, 3, 'COMPLETED', '2025-11-21 14:00:00', 'Elektronik kart arızası', 'Termostat değiştirildi', 4990, false, '2025-11-21 12:00:00', '2025-11-22 20:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (203, 1, 8, 2, 'COMPLETED', '2025-11-23 05:00:00', 'Kombi arıza kontrolü', 'Genel kontrol yapıldı', 2295, false, '2025-11-22 13:00:00', '2025-11-23 21:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (204, 1, 7, 3, 'COMPLETED', '2025-11-22 23:00:00', 'Isıtma problemi', '', 0, false, '2025-11-22 13:00:00', '2025-11-22 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (205, 1, 13, 2, 'COMPLETED', '2025-11-22 16:00:00', 'Elektronik kart arızası', 'Fan motoru değiştirildi', 1067, false, '2025-11-22 13:00:00', '2025-11-22 19:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (206, 1, 15, 2, 'COMPLETED', '2025-11-23 14:00:00', 'Isıtma problemi', '', 0, false, '2025-11-23 13:00:00', '2025-11-23 13:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (207, 1, 6, 3, 'COMPLETED', '2025-11-23 15:00:00', 'Klima gaz dolumu', '', 0, false, '2025-11-23 11:00:00', '2025-11-23 11:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (208, 1, 7, 2, 'PENDING', '2025-11-25 08:00:00', 'Isıtma problemi', '', 0, false, '2025-11-24 16:00:00', '2025-11-24 16:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (209, 1, 10, 2, 'PENDING', '2025-11-25 08:00:00', 'Klima ses problemi', '', 0, false, '2025-11-24 08:00:00', '2025-11-24 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (210, 1, 8, 3, 'IN_PROGRESS', '2025-11-24 23:00:00', 'Isıtma problemi', '', 0, false, '2025-11-24 08:00:00', '2025-11-24 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (211, 1, 15, 2, 'IN_PROGRESS', '2025-11-25 20:00:00', 'Su kaçağı kontrolü', '', 0, false, '2025-11-25 09:00:00', '2025-11-25 09:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (212, 1, 8, 2, 'IN_PROGRESS', '2025-11-25 15:00:00', 'Kombi arıza kontrolü', '', 0, false, '2025-11-25 08:00:00', '2025-11-25 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (213, 1, 8, 3, 'IN_PROGRESS', '2025-11-26 14:00:00', 'Su kaçağı kontrolü', '', 0, false, '2025-11-26 08:00:00', '2025-11-26 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (214, 1, 9, 2, 'PENDING', '2025-11-27 07:00:00', 'Klima bakım ve temizlik', '', 0, false, '2025-11-26 08:00:00', '2025-11-26 08:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (215, 1, 9, 3, 'IN_PROGRESS', '2025-11-27 12:00:00', 'Su kaçağı kontrolü', '', 0, false, '2025-11-27 10:00:00', '2025-11-27 10:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (216, 1, 6, 3, 'IN_PROGRESS', '2025-11-28 14:00:00', 'Soğutma performans düşüklüğü', '', 0, false, '2025-11-27 15:00:00', '2025-11-27 15:00:00');
INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES (217, 1, 12, 2, 'ASSIGNED', '2025-11-28 06:00:00', 'Klima bakım ve temizlik', '', 0, false, '2025-11-27 11:00:00', '2025-11-27 11:00:00');
-- Daily Closings (Generated)

INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-01', 1, 0, 25450, -25450, 'CLOSED', '2025-09-01 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-02', 1, 8048, 320, 7728, 'CLOSED', '2025-09-02 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-03', 1, 1369, 1250, 119, 'CLOSED', '2025-09-03 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-04', 1, 2240, 580, 1660, 'CLOSED', '2025-09-04 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-05', 1, 7961, 280, 7681, 'CLOSED', '2025-09-05 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-06', 1, 3696, 1850, 1846, 'CLOSED', '2025-09-06 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-07', 1, 14421, 0, 14421, 'CLOSED', '2025-09-07 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-08', 1, 5378, 420, 4958, 'CLOSED', '2025-09-08 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-09', 1, 11884, 350, 11534, 'CLOSED', '2025-09-09 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-10', 1, 9428, 2100, 7328, 'CLOSED', '2025-09-10 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-11', 1, 3452, 490, 2962, 'CLOSED', '2025-09-11 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-12', 1, 3029, 0, 3029, 'CLOSED', '2025-09-12 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-13', 1, 6519, 0, 6519, 'CLOSED', '2025-09-13 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-14', 1, 2947, 0, 2947, 'CLOSED', '2025-09-14 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-15', 1, 7548, 60000, -52452, 'CLOSED', '2025-09-15 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-16', 1, 10770, 860, 9910, 'CLOSED', '2025-09-16 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-17', 1, 8185, 380, 7805, 'CLOSED', '2025-09-17 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-18', 1, 4798, 1450, 3348, 'CLOSED', '2025-09-18 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-19', 1, 7914, 520, 7394, 'CLOSED', '2025-09-19 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-20', 1, 4219, 310, 3909, 'CLOSED', '2025-09-20 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-21', 1, 8269, 0, 8269, 'CLOSED', '2025-09-21 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-22', 1, 2945, 750, 2195, 'CLOSED', '2025-09-22 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-23', 1, 6611, 440, 6171, 'CLOSED', '2025-09-23 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-24', 1, 4264, 290, 3974, 'CLOSED', '2025-09-24 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-25', 1, 6836, 1920, 4916, 'CLOSED', '2025-09-25 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-26', 1, 12600, 560, 12040, 'CLOSED', '2025-09-26 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-27', 1, 5681, 2350, 3331, 'CLOSED', '2025-09-27 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-28', 1, 5446, 1180, 4266, 'CLOSED', '2025-09-28 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-29', 1, 10607, 670, 9937, 'CLOSED', '2025-09-29 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-09-30', 1, 5461, 420, 5041, 'CLOSED', '2025-09-30 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-01', 1, 1133, 25510, -24377, 'CLOSED', '2025-10-01 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-02', 1, 3947, 340, 3607, 'CLOSED', '2025-10-02 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-03', 1, 1129, 1680, -551, 'CLOSED', '2025-10-03 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-04', 1, 10454, 460, 9994, 'CLOSED', '2025-10-04 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-05', 1, 12807, 0, 12807, 'CLOSED', '2025-10-05 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-06', 1, 4978, 0, 4978, 'CLOSED', '2025-10-06 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-07', 1, 11343, 395, 10948, 'CLOSED', '2025-10-07 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-08', 1, 4049, 2200, 1849, 'CLOSED', '2025-10-08 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-09', 1, 2823, 530, 2293, 'CLOSED', '2025-10-09 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-10', 1, 0, 320, -320, 'CLOSED', '2025-10-10 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-11', 1, 7065, 1540, 5525, 'CLOSED', '2025-10-11 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-12', 1, 0, 0, 0, 'CLOSED', '2025-10-12 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-13', 1, 8477, 0, 8477, 'CLOSED', '2025-10-13 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-14', 1, 4491, 0, 4491, 'CLOSED', '2025-10-14 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-15', 1, 8245, 60000, -51755, 'CLOSED', '2025-10-15 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-16', 1, 7566, 480, 7086, 'CLOSED', '2025-10-16 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-17', 1, 8919, 1890, 7029, 'CLOSED', '2025-10-17 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-18', 1, 3438, 550, 2888, 'CLOSED', '2025-10-18 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-19', 1, 7679, 0, 7679, 'CLOSED', '2025-10-19 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-20', 1, 13082, 0, 13082, 'CLOSED', '2025-10-20 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-21', 1, 4411, 370, 4041, 'CLOSED', '2025-10-21 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-22', 1, 560, 1220, -660, 'CLOSED', '2025-10-22 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-23', 1, 11086, 490, 10596, 'CLOSED', '2025-10-23 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-24', 1, 2282, 2150, 132, 'CLOSED', '2025-10-24 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-25', 1, 10805, 520, 10285, 'CLOSED', '2025-10-25 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-26', 1, 753, 0, 753, 'CLOSED', '2025-10-26 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-27', 1, 3929, 0, 3929, 'CLOSED', '2025-10-27 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-28', 1, 12199, 310, 11889, 'CLOSED', '2025-10-28 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-29', 1, 3559, 2420, 1139, 'CLOSED', '2025-10-29 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-30', 1, 7107, 1150, 5957, 'CLOSED', '2025-10-30 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-10-31', 1, 10752, 690, 10062, 'CLOSED', '2025-10-31 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-01', 1, 5258, 25540, -20282, 'CLOSED', '2025-11-01 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-02', 1, 6247, 0, 6247, 'CLOSED', '2025-11-02 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-03', 1, 4964, 0, 4964, 'CLOSED', '2025-11-03 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-04', 1, 986, 360, 626, 'CLOSED', '2025-11-04 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-05', 1, 7952, 1750, 6202, 'CLOSED', '2025-11-05 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-06', 1, 8985, 470, 8515, 'CLOSED', '2025-11-06 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-07', 1, 6407, 2350, 4057, 'CLOSED', '2025-11-07 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-08', 1, 1766, 510, 1256, 'CLOSED', '2025-11-08 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-09', 1, 10617, 0, 10617, 'CLOSED', '2025-11-09 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-10', 1, 9520, 0, 9520, 'CLOSED', '2025-11-10 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-11', 1, 5543, 330, 5213, 'CLOSED', '2025-11-11 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-12', 1, 10833, 1640, 9193, 'CLOSED', '2025-11-12 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-13', 1, 8032, 0, 8032, 'CLOSED', '2025-11-13 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-14', 1, 6495, 0, 6495, 'CLOSED', '2025-11-14 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-15', 1, 548, 60000, -59452, 'CLOSED', '2025-11-15 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-16', 1, 9709, 0, 9709, 'CLOSED', '2025-11-16 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-17', 1, 886, 0, 886, 'CLOSED', '2025-11-17 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-18', 1, 6851, 490, 6361, 'CLOSED', '2025-11-18 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-19', 1, 8265, 1920, 6345, 'CLOSED', '2025-11-19 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-20', 1, 10489, 530, 9959, 'CLOSED', '2025-11-20 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-21', 1, 10495, 340, 10155, 'CLOSED', '2025-11-21 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-22', 1, 6057, 1280, 4777, 'CLOSED', '2025-11-22 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-23', 1, 5934, 0, 5934, 'CLOSED', '2025-11-23 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-24', 1, 0, 0, 0, 'CLOSED', '2025-11-24 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-25', 1, 0, 460, -460, 'CLOSED', '2025-11-25 23:59:59', 1);
INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('2025-11-26', 1, 0, 310, -310, 'CLOSED', '2025-11-26 23:59:59', 1);
