CREATE SEQUENCE seq_user_id START WITH 101 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_account_id START WITH 1000001 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_beneficiary_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_transaction_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_approval_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_audit_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


CREATE TABLE users (
    user_id    NUMBER PRIMARY KEY,
    name       VARCHAR2(100) NOT NULL,
    email      VARCHAR2(150) NOT NULL UNIQUE,
    phone      VARCHAR2(10) NOT NULL UNIQUE,
    password   VARCHAR2(255) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE account (
    account_id NUMBER PRIMARY KEY,
    user_id    NUMBER NOT NULL,
    balance    NUMBER(18,2) DEFAULT 5000.00 NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_acct_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT sp_ck_acct_min_bal CHECK (balance >= 5000.00)
);

CREATE TABLE beneficiaries (
    beneficiary_id      NUMBER PRIMARY KEY,
    account_id          NUMBER NOT NULL,
    beneficiary_name    VARCHAR2(100) NOT NULL,
    bank_account_number VARCHAR2(30) NOT NULL,
    ifsc                VARCHAR2(20) NOT NULL,
    status              VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL,
    created_at          TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_benef_acct FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT sp_uq_benef_acct UNIQUE (account_id, bank_account_number, ifsc),
    CONSTRAINT sp_ck_benef_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE transaction_db (
    transaction_id          NUMBER PRIMARY KEY,
    transaction_ref         VARCHAR2(50) NOT NULL UNIQUE,
    idempotency_key         VARCHAR2(100) NOT NULL UNIQUE,
    from_account_id         NUMBER NOT NULL,
    beneficiary_id          NUMBER NOT NULL,
    amount                  NUMBER(18,2) NOT NULL,
    purpose                 VARCHAR2(255),
    state                   VARCHAR2(20) NOT NULL,
    risk_tier               VARCHAR2(15) NOT NULL,
    protection_seconds      NUMBER DEFAULT 0 NOT NULL,
    authentication_required CHAR(1) DEFAULT 'N' NOT NULL,
    risk_reason             VARCHAR2(500) NOT NULL,
    protection_expires_at   TIMESTAMP,
    version                 NUMBER DEFAULT 0 NOT NULL,
    created_at              TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    settled_at              TIMESTAMP,
    cancelled_at            TIMESTAMP,
    CONSTRAINT sp_fk_tx_acct FOREIGN KEY (from_account_id) REFERENCES account(account_id),
    CONSTRAINT sp_fk_tx_benef FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(beneficiary_id),
    CONSTRAINT sp_ck_tx_amount CHECK (amount > 0),
    CONSTRAINT sp_ck_tx_state CHECK (
        state IN ('CREATED', 'AUTHORIZED', 'RISK_ASSESSED', 'PROTECTED', 'HARD_HOLD', 'CANCELLED', 'SETTLED')
    ),
    CONSTRAINT sp_ck_tx_tier CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'HARD_HOLD')),
    CONSTRAINT sp_ck_tx_auth CHECK (authentication_required IN ('Y', 'N')),
    CONSTRAINT sp_ck_tx_version CHECK (version >= 0),
    CONSTRAINT sp_ck_tx_reason CHECK (LENGTH(TRIM(risk_reason)) > 0),
    CONSTRAINT sp_ck_tx_risk_rule CHECK (
        (amount <= 10000.00 AND risk_tier = 'LOW' AND protection_seconds = 0 AND authentication_required = 'N')
        OR (amount > 10000.00 AND amount <= 50000.00 AND risk_tier = 'MEDIUM' AND protection_seconds = 10 AND authentication_required = 'N')
        OR (amount > 50000.00 AND amount <= 100000.00 AND risk_tier = 'HIGH' AND protection_seconds = 60 AND authentication_required = 'N')
        OR (amount > 100000.00 AND risk_tier = 'HARD_HOLD' AND protection_seconds = 0 AND authentication_required = 'Y')
    )
);

-- This is a simple future placeholder. It is not used by the retail Phase 1 flow.
CREATE TABLE approvals (
    approval_id    NUMBER PRIMARY KEY,
    transaction_id NUMBER NOT NULL,
    reviewer_id    NUMBER,
    status         VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    comments       VARCHAR2(500),
    created_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_appr_tx FOREIGN KEY (transaction_id) REFERENCES transaction_db(transaction_id),
    CONSTRAINT sp_fk_appr_user FOREIGN KEY (reviewer_id) REFERENCES users(user_id),
    CONSTRAINT sp_ck_appr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT sp_ck_appr_reviewer CHECK (status = 'PENDING' OR reviewer_id IS NOT NULL)
);

CREATE TABLE audit_log (
    audit_id       NUMBER PRIMARY KEY,
    transaction_id NUMBER,
    user_id        NUMBER,
    action         VARCHAR2(50) NOT NULL,
    old_state      VARCHAR2(20),
    new_state      VARCHAR2(20),
    created_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT sp_fk_audit_tx FOREIGN KEY (transaction_id) REFERENCES transaction_db(transaction_id),
    CONSTRAINT sp_fk_audit_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT sp_ck_audit_action CHECK (LENGTH(TRIM(action)) > 0)
);

CREATE INDEX idx_account_user ON account(user_id);
CREATE INDEX idx_beneficiaries_account ON beneficiaries(account_id);
CREATE INDEX idx_transaction_db_account ON transaction_db(from_account_id);
CREATE INDEX idx_transaction_db_state ON transaction_db(state);
CREATE INDEX idx_approvals_transaction ON approvals(transaction_id);
CREATE INDEX idx_audit_log_transaction ON audit_log(transaction_id);