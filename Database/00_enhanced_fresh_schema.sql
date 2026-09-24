-- Enhanced Shreya code: NEW, EMPTY Oracle schema only. Execute manually as owner.
-- Alternative to the historical scripts 01-11; do not combine schema creation paths.
-- Afterwards run 12_admin_reporting_views.sql. No database was changed by code delivery.
CREATE SEQUENCE seq_role_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_user_id START WITH 101 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_account_id START WITH 1000001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_account_number START WITH 500000000001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_beneficiary_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_transaction_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_audit_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE TABLE roles (
 role_id NUMBER(19) PRIMARY KEY, role_name VARCHAR2(30) NOT NULL UNIQUE, description VARCHAR2(255)
);
CREATE TABLE users (
 user_id NUMBER(19) PRIMARY KEY, role_id NUMBER(19) NOT NULL REFERENCES roles(role_id),
 name VARCHAR2(100 CHAR) NOT NULL, email VARCHAR2(150) NOT NULL UNIQUE, phone VARCHAR2(10) NOT NULL UNIQUE,
 password_hash VARCHAR2(255) NOT NULL, status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
 failed_login_attempts NUMBER(3) DEFAULT 0 NOT NULL, locked_until TIMESTAMP, last_login_at TIMESTAMP,
 created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL, updated_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
 CONSTRAINT sp_ck_user_status CHECK (status IN ('ACTIVE','LOCKED','SUSPENDED','INACTIVE'))
);
CREATE TABLE account (
 account_id NUMBER(19) PRIMARY KEY, user_id NUMBER(19) NOT NULL REFERENCES users(user_id),
 account_number VARCHAR2(30) NOT NULL UNIQUE, account_type VARCHAR2(20) DEFAULT 'SAVINGS' NOT NULL,
 balance NUMBER(18,2) NOT NULL, status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
 created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL, updated_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
 CONSTRAINT sp_ck_acct_type CHECK (account_type IN ('SAVINGS','CURRENT')),
 CONSTRAINT sp_ck_acct_status CHECK (status IN ('ACTIVE','BLOCKED','CLOSED'))
);
CREATE TABLE beneficiaries (
 beneficiary_id NUMBER(19) PRIMARY KEY, account_id NUMBER(19) NOT NULL REFERENCES account(account_id),
 beneficiary_name VARCHAR2(100 CHAR) NOT NULL, bank_account_number VARCHAR2(30) NOT NULL,
 ifsc VARCHAR2(20) NOT NULL, status VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL,
 created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
 CONSTRAINT sp_uq_benef_acct UNIQUE (account_id, bank_account_number, ifsc),
 CONSTRAINT sp_ck_benef_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE TABLE transaction_db (
 transaction_id NUMBER(19) PRIMARY KEY, transaction_ref VARCHAR2(50) NOT NULL UNIQUE,
 idempotency_key VARCHAR2(100) NOT NULL UNIQUE,
 cancel_idempotency_key VARCHAR2(100) UNIQUE, verification_idempotency_key VARCHAR2(100) UNIQUE,
 from_account_id NUMBER(19) NOT NULL REFERENCES account(account_id),
 beneficiary_id NUMBER(19) NOT NULL REFERENCES beneficiaries(beneficiary_id),
 amount NUMBER(18,2) NOT NULL, purpose VARCHAR2(255), state VARCHAR2(20) NOT NULL,
 risk_tier VARCHAR2(15) NOT NULL, protection_seconds NUMBER(10) DEFAULT 0 NOT NULL,
 authentication_required CHAR(1) DEFAULT 'N' NOT NULL, risk_reason VARCHAR2(2000) NOT NULL,
 protection_expires_at TIMESTAMP, version NUMBER(19) DEFAULT 0 NOT NULL,
 created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL, authorized_at TIMESTAMP, released_at TIMESTAMP,
 settled_at TIMESTAMP, cancelled_at TIMESTAMP, verified_at TIMESTAMP,
 CONSTRAINT sp_ck_tx_amount CHECK (amount > 0),
 CONSTRAINT sp_ck_tx_state CHECK (state IN ('CREATED','AUTHORIZED','RISK_ASSESSED','PROTECTED','HARD_HOLD','CANCELLED','SETTLED')),
 CONSTRAINT sp_ck_tx_tier CHECK (risk_tier IN ('LOW','MEDIUM','HIGH','VERY_HIGH','HARD_HOLD')),
 CONSTRAINT sp_ck_tx_auth CHECK (authentication_required IN ('Y','N')),
 CONSTRAINT sp_ck_tx_version CHECK (version >= 0),
 CONSTRAINT sp_ck_tx_reason CHECK (LENGTH(TRIM(risk_reason)) > 0),
 CONSTRAINT sp_ck_tx_risk_policy CHECK (
  (risk_tier='LOW' AND protection_seconds=0 AND authentication_required='N') OR
  (risk_tier='MEDIUM' AND protection_seconds=10 AND authentication_required='N') OR
  (risk_tier='HIGH' AND protection_seconds=60 AND authentication_required='N') OR
  (risk_tier IN ('VERY_HIGH','HARD_HOLD') AND protection_seconds=0 AND authentication_required='Y'
    AND protection_expires_at IS NULL))
);
CREATE TABLE audit_log (
 audit_id NUMBER(19) PRIMARY KEY, request_key VARCHAR2(100) UNIQUE, transaction_id NUMBER(19) REFERENCES transaction_db(transaction_id),
 user_id NUMBER(19) REFERENCES users(user_id), action VARCHAR2(50) NOT NULL,
 old_state VARCHAR2(20), new_state VARCHAR2(20), created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL
);
CREATE INDEX idx_sp_expired ON transaction_db(state, protection_expires_at);
CREATE INDEX idx_sp_account_state ON transaction_db(from_account_id, state);
CREATE INDEX idx_sp_tx_history ON transaction_db(from_account_id, settled_at);
INSERT INTO roles(role_id,role_name,description) VALUES(seq_role_id.NEXTVAL,'CUSTOMER','Retail customer');
INSERT INTO roles(role_id,role_name,description) VALUES(seq_role_id.NEXTVAL,'ADMIN','Demo administration');
COMMIT;


