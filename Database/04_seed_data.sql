-- SafePay Phase 1: synthetic data for development and demonstration only.
-- Password values below are BCrypt hashes, never plaintext passwords.

INSERT INTO roles (role_id, role_name, description) VALUES (1, 'CUSTOMER', 'Retail SafePay customer');
INSERT INTO roles (role_id, role_name, description) VALUES (2, 'MAKER', 'Corporate payment initiator');
INSERT INTO roles (role_id, role_name, description) VALUES (3, 'CHECKER', 'Corporate payment reviewer');
INSERT INTO roles (role_id, role_name, description) VALUES (4, 'ADMIN', 'SafePay administrator');

INSERT INTO banks (bank_id, bank_name, bank_code) VALUES (1, 'SafePay Demo Bank', 'SPDBIN01');
INSERT INTO banks (bank_id, bank_name, bank_code) VALUES (2, 'Demo Merchant Bank', 'DMBKIN01');

INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash, created_at, updated_at)
VALUES (1001, 1, 'Rahul Sharma', 'rahul@safepay.test', '9000000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TO_TIMESTAMP('2026-08-01 09:00:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-01 09:00:00', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash) VALUES (1002, 1, 'Amit Mehta', 'amit@safepay.test', '9000000002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash) VALUES (1003, 1, 'Priya Shah', 'priya@safepay.test', '9000000003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash) VALUES (1007, 2, 'Meera Iyer', 'meera@safepay.test', '9000000007', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash) VALUES (1008, 3, 'Vikram Singh', 'vikram@safepay.test', '9000000008', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, role_id, user_name, email, phone, password_hash) VALUES (1009, 4, 'Admin User', 'admin@safepay.test', '9000000009', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

INSERT INTO accounts (account_id, user_id, bank_id, account_number, account_type, balance) VALUES (5001, 1001, 1, 'XXXX1001', 'SAVINGS', 800000.00);
INSERT INTO accounts (account_id, user_id, bank_id, account_number, account_type, balance) VALUES (5002, 1002, 1, 'XXXX1002', 'SAVINGS', 250000.00);
INSERT INTO accounts (account_id, user_id, bank_id, account_number, account_type, balance) VALUES (5003, 1003, 1, 'XXXX1003', 'SAVINGS', 125000.00);
INSERT INTO accounts (account_id, user_id, bank_id, account_number, account_type, balance) VALUES (5007, 1007, 1, 'XXXX1007', 'CURRENT', 2500000.00);

INSERT INTO beneficiaries (beneficiary_id, user_id, bank_id, beneficiary_name, account_number, added_at) VALUES (2001, 1001, 2, 'ABC Traders', 'XXXX9001', TO_TIMESTAMP('2026-07-20 10:00:00', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO beneficiaries (beneficiary_id, user_id, bank_id, beneficiary_name, account_number, added_at) VALUES (2002, 1001, 2, 'New Vendor', 'XXXX9002', TO_TIMESTAMP('2026-08-30 09:45:00', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO beneficiaries (beneficiary_id, user_id, bank_id, beneficiary_name, account_number, added_at) VALUES (2003, 1002, 2, 'Electricity Co', 'XXXX9003', TO_TIMESTAMP('2026-07-15 11:00:00', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO beneficiaries (beneficiary_id, user_id, bank_id, beneficiary_name, account_number, added_at) VALUES (2004, 1003, 1, 'Family Account', 'XXXX9004', TO_TIMESTAMP('2026-07-10 12:00:00', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO beneficiaries (beneficiary_id, user_id, bank_id, beneficiary_name, account_number, added_at) VALUES (2007, 1007, 2, 'Vendor A', 'XXXX9007', TO_TIMESTAMP('2026-08-28 16:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (1, 'NEW_BENEFICIARY', 'New beneficiary', 'Beneficiary was added recently.', 30);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (2, 'HIGH_AMOUNT', 'High amount', 'Amount is above the configured high-value threshold.', 25);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (3, 'FIRST_TRANSACTION', 'First transaction', 'No prior successful payment to this beneficiary.', 20);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (4, 'UNUSUAL_AMOUNT', 'Unusual amount', 'Amount is unusual for the customer history.', 15);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (5, 'NEW_DEVICE', 'New device', 'Device has not been seen previously.', 20);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (6, 'UNUSUAL_LOCATION', 'Unusual location', 'Location differs from the normal pattern.', 15);
INSERT INTO risk_factors (risk_factor_id, factor_code, factor_name, description, weight) VALUES (7, 'UNUSUAL_TIME', 'Unusual time', 'Time is outside the normal payment pattern.', 5);

INSERT INTO protection_rules (rule_id, risk_tier, min_score, max_score, policy_action, protection_seconds, priority) VALUES (1, 'LOW', 0, 30, 'INSTANT_SETTLEMENT', 0, 1);
INSERT INTO protection_rules (rule_id, risk_tier, min_score, max_score, policy_action, protection_seconds, priority) VALUES (2, 'MEDIUM', 31, 60, 'SHORT_PROTECTION', 10, 1);
INSERT INTO protection_rules (rule_id, risk_tier, min_score, max_score, policy_action, protection_seconds, priority) VALUES (3, 'HIGH', 61, 85, 'PROTECTION_WINDOW', 60, 1);
INSERT INTO protection_rules (rule_id, risk_tier, min_score, max_score, policy_action, protection_seconds, priority) VALUES (4, 'VERY_HIGH', 86, NULL, 'HARD_HOLD', 0, 1);

INSERT INTO user_safety_settings (setting_id, user_id, amount_threshold, protection_seconds, new_beneficiary_protection, additional_verification_amount) VALUES (1, 1001, 50000.00, 30, 'Y', 500000.00);
INSERT INTO user_safety_settings (setting_id, user_id, amount_threshold, protection_seconds, new_beneficiary_protection, additional_verification_amount) VALUES (2, 1007, 100000.00, 60, 'Y', 500000.00);

INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, settled_at, updated_at)
VALUES (10001, 'SP-20260830-001', 'REQ-20260830-001', 5001, 2001, 500.00, 5, 'LOW', 'INSTANT_SETTLEMENT', 1, 'N', 0, 'SETTLED', 'Normal payment', 1001, TO_TIMESTAMP('2026-08-30 09:10:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 09:10:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 09:10:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 09:10:03', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 09:10:03', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, protection_start, protection_end, cancelled_at, updated_at)
VALUES (10002, 'SP-20260830-002', 'REQ-20260830-002', 5001, 2002, 450000.00, 65, 'HIGH', 'PROTECTION_WINDOW', 3, 'Y', 60, 'CANCELLED', 'Customer cancelled during protection window', 1001, TO_TIMESTAMP('2026-08-30 10:05:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:05:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:05:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:05:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:06:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:05:20', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:05:20', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, protection_start, protection_end, settled_at, updated_at)
VALUES (10003, 'SP-20260830-003', 'REQ-20260830-003', 5001, 2002, 450000.00, 65, 'HIGH', 'PROTECTION_WINDOW', 3, 'Y', 60, 'SETTLED', 'Customer released protected payment', 1001, TO_TIMESTAMP('2026-08-30 10:10:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:10:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:10:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:10:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:11:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:10:40', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:10:40', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, updated_at)
VALUES (10004, 'SP-20260830-004', 'REQ-20260830-004', 5007, 2007, 1000000.00, 110, 'VERY_HIGH', 'HARD_HOLD', 4, 'N', 0, 'PENDING_APPROVAL', 'Additional approval required', 1007, TO_TIMESTAMP('2026-08-30 10:40:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:40:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:40:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:40:03', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, protection_start, protection_end, settled_at, updated_at)
VALUES (10005, 'SP-20260830-005', 'REQ-20260830-005', 5002, 2003, 30000.00, 45, 'MEDIUM', 'SHORT_PROTECTION', 2, 'Y', 10, 'SETTLED', 'Protection window expired under project policy', 1002, TO_TIMESTAMP('2026-08-30 10:30:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:12', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:12', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 10:30:12', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO transactions (transaction_id, transaction_reference, request_reference, sender_account_id, beneficiary_id, amount, risk_score, risk_tier, policy_action, applied_rule_id, protection_required, protection_seconds, status, status_reason, created_by, created_at, authorized_at, risk_assessed_at, settled_at, updated_at)
VALUES (10006, 'SP-20260830-006', 'REQ-20260830-006', 5003, 2004, 5000.00, 10, 'LOW', 'INSTANT_SETTLEMENT', 1, 'N', 0, 'DISPUTED', 'Customer raised a simulated post-settlement dispute', 1003, TO_TIMESTAMP('2026-08-30 11:00:00', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 11:00:01', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 11:00:02', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 11:00:03', 'YYYY-MM-DD HH24:MI:SS'), TO_TIMESTAMP('2026-08-30 11:05:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO transaction_context (context_id, transaction_id, device_id, device_known, ip_address, city, country, location_known, channel, session_id) VALUES (1, 10001, 'DEVICE-R1', 'Y', '198.51.100.10', 'Mumbai', 'India', 'Y', 'WEB', 'SESSION-10001');
INSERT INTO transaction_context (context_id, transaction_id, device_id, device_known, ip_address, city, country, location_known, channel, session_id) VALUES (2, 10002, 'DEVICE-R1', 'Y', '198.51.100.10', 'Mumbai', 'India', 'Y', 'WEB', 'SESSION-10002');
INSERT INTO transaction_context (context_id, transaction_id, device_id, device_known, ip_address, city, country, location_known, channel, session_id) VALUES (3, 10004, 'DEVICE-M-NEW', 'N', '203.0.113.88', 'Pune', 'India', 'N', 'WEB', 'SESSION-10004');

INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10002, 1, 30, 'Beneficiary 2002 was added recently');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10002, 3, 20, 'No previous payment to beneficiary 2002');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10002, 4, 15, '450000 compared with customer history');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10003, 1, 30, 'Beneficiary 2002 was added recently');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10003, 3, 20, 'No previous payment to beneficiary 2002');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10003, 4, 15, '450000 compared with customer history');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10004, 1, 30, 'Beneficiary 2007 was added recently');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10004, 2, 25, '1000000 exceeds high-value threshold');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10004, 3, 20, 'No previous payment to beneficiary 2007');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10004, 5, 20, 'New device DEVICE-M-NEW');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10004, 6, 15, 'Unusual location');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10005, 2, 25, '30000 exceeds the configured customer threshold');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10005, 4, 15, '30000 compared with customer history');
INSERT INTO transaction_risk_factors (transaction_id, risk_factor_id, score_added, detected_value) VALUES (10005, 7, 5, 'Transaction at an unusual time');

INSERT INTO approvals (approval_id, transaction_id, maker_id, action, comments, created_at) VALUES (1, 10004, 1007, 'PENDING', 'Awaiting checker verification for the simulated payment.', TO_TIMESTAMP('2026-08-30 10:40:03', 'YYYY-MM-DD HH24:MI:SS'));
INSERT INTO disputes (dispute_id, transaction_id, user_id, dispute_type, description, status, created_at) VALUES (1, 10006, 1003, 'CUSTOMER_COMPLAINT', 'Synthetic post-settlement dispute for demonstration.', 'OPEN', TO_TIMESTAMP('2026-08-30 11:05:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (1, 1001, 10002, 'PROTECT_TRANSACTION', 'RISK_ASSESSED', 'PROTECTED', TO_TIMESTAMP('2026-08-30 10:05:02', 'YYYY-MM-DD HH24:MI:SS'), 'High-risk payment protected.');
INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (2, 1001, 10002, 'CANCEL_TRANSACTION', 'PROTECTED', 'CANCELLED', TO_TIMESTAMP('2026-08-30 10:05:20', 'YYYY-MM-DD HH24:MI:SS'), 'Customer cancelled during the protection window.');
INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (3, 1001, 10003, 'PROTECT_TRANSACTION', 'RISK_ASSESSED', 'PROTECTED', TO_TIMESTAMP('2026-08-30 10:10:02', 'YYYY-MM-DD HH24:MI:SS'), 'High-risk payment protected.');
INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (4, 1001, 10003, 'RELEASE_TRANSACTION', 'PROTECTED', 'SETTLED', TO_TIMESTAMP('2026-08-30 10:10:40', 'YYYY-MM-DD HH24:MI:SS'), 'Customer continued the protected payment.');
INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (5, 1007, 10004, 'HOLD_TRANSACTION', 'RISK_ASSESSED', 'PENDING_APPROVAL', TO_TIMESTAMP('2026-08-30 10:40:03', 'YYYY-MM-DD HH24:MI:SS'), 'Very-high-risk payment requires a checker.');
INSERT INTO audit_log (audit_id, user_id, transaction_id, action, old_status, new_status, event_timestamp, remarks) VALUES (6, 1003, 10006, 'CREATE_DISPUTE', 'SETTLED', 'DISPUTED', TO_TIMESTAMP('2026-08-30 11:05:00', 'YYYY-MM-DD HH24:MI:SS'), 'Synthetic post-settlement dispute created.');

COMMIT;
