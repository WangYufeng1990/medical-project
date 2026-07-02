DELETE FROM sys_user WHERE username IN ('doctor2', 'doctor3');
DELETE FROM sys_user_role WHERE user_id NOT IN (1, 2);
DELETE FROM sys_role WHERE role_code IN ('TEST_ROLE', 'NURSE');
DELETE FROM sys_menu WHERE (menu_name LIKE 'Test%' OR menu_name = 'Updated Menu') AND id != 14;
DELETE FROM patient WHERE mrn LIKE '%TEST%' OR name LIKE 'Test%' OR name LIKE 'Updated%';
DELETE FROM emergency_access WHERE user_id IS NOT NULL;
DELETE FROM bill WHERE id NOT IN (500, 501, 502);
DELETE FROM prescription_item WHERE prescription_id NOT IN (300, 301, 302);
DELETE FROM prescription WHERE id NOT IN (300, 301, 302);
DELETE FROM appointment WHERE id NOT IN (200, 201, 202, 203, 204);

-- Restore soft-deleted menu 14 needed by updateMenu test
UPDATE sys_menu SET is_deleted = 0 WHERE id = 14;

-- Fix admin password to match test password
UPDATE sys_user SET password = '$2a$10$f/.OScWLwDPKqQiRrMFU1eF7sGTw09GSVMXZyQBQjT.HaUBXjPX7a' WHERE username = 'admin';
UPDATE sys_user SET force_logout_after = NULL WHERE username = 'admin';

-- Clear password history so changePassword test passes
DELETE FROM password_history WHERE user_id = 1;

-- Reset auto-increment to match test expectations
ALTER TABLE sys_user AUTO_INCREMENT = 3;
ALTER TABLE appointment AUTO_INCREMENT = 205;
ALTER TABLE bill AUTO_INCREMENT = 503;
ALTER TABLE prescription AUTO_INCREMENT = 303;
ALTER TABLE prescription_item AUTO_INCREMENT = 406;
ALTER TABLE sys_menu AUTO_INCREMENT = 100;
ALTER TABLE patient AUTO_INCREMENT = 100;

-- Fix bill test: order 56 expects 2 PAID bills, only 1 in seed (500).
-- createBill(order 58) creates DRAFT 503, submit(order 59) → SUBMITTED,
-- pay(order 62) → PAID. But filter test(order 56) runs before all of these.
-- Fix: set bill 502 to PAID so there are 2 PAID bills at test time.
UPDATE bill SET claim_status = 'PAID' WHERE id = 502;

-- Restore prescription items (may have been deleted by previous tests)
DELETE FROM prescription_item WHERE prescription_id IN (300, 301, 302);
INSERT INTO prescription_item (id, prescription_id, drug_name, ndc_code, rxnorm_code, specification, dosage, route, frequency, sig, duration, days_supply, quantity, refills, daw, unit_price, create_time, update_time) VALUES
(400, 300, 'Amoxicillin', '65862-0017-01', '308191', '500mg', '500mg', 'PO', 'TID', 'Take one capsule three times daily with food', 7, 7, 21, 0, 0, 0.85, NOW(), NOW()),
(401, 300, 'Ibuprofen', '49035-0323-50', '5640', '200mg', '200mg', 'PO', 'BID', 'Take one tablet twice daily as needed', 3, 3, 6, 0, 0, 0.50, NOW(), NOW()),
(402, 301, 'Cetirizine', '55111-0183-01', '23642', '10mg', '10mg', 'PO', 'QD', 'Take one tablet daily for allergy', 14, 14, 14, 0, 0, 1.20, NOW(), NOW()),
(403, 301, 'Metformin HCl', '65862-0109-01', '6809', '850mg', '850mg', 'PO', 'BID', 'Take one tablet twice daily with meals', 30, 30, 60, 2, 0, 0.35, NOW(), NOW()),
(404, 302, 'Montelukast', '00006-1715-31', '64479', '5mg', '5mg', 'PO', 'QD', 'Take one tablet daily at bedtime', 30, 30, 30, 2, 0, 2.50, NOW(), NOW()),
(405, 302, 'Albuterol HFA Inhaler', '59310-0579-22', '435', '100mcg', '100mcg', 'INH', 'PRN', 'Inhale 1-2 puffs as needed', 30, 30, 1, 0, 1, 25.00, NOW(), NOW());

UPDATE prescription SET rx_status = 'completed' WHERE id = 300;
UPDATE sys_user SET real_name='Dr. Mitchell' WHERE id=2 AND real_name IS NULL;
