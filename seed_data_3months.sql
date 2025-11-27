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

-- Service Tickets - September 2025
INSERT INTO service_tickets (company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at) VALUES
(1, 1, 2, 'COMPLETED', '2025-09-02 10:00:00', 'Klima bakım ve temizlik', 'Filtre değişimi yapıldı', 850.00, false, '2025-09-01 14:30:00', '2025-09-02 15:45:00'),
(1, 3, 3, 'COMPLETED', '2025-09-02 14:00:00', 'Kombi arıza kontrolü', 'Termostat değiştirildi', 1200.00, false, '2025-09-01 16:20:00', '2025-09-02 17:30:00'),
(1, 5, 2, 'COMPLETED', '2025-09-03 09:30:00', 'Klima gaz dolumu', 'R410A gaz eklendi', 950.00, false, '2025-09-02 11:00:00', '2025-09-03 12:15:00'),
(1, 2, 3, 'COMPLETED', '2025-09-03 13:00:00', 'VRF sistem bakımı', 'Genel kontrol yapıldı', 2500.00, false, '2025-09-02 15:45:00', '2025-09-03 16:30:00'),
(1, 7, 2, 'COMPLETED', '2025-09-04 11:00:00', 'Klima ses problemi', 'Fan motoru değiştirildi', 1450.00, false, '2025-09-03 10:30:00', '2025-09-04 14:20:00'),
(1, 4, 3, 'COMPLETED', '2025-09-04 15:30:00', 'Kombi elektrik arızası', 'Elektronik kart tamiri', 1800.00, false, '2025-09-03 13:15:00', '2025-09-04 17:00:00');

-- Continue with more service tickets...
-- (Due to length, I'll create a Python script to generate the remaining data)
