-- SafePay Phase 1: normalized Oracle tables.
-- Constraints and indexes are defined in 03_constraints_indexes.sql.

CREATE TABLE roles (
    role_id NUMBER(19) NOT NULL,
    role_name VARCHAR2(30) NOT NULL,
    description VARCHAR2(255),
    active_flag CHAR(1) DEFAULT 'Y' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE banks (
    bank_id NUMBER(19) NOT NULL,
    bank_name VARCHAR2(100) NOT NULL,
    bank_code VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE users (
    user_id NUMBER(19) NOT NULL,
    role_id NUMBER(19) NOT NULL,
    user_name VARCHAR2(100) NOT NULL,
    email VARCHAR2(254) NOT NULL,
    phone VARCHAR2(20),
    password_hash VARCHAR2(100) NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE accounts (
    account_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    bank_id NUMBER(19) NOT NULL,
    account_number VARCHAR2(30) NOT NULL,
    account_type VARCHAR2(20) NOT NULL,
    balance NUMBER(15,2) DEFAULT 0 NOT NULL,
    currency CHAR(3) DEFAULT 'INR' NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE beneficiaries (
    beneficiary_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    bank_id NUMBER(19) NOT NULL,
    beneficiary_name VARCHAR2(100) NOT NULL,
    account_number VARCHAR2(30) NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    added_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE risk_factors (
    risk_factor_id NUMBER(19) NOT NULL,
    factor_code VARCHAR2(50) NOT NULL,
    factor_name VARCHAR2(100) NOT NULL,
    description VARCHAR2(500),
    weight NUMBER(5) NOT NULL,
    active_flag CHAR(1) DEFAULT 'Y' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE protection_rules (
    rule_id NUMBER(19) NOT NULL,
    risk_tier VARCHAR2(15) NOT NULL,
    min_score NUMBER(5) NOT NULL,
    max_score NUMBER(5),
    policy_action VARCHAR2(30) NOT NULL,
    protection_seconds NUMBER(6) DEFAULT 0 NOT NULL,
    priority NUMBER(3) DEFAULT 1 NOT NULL,
    active_flag CHAR(1) DEFAULT 'Y' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE user_safety_settings (
    setting_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    amount_threshold NUMBER(15,2),
    protection_seconds NUMBER(6),
    new_beneficiary_protection CHAR(1) DEFAULT 'Y' NOT NULL,
    additional_verification_amount NUMBER(15,2),
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE transactions (
    transaction_id NUMBER(19) NOT NULL,
    transaction_reference VARCHAR2(50) NOT NULL,
    request_reference VARCHAR2(60) NOT NULL,
    sender_account_id NUMBER(19) NOT NULL,
    beneficiary_id NUMBER(19) NOT NULL,
    amount NUMBER(15,2) NOT NULL,
    currency CHAR(3) DEFAULT 'INR' NOT NULL,
    transaction_type VARCHAR2(30) DEFAULT 'TRANSFER' NOT NULL,
    channel VARCHAR2(30) DEFAULT 'WEB' NOT NULL,
    purpose VARCHAR2(255),
    status VARCHAR2(30) NOT NULL,
    status_reason VARCHAR2(255),
    error_category VARCHAR2(20),
    error_code VARCHAR2(50),
    risk_score NUMBER(5),
    risk_tier VARCHAR2(15),
    policy_action VARCHAR2(30),
    applied_rule_id NUMBER(19),
    protection_required CHAR(1) DEFAULT 'N' NOT NULL,
    protection_seconds NUMBER(6) DEFAULT 0 NOT NULL,
    created_by NUMBER(19) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    authorized_at TIMESTAMP(6),
    risk_assessed_at TIMESTAMP(6),
    protection_start TIMESTAMP(6),
    protection_end TIMESTAMP(6),
    settled_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    rejected_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE transaction_context (
    context_id NUMBER(19) NOT NULL,
    transaction_id NUMBER(19) NOT NULL,
    device_id VARCHAR2(100),
    device_known CHAR(1) DEFAULT 'N' NOT NULL,
    ip_address VARCHAR2(45),
    city VARCHAR2(100),
    country VARCHAR2(100),
    location_known CHAR(1) DEFAULT 'N' NOT NULL,
    channel VARCHAR2(30),
    session_id VARCHAR2(100),
    captured_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE transaction_risk_factors (
    transaction_id NUMBER(19) NOT NULL,
    risk_factor_id NUMBER(19) NOT NULL,
    score_added NUMBER(5) NOT NULL,
    detected_value VARCHAR2(500),
    evaluated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE approvals (
    approval_id NUMBER(19) NOT NULL,
    transaction_id NUMBER(19) NOT NULL,
    maker_id NUMBER(19) NOT NULL,
    checker_id NUMBER(19),
    action VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    comments VARCHAR2(1000),
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    action_at TIMESTAMP(6)
);

CREATE TABLE disputes (
    dispute_id NUMBER(19) NOT NULL,
    transaction_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    dispute_type VARCHAR2(50) NOT NULL,
    description VARCHAR2(1000) NOT NULL,
    status VARCHAR2(30) DEFAULT 'OPEN' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP(6),
    resolution_date TIMESTAMP(6),
    resolution_remarks VARCHAR2(1000)
);

CREATE TABLE audit_log (
    audit_id NUMBER(19) NOT NULL,
    user_id NUMBER(19),
    transaction_id NUMBER(19),
    actor_type VARCHAR2(10) DEFAULT 'USER' NOT NULL,
    action VARCHAR2(50) NOT NULL,
    old_status VARCHAR2(30),
    new_status VARCHAR2(30),
    error_code VARCHAR2(50),
    correlation_reference VARCHAR2(60),
    event_timestamp TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    remarks VARCHAR2(1000)
);
