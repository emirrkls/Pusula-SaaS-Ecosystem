SELECT 'Data Check' as info;
SELECT id, name, subscription_status FROM companies;
SELECT id, username, role, company_id FROM users WHERE username = 'admin';
SELECT COUNT(*) as expense_count FROM expenses WHERE company_id = 1;
SELECT COUNT(*) as ticket_count FROM service_tickets WHERE company_id = 1;
