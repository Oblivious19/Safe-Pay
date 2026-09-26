-- NEW, EMPTY SAFEPAY_TEAM schema only.
-- Oracle DDL commits implicitly. Stop on the first error.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
SET DEFINE OFF

-- A. Confirm the destination.
DECLARE
    n NUMBER;
BEGIN
    IF USER <> 'SAFEPAY_TEAM'
       OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'SAFEPAY_TEAM'
       OR SYS_CONTEXT('USERENV','CON_NAME') <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Use SAFEPAY_TEAM in FREEPDB1.');
    END IF;

    SELECT COUNT(*) INTO n FROM user_objects;
    IF n <> 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Schema is not empty. Stop and inspect.');
    END IF;
END;
/

-- B. Sequences.
CREATE SEQUENCE seq_role_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_user_id START WITH 2026001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_account_id START WITH 1000001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_account_number START WITH 987600000001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_beneficiary_id START WITH 101 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_transaction_id START WITH 202600001 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_audit_id START WITH 1 INCREMENT BY 1 NOCACHE;

-- C. Roles and users.
CREATE TABLE roles (
    role_id NUMBER(19) PRIMARY KEY,
    role_name VARCHAR2(30) NOT NULL UNIQUE,
    description VARCHAR2(255)
);

CREATE TABLE users (
    user_id NUMBER(19) PRIMARY KEY,
    role_id NUMBER(19) NOT NULL,
    name VARCHAR2(100 CHAR) NOT NULL,
    email VARCHAR2(150) NOT NULL UNIQUE,
    phone VARCHAR2(10) NOT NULL UNIQUE,
    password_hash VARCHAR2(255) NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    failed_login_attempts NUMBER(3) DEFAULT 0 NOT NULL,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_user_role
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    CONSTRAINT sp_ck_user_status
        CHECK (status IN ('ACTIVE','LOCKED','SUSPENDED','INACTIVE')),
    CONSTRAINT sp_ck_user_failed_logins
        CHECK (failed_login_attempts >= 0)
);

-- D. Accounts.
CREATE TABLE account (
    account_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    account_number VARCHAR2(30) NOT NULL,
    account_type VARCHAR2(20) DEFAULT 'SAVINGS' NOT NULL,
    balance NUMBER(18,2) NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,

    CONSTRAINT sp_fk_acct_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT sp_uq_account_number
        UNIQUE (account_number),
    CONSTRAINT sp_ck_account_type
        CHECK (account_type IN ('SAVINGS','CURRENT')),
    CONSTRAINT sp_ck_account_status
        CHECK (status IN ('ACTIVE','BLOCKED','CLOSED')),
    CONSTRAINT sp_ck_account_nonneg_bal
        CHECK (balance >= 0)
);

-- E. Beneficiaries belong to the selected SOURCE account.
CREATE TABLE beneficiaries (
    beneficiary_id NUMBER(19) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    beneficiary_name VARCHAR2(100 CHAR) NOT NULL,
    bank_account_number VARCHAR2(30) NOT NULL,
    ifsc VARCHAR2(20) NOT NULL,
    status VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_benef_acct
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT sp_uq_benef_acct
        UNIQUE (account_id, bank_account_number, ifsc),
    CONSTRAINT sp_ck_benef_status
        CHECK (status IN ('ACTIVE','INACTIVE'))
);

