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
DELETE FROM refill_request;  -- refill tests create their own PENDING rows (Review III C8 dedup)

-- Restore menu 14 needed by updateMenu test (seed only defines 1-5, 10-13)
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, icon, type, permission, sort, status, create_time, update_time)
SELECT 14, 0, 'Reports', '/reports', 'reports/index', 'DataAnalysis', 'MENU', 'report:list', 60, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 14);
UPDATE sys_menu SET is_deleted = 0 WHERE id = 14;

-- Fix admin password to match test password
UPDATE sys_user SET password = '$2a$10$f/.OScWLwDPKqQiRrMFU1eF7sGTw09GSVMXZyQBQjT.HaUBXjPX7a' WHERE username = 'admin';
UPDATE sys_user SET force_logout_after = NULL WHERE username = 'admin';

-- Clear password history so changePassword test passes
DELETE FROM password_history WHERE user_id = 1;

-- Reset identity counters (H2 syntax — MySQL-style AUTO_INCREMENT=N is silently
-- ignored by H2, leaving counters at the first seeded id and colliding on insert)
ALTER TABLE sys_user ALTER COLUMN id RESTART WITH 3;
ALTER TABLE appointment ALTER COLUMN id RESTART WITH 205;
ALTER TABLE bill ALTER COLUMN id RESTART WITH 503;
ALTER TABLE prescription ALTER COLUMN id RESTART WITH 303;
ALTER TABLE prescription_item ALTER COLUMN id RESTART WITH 406;
ALTER TABLE sys_menu ALTER COLUMN id RESTART WITH 100;
ALTER TABLE patient ALTER COLUMN id RESTART WITH 104;

-- AppointmentScheduler marks past scheduled appointments no-show on context
-- startup — restore seed 202/203 to scheduled so the status filter test passes.
UPDATE appointment SET status = 0 WHERE id IN (202, 203);

-- Fix bill test: order 56 expects 2 PAID bills, only 1 in seed (500).
-- createBill(order 58) creates DRAFT 503, submit(order 59) → SUBMITTED,
-- pay(order 62) → PAID. But filter test(order 56) runs before all of these.
-- Fix: set bill 502 to PAID so there are 2 PAID bills at test time.
UPDATE bill SET claim_status = 'PAID' WHERE id = 502;

-- Restore prescription items (may have been deleted by previous tests)
DELETE FROM prescription_item WHERE prescription_id IN (300, 301, 302);
INSERT INTO prescription_item (id, prescription_id, drug_name, ndc_code, rxnorm_code, specification, dosage, route, frequency, sig, duration, days_supply, quantity, refills, daw, unit_price, create_time, update_time) VALUES
(400, 300, '01e9716553fd7f535b6f4c226788884b4b181c8b3c3cf31a7aba28966c0297e17010c6457e07816d', '65862-0017-01', '308191', '500mg', '01946ab0f31eecfca5c4fb8c1a2570284ffc6ed54b41a776f3a194ddf9f38d5028f5', 'PO', 'TID', '0165c2101a0c27fd461f703834f3a412d9542caf3672768921674f273176ec4af3f492cacacd550891f30160c2522293ecc84f67e881f2d9590c81c2d95893bd64185d898b49960f30', 7, 7, 21, 0, 0, 0.85, NOW(), NOW()),
(401, 300, '01fc2096c337cda2e262a1bc86e5e8087c13171ac7c424fa83f91d35520d270a450ffbf10852', '49035-0323-50', '5640', '200mg', '019ffb0cdf2800dca4c12b8e4511ab815137373bbdc788b2162f4b84c80ac9dae4f6', 'PO', 'BID', '0129d0a19374221aff4a92ad299d2c276013eb519d00e557ad773f5971218f4872f2029ed52a312786b4d6f8d459f1fea65ac2c55de8b568120ab5165c0a96edf977', 3, 3, 6, 0, 0, 0.50, NOW(), NOW()),
(402, 301, '0143c13ffc063c804386ef69c1d1ba5ecb79591ae123c15015dec536f48bfa6d83ffcd032edaa1', '55111-0183-01', '23642', '10mg', '018fca7afba0acfd41ecd7dc83a02048e0fada8376da6b0bc964d37b4d780eadab', 'PO', 'QD', '019c93acb6d3e56fe10811fbcc18f420b25bb98a7e103a96376f8b25bc1838bd51246c45a31404e55552e5b2adf87ebb95efd0783e2754ba29b49fd3dd2c', 14, 14, 14, 0, 0, 1.20, NOW(), NOW()),
(403, 301, '01fef8549ab81c83dd31683e4b4ca86142a7e78c04dbe4070f6a71548ecc9ff3086ae6ab7d795d3ed06f', '65862-0109-01', '6809', '850mg', '0112b6eb5b67f70cbbe662f61932b5d6200132b964536e2ef819695d11f853d07777', 'PO', 'BID', '01a65165377ef3104eaf9bf7462a05267e86383f23e8809c74e6d541476039464d86095695cbdc0d28cbf1bcf9d04b6b5802139a4309ad6f5ae281eaed5b1b3493d215', 30, 30, 60, 2, 0, 0.35, NOW(), NOW()),
(404, 302, '0134862adb480e1028019b45a4cbae7618bd04374b511a38fee4c6f2253120683d5dcaffa77c5928', '00006-1715-31', '64479', '5mg', '01d86d72ef6b32e1b269cfecaa769f87c30bb5b59f9cc0fec3805f9b19b139f7', 'PO', 'QD', '01534fa4b21206d74cd43275c6c04eed6cff86f27b40b70f956f35b91f5ccbd584d1777d287086edbbd28277b68f441dd013b0680709adcce322a37336', 30, 30, 30, 2, 0, 2.50, NOW(), NOW()),
(405, 302, '0164fa04fcdab3eeeaea8e29ca88ece3af8faf9ea55cadef2eeb6c9558d29838f5136b50120f0aa43c44dbb03acfac091463', '59310-0579-22', '435', '100mcg', '01be9d754a1a4f39ffd49476d725722db5173f105494f0ce16a5f8a8dbecaf56157367', 'INH', 'PRN', '01b26d79c7266ebca1949bd78e232d69ca5621efc44d9393d53d63c4493e33c123094d9d473680d201cfb2ee1c73f3e1c4d939f0abfcd4', 30, 30, 1, 0, 1, 25.00, NOW(), NOW());

UPDATE prescription SET rx_status = 'completed' WHERE id = 300;
UPDATE prescription SET rx_status = 'active' WHERE id IN (301, 302);  -- restore for transmit test (Review III C4 generated status)
UPDATE sys_user SET real_name='Dr. Mitchell' WHERE id=2 AND real_name IS NULL;
