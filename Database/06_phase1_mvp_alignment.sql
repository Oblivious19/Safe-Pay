-- Additive alignment for PHASE1_SPEC.md. Run after 01-03 scripts for Oracle environments.
ALTER TABLE users ADD (full_name VARCHAR2(150), account_locked CHAR(1) DEFAULT 'N' NOT NULL,
                       failed_login_attempts NUMBER(3) DEFAULT 0 NOT NULL);
ALTER TABLE accounts ADD (average_monthly_spend NUMBER(18,2));
ALTER TABLE beneficiaries ADD (ifsc VARCHAR2(20), nickname VARCHAR2(100));
ALTER TABLE transactions ADD (version NUMBER(19) DEFAULT 0 NOT NULL, idempotency_key VARCHAR2(100));
ALTER TABLE transactions MODIFY (amount NUMBER(18,2));
ALTER TABLE accounts MODIFY (balance NUMBER(18,2));
CREATE TABLE risk_signal_logs (
    risk_signal_log_id NUMBER(19) NOT NULL,
    transaction_id NUMBER(19) NOT NULL,
    signal_name VARCHAR2(50) NOT NULL,
    weight NUMBER(5) NOT NULL,
    reason VARCHAR2(500) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_risk_signal_logs PRIMARY KEY (risk_signal_log_id),
    CONSTRAINT fk_risk_signal_logs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);
CREATE SEQUENCE seq_risk_signal_log_id START WITH 100000 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE UNIQUE INDEX uq_transactions_idempotency_key ON transactions (idempotency_key);
CREATE INDEX idx_risk_signal_logs_transaction ON risk_signal_logs (transaction_id);