-- F. Transactions.
CREATE TABLE transaction_db (
    transaction_id NUMBER(19) PRIMARY KEY,
    transaction_ref VARCHAR2(50) NOT NULL UNIQUE,
    idempotency_key VARCHAR2(100) NOT NULL,
    cancel_idempotency_key VARCHAR2(100),
    verification_idempotency_key VARCHAR2(100),

    from_account_id NUMBER(19) NOT NULL,
    to_account_id NUMBER(19),
    beneficiary_id NUMBER(19) NOT NULL,

    amount NUMBER(18,2) NOT NULL,
    purpose VARCHAR2(255),
    payment_category VARCHAR2(20 CHAR),
    state VARCHAR2(20) NOT NULL,
    risk_tier VARCHAR2(15) NOT NULL,
    protection_seconds NUMBER(10) DEFAULT 0 NOT NULL,
    authentication_required CHAR(1) DEFAULT 'N' NOT NULL,
    risk_reason VARCHAR2(2000) NOT NULL,
    protection_expires_at TIMESTAMP,
    version NUMBER(19) DEFAULT 0 NOT NULL,

    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,
    authorized_at TIMESTAMP,
    released_at TIMESTAMP,
    settled_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    verified_at TIMESTAMP,

    CONSTRAINT sp_uq_tx_initiate_key
        UNIQUE (idempotency_key),
    CONSTRAINT sp_uq_tx_cancel_key
        UNIQUE (cancel_idempotency_key),
    CONSTRAINT sp_uq_tx_verify_key
        UNIQUE (verification_idempotency_key),
    CONSTRAINT sp_fk_tx_acct
        FOREIGN KEY (from_account_id) REFERENCES account(account_id),
    CONSTRAINT sp_fk_tx_benef
        FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(beneficiary_id),

    CONSTRAINT sp_fk_tx_receiver
        FOREIGN KEY (to_account_id) REFERENCES account(account_id),
    CONSTRAINT sp_ck_tx_amount CHECK (amount > 0),
    CONSTRAINT sp_ck_tx_state CHECK (
        state IN ('CREATED','AUTHORIZED','RISK_ASSESSED',
                  'PROTECTED','HARD_HOLD','CANCELLED','SETTLED')
    ),
    CONSTRAINT sp_ck_tx_tier CHECK (
        risk_tier IN ('LOW','MEDIUM','HIGH','VERY_HIGH','HARD_HOLD')
    ),
    CONSTRAINT sp_ck_tx_auth CHECK (authentication_required IN ('Y','N')),
    CONSTRAINT sp_ck_tx_version CHECK (version >= 0),
    CONSTRAINT sp_ck_tx_reason CHECK (LENGTH(TRIM(risk_reason)) > 0),

    -- Strict amount bands and current durations.
    -- HARD_HOLD tier is retained only as the existing legacy alias.
    CONSTRAINT sp_ck_tx_risk_policy CHECK (
        (amount > 0 AND amount <= 10000
            AND risk_tier='LOW'
            AND protection_seconds=0
            AND authentication_required='N')
        OR
        (amount > 10000 AND amount <= 50000
            AND risk_tier='MEDIUM'
            AND protection_seconds=10
            AND authentication_required='N')
        OR
        (amount > 50000 AND amount <= 100000
            AND risk_tier='HIGH'
            AND protection_seconds=30
            AND authentication_required='N')
        OR
        (amount > 100000
            AND risk_tier IN ('VERY_HIGH','HARD_HOLD')
            AND protection_seconds=0
            AND authentication_required='Y'
            AND protection_expires_at IS NULL)
    ),

    -- Nullable for compatibility. Java requires it for NEW payments > 1 lakh.
    CONSTRAINT sp_ck_tx_cat_values CHECK (
        payment_category IN ('MEDICAL','LOAN','FRIENDS_FAMILY',
                             'INVESTMENTS','OTHERS')
    ),
    CONSTRAINT sp_ck_tx_cat_amount CHECK (
        payment_category IS NULL OR amount > 100000
    ),
    CONSTRAINT sp_ck_tx_cat_purpose CHECK (
        payment_category IS NULL OR payment_category <> 'OTHERS'
        OR (
            purpose IS NOT NULL
            AND LENGTH(purpose) BETWEEN 1 AND 140
            AND purpose=TRIM(purpose)
            AND REGEXP_LIKE(purpose, '[^[:space:]]')
        )
    )
);

-- G. Audit.
CREATE TABLE audit_log (
    audit_id NUMBER(19) PRIMARY KEY,
    request_key VARCHAR2(100) UNIQUE,
    transaction_id NUMBER(19),
    user_id NUMBER(19),
    action VARCHAR2(50) NOT NULL,
    old_state VARCHAR2(20),
    new_state VARCHAR2(20),
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP NOT NULL,

    CONSTRAINT sp_fk_audit_tx
        FOREIGN KEY (transaction_id)
        REFERENCES transaction_db(transaction_id),
    CONSTRAINT sp_fk_audit_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),
    CONSTRAINT sp_ck_audit_action
        CHECK (REGEXP_LIKE(action, '[^[:space:]]'))
);

-- H. Operational indexes.
CREATE INDEX idx_sp_expired
    ON transaction_db(state, protection_expires_at);
CREATE INDEX idx_sp_account_state
    ON transaction_db(from_account_id, state);
CREATE INDEX idx_sp_tx_history
    ON transaction_db(from_account_id, settled_at);
CREATE INDEX sp_ix_tx_receiver
    ON transaction_db(to_account_id, state);
CREATE INDEX idx_account_user
    ON account(user_id);
CREATE INDEX idx_audit_log_transaction
    ON audit_log(transaction_id);

-- I. Reporting.
-- Admin declines persist as CANCELLED, with ADMIN_DECLINED_CANCELLED audit.
-- Report cancellation and admin rejection as mutually exclusive categories.
CREATE VIEW vw_sp_tx_report_totals AS
SELECT
    COUNT(*) AS total_transactions,
    COUNT(CASE WHEN t.state='SETTLED' THEN 1 END) AS settled_transactions,
    COUNT(CASE WHEN t.state='PROTECTED' THEN 1 END) AS protected_transactions,
    COUNT(CASE WHEN t.state='CANCELLED'
               AND d.transaction_id IS NULL THEN 1 END) AS cancelled_transactions,
    COUNT(CASE WHEN t.state='CANCELLED'
               AND d.transaction_id IS NOT NULL THEN 1 END) AS rejected_transactions,
    COUNT(CASE WHEN t.state='HARD_HOLD' THEN 1 END) AS hard_holds,
    COUNT(CASE WHEN t.risk_tier IN ('HIGH','VERY_HIGH','HARD_HOLD')
               THEN 1 END) AS high_risk_transactions,
    NVL(SUM(t.amount),0) AS total_amount,
    NVL(SUM(CASE WHEN t.state='SETTLED' THEN t.amount ELSE 0 END),0)
        AS settled_amount
FROM transaction_db t
LEFT JOIN (
    SELECT DISTINCT transaction_id
    FROM audit_log
    WHERE action='ADMIN_DECLINED_CANCELLED'
) d ON d.transaction_id=t.transaction_id
WITH READ ONLY;

CREATE VIEW vw_sp_tx_report_daily AS
SELECT
    TRUNC(t.created_at) AS report_day,
    COUNT(*) AS total_transactions,
    COUNT(CASE WHEN t.state='SETTLED' THEN 1 END) AS settled_transactions,
    COUNT(CASE WHEN t.state='PROTECTED' THEN 1 END) AS protected_transactions,
    COUNT(CASE WHEN t.state='CANCELLED'
               AND d.transaction_id IS NULL THEN 1 END) AS cancelled_transactions,
    COUNT(CASE WHEN t.state='CANCELLED'
               AND d.transaction_id IS NOT NULL THEN 1 END) AS rejected_transactions,
    COUNT(CASE WHEN t.state='HARD_HOLD' THEN 1 END) AS hard_holds,
    COUNT(CASE WHEN t.risk_tier IN ('HIGH','VERY_HIGH','HARD_HOLD')
               THEN 1 END) AS high_risk_transactions,
    NVL(SUM(t.amount),0) AS total_amount,
    NVL(SUM(CASE WHEN t.state='SETTLED' THEN t.amount ELSE 0 END),0)
        AS settled_amount
FROM transaction_db t
LEFT JOIN (
    SELECT DISTINCT transaction_id
    FROM audit_log
    WHERE action='ADMIN_DECLINED_CANCELLED'
) d ON d.transaction_id=t.transaction_id
GROUP BY TRUNC(t.created_at)
WITH READ ONLY;

-- J. Required reference data.
INSERT INTO roles(role_id,role_name,description)
VALUES(seq_role_id.NEXTVAL,'CUSTOMER','Retail customer');

INSERT INTO roles(role_id,role_name,description)
VALUES(seq_role_id.NEXTVAL,'ADMIN','Demo administration');

COMMIT;

PROMPT SAFEPAY_TEAM_STRUCTURE_COMPLETE