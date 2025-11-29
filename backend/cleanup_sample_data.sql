-- Cleanup Script - Remove ALL Sample Data Before Going Live
-- WARNING: This will delete all data with the sample company ID
-- Run this ONLY when you're ready to start with real production data

BEGIN;

-- Delete sample data (cascading deletes will handle related records)
DELETE FROM companies WHERE id = '11111111-1111-1111-1111-111111111111';

-- Verification - Should return 0 for all
SELECT COUNT(*) as remaining_companies FROM companies;
SELECT COUNT(*) as remaining_users FROM users;
SELECT COUNT(*) as remaining_customers FROM customers;
SELECT COUNT(*) as remaining_inventory FROM inventory;
SELECT COUNT(*) as remaining_tickets FROM service_tickets;
SELECT COUNT(*) as remaining_expenses FROM expenses;
SELECT COUNT(*) as remaining_fixed_expenses FROM fixed_expenses;
SELECT COUNT(*) as remaining_daily_closings FROM daily_closings;

-- If everything looks good, commit:
-- COMMIT;

-- If you see unexpected data, rollback:
-- ROLLBACK;

COMMIT;

SELECT 'Sample data cleanup completed! Database is ready for production.' as status;
