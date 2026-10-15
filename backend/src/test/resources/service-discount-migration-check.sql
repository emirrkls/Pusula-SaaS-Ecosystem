-- Isolated PostgreSQL smoke test only; never run against an application database.
\set ON_ERROR_STOP on
CREATE TABLE service_tickets (id BIGINT PRIMARY KEY, invoice_total NUMERIC(14,2));
INSERT INTO service_tickets VALUES (1, 500);
\i /migrations/V48__service_ticket_discount.sql
INSERT INTO service_tickets VALUES (2, 4500, 'PERCENT', 10, 500);
DO $check$
BEGIN
    IF (SELECT discount_amount FROM service_tickets WHERE id = 1) IS NOT NULL THEN
        RAISE EXCEPTION 'Legacy ticket changed';
    END IF;
    BEGIN
        INSERT INTO service_tickets VALUES (3, 0, 'AMOUNT', -1, -1);
        RAISE EXCEPTION 'Negative discount was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO service_tickets VALUES (4, 0, 'PERCENT', 101, 500);
        RAISE EXCEPTION 'Over 100 percent was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO service_tickets VALUES (5, 0, 'INVALID', 0, 0);
        RAISE EXCEPTION 'Unknown discount type was accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $check$;
SELECT 'PASS: legacy rows preserved, valid discount saved, invalid discounts rejected' AS result;
