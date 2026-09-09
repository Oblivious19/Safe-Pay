-- SafePay: five linked sample records for every simplified table.
-- Run this only after 07_simplified_phase1_schema.sql.

-- USERS (5)
INSERT INTO users (user_id, name, email, phone, password) VALUES (seq_user_id.NEXTVAL, 'Rahul Sharma', 'rahul@safepay.test', '9000000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, name, email, phone, password) VALUES (seq_user_id.NEXTVAL, 'Priya Shah', 'priya@safepay.test', '9000000002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, name, email, phone, password) VALUES (seq_user_id.NEXTVAL, 'Amit Mehta', 'amit@safepay.test', '9000000003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, name, email, phone, password) VALUES (seq_user_id.NEXTVAL, 'Neha Verma', 'neha@safepay.test', '9000000004', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO users (user_id, name, email, phone, password) VALUES (seq_user_id.NEXTVAL, 'Arjun Rao', 'arjun@safepay.test', '9000000005', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

-- ACCOUNTS (5): one account per user
INSERT INTO account (account_id, user_id, balance) VALUES (seq_account_id.NEXTVAL, 101, 800000.00);
INSERT INTO account (account_id, user_id, balance) VALUES (seq_account_id.NEXTVAL, 102, 250000.00);
INSERT INTO account (account_id, user_id, balance) VALUES (seq_account_id.NEXTVAL, 103, 125000.00);
INSERT INTO account (account_id, user_id, balance) VALUES (seq_account_id.NEXTVAL, 104, 500000.00);
INSERT INTO account (account_id, user_id, balance) VALUES (seq_account_id.NEXTVAL, 105, 1500000.00);

-- BENEFICIARIES (5): one beneficiary per account
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, bank_account_number, ifsc) VALUES (seq_beneficiary_id.NEXTVAL, 1000001, 'ABC Traders', '123456789001', 'HDFC0001001');
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, bank_account_number, ifsc) VALUES (seq_beneficiary_id.NEXTVAL, 1000002, 'Electricity Board', '123456789002', 'ICIC0001002');
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, bank_account_number, ifsc) VALUES (seq_beneficiary_id.NEXTVAL, 1000003, 'Family Account', '123456789003', 'SBIN0001003');
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, bank_account_number, ifsc) VALUES (seq_beneficiary_id.NEXTVAL, 1000004, 'XYZ Store', '123456789004', 'AXIS0001004');
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, bank_account_number, ifsc) VALUES (seq_beneficiary_id.NEXTVAL, 1000005, 'Supplier One', '123456789005', 'KKBK0001005');

-- TRANSACTIONS (5): demonstrates all four amount ranges.
INSERT INTO transaction_db (transaction_id, transaction_ref, idempotency_key, from_account_id, beneficiary_id, amount, purpose, state, risk_tier, protection_seconds, authentication_required, risk_reason, settled_at)
VALUES (seq_transaction_id.NEXTVAL, 'TXN-00001', 'demo-key-00001', 1000001, 1, 5000.00, 'Groceries', 'SETTLED', 'LOW', 0, 'N', 'Amount ₹5,000 is within the Low-risk range.', SYSTIMESTAMP);

INSERT INTO transaction_db (transaction_id, transaction_ref, idempotency_key, from_account_id, beneficiary_id, amount, purpose, state, risk_tier, protection_seconds, authentication_required, risk_reason, protection_expires_at)
VALUES (seq_transaction_id.NEXTVAL, 'TXN-00002', 'demo-key-00002', 1000002, 2, 25000.00, 'Electricity bill', 'PROTECTED', 'MEDIUM', 10, 'N', 'Amount ₹25,000 is within the Medium-risk range.', SYSTIMESTAMP + INTERVAL '10' SECOND);

INSERT INTO transaction_db (transaction_id, transaction_ref, idempotency_key, from_account_id, beneficiary_id, amount, purpose, state, risk_tier, protection_seconds, authentication_required, risk_reason, protection_expires_at)
VALUES (seq_transaction_id.NEXTVAL, 'TXN-00003', 'demo-key-00003', 1000003, 3, 75000.00, 'Family transfer', 'PROTECTED', 'HIGH', 60, 'N', 'Amount ₹75,000 is within the High-risk range.', SYSTIMESTAMP + INTERVAL '60' SECOND);

INSERT INTO transaction_db (transaction_id, transaction_ref, idempotency_key, from_account_id, beneficiary_id, amount, purpose, state, risk_tier, protection_seconds, authentication_required, risk_reason)
VALUES (seq_transaction_id.NEXTVAL, 'TXN-00004', 'demo-key-00004', 1000004, 4, 150000.00, 'Large purchase', 'HARD_HOLD', 'HARD_HOLD', 0, 'Y', 'Amount ₹1,50,000 is above ₹1,00,000 and requires authentication.');

INSERT INTO transaction_db (transaction_id, transaction_ref, idempotency_key, from_account_id, beneficiary_id, amount, purpose, state, risk_tier, protection_seconds, authentication_required, risk_reason, cancelled_at)
VALUES (seq_transaction_id.NEXTVAL, 'TXN-00005', 'demo-key-00005', 1000005, 5, 45000.00, 'Supplier payment', 'CANCELLED', 'MEDIUM', 10, 'N', 'Amount ₹45,000 is within the Medium-risk range.', SYSTIMESTAMP);

-- APPROVALS (5): placeholder records for the future approval feature.
INSERT INTO approvals (approval_id, transaction_id, reviewer_id, status, comments) VALUES (seq_approval_id.NEXTVAL, 1, 102, 'APPROVED', 'Demo approval record.');
INSERT INTO approvals (approval_id, transaction_id, reviewer_id, status, comments) VALUES (seq_approval_id.NEXTVAL, 2, 103, 'PENDING', 'Demo approval record.');
INSERT INTO approvals (approval_id, transaction_id, reviewer_id, status, comments) VALUES (seq_approval_id.NEXTVAL, 3, 104, 'APPROVED', 'Demo approval record.');
INSERT INTO approvals (approval_id, transaction_id, reviewer_id, status, comments) VALUES (seq_approval_id.NEXTVAL, 4, 105, 'PENDING', 'Demo approval record.');
INSERT INTO approvals (approval_id, transaction_id, reviewer_id, status, comments) VALUES (seq_approval_id.NEXTVAL, 5, 101, 'REJECTED', 'Demo approval record.');

-- AUDIT LOG (5)
INSERT INTO audit_log (audit_id, transaction_id, user_id, action, new_state) VALUES (seq_audit_id.NEXTVAL, 1, 101, 'TRANSACTION_SETTLED', 'SETTLED');
INSERT INTO audit_log (audit_id, transaction_id, user_id, action, new_state) VALUES (seq_audit_id.NEXTVAL, 2, 102, 'PROTECTION_STARTED', 'PROTECTED');
INSERT INTO audit_log (audit_id, transaction_id, user_id, action, new_state) VALUES (seq_audit_id.NEXTVAL, 3, 103, 'PROTECTION_STARTED', 'PROTECTED');
INSERT INTO audit_log (audit_id, transaction_id, user_id, action, new_state) VALUES (seq_audit_id.NEXTVAL, 4, 104, 'HARD_HOLD_APPLIED', 'HARD_HOLD');
INSERT INTO audit_log (audit_id, transaction_id, user_id, action, old_state, new_state) VALUES (seq_audit_id.NEXTVAL, 5, 105, 'TRANSACTION_CANCELLED', 'PROTECTED', 'CANCELLED');

COMMIT;
