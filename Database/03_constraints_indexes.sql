-- SafePay Phase 1: keys, validation constraints, and query indexes.

ALTER TABLE roles ADD CONSTRAINT pk_roles PRIMARY KEY (role_id);
ALTER TABLE roles ADD CONSTRAINT uq_roles_name UNIQUE (role_name);
ALTER TABLE roles ADD CONSTRAINT ck_roles_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE banks ADD CONSTRAINT pk_banks PRIMARY KEY (bank_id);
ALTER TABLE banks ADD CONSTRAINT uq_banks_code UNIQUE (bank_code);
ALTER TABLE banks ADD CONSTRAINT ck_banks_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE users ADD CONSTRAINT pk_users PRIMARY KEY (user_id);
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'));
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (role_id);

ALTER TABLE accounts ADD CONSTRAINT pk_accounts PRIMARY KEY (account_id);
ALTER TABLE accounts ADD CONSTRAINT uq_accounts_number UNIQUE (account_number);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT'));
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_balance CHECK (balance >= 0);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_currency CHECK (currency = 'INR');
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'FROZEN'));
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (user_id);
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_bank FOREIGN KEY (bank_id) REFERENCES banks (bank_id);

ALTER TABLE beneficiaries ADD CONSTRAINT pk_beneficiaries PRIMARY KEY (beneficiary_id);
ALTER TABLE beneficiaries ADD CONSTRAINT uq_beneficiary_owner UNIQUE (user_id, bank_id, account_number);
ALTER TABLE beneficiaries ADD CONSTRAINT ck_beneficiary_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE beneficiaries ADD CONSTRAINT fk_beneficiaries_user FOREIGN KEY (user_id) REFERENCES users (user_id);
ALTER TABLE beneficiaries ADD CONSTRAINT fk_beneficiaries_bank FOREIGN KEY (bank_id) REFERENCES banks (bank_id);

ALTER TABLE risk_factors ADD CONSTRAINT pk_risk_factors PRIMARY KEY (risk_factor_id);
ALTER TABLE risk_factors ADD CONSTRAINT uq_risk_factor_code UNIQUE (factor_code);
ALTER TABLE risk_factors ADD CONSTRAINT ck_risk_factor_weight CHECK (weight >= 0);
ALTER TABLE risk_factors ADD CONSTRAINT ck_risk_factor_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE protection_rules ADD CONSTRAINT pk_protection_rules PRIMARY KEY (rule_id);
ALTER TABLE protection_rules ADD CONSTRAINT uq_rule_priority UNIQUE (risk_tier, priority);
ALTER TABLE protection_rules ADD CONSTRAINT ck_rule_tier CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'));
ALTER TABLE protection_rules ADD CONSTRAINT ck_rule_range CHECK (max_score IS NULL OR max_score >= min_score);
ALTER TABLE protection_rules ADD CONSTRAINT ck_rule_action CHECK (policy_action IN ('INSTANT_SETTLEMENT', 'SHORT_PROTECTION', 'PROTECTION_WINDOW', 'HARD_HOLD'));
ALTER TABLE protection_rules ADD CONSTRAINT ck_rule_seconds CHECK (protection_seconds >= 0);
ALTER TABLE protection_rules ADD CONSTRAINT ck_rule_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE user_safety_settings ADD CONSTRAINT pk_user_safety_settings PRIMARY KEY (setting_id);
ALTER TABLE user_safety_settings ADD CONSTRAINT uq_safety_settings_user UNIQUE (user_id);
ALTER TABLE user_safety_settings ADD CONSTRAINT ck_settings_amount CHECK (amount_threshold IS NULL OR amount_threshold >= 0);
ALTER TABLE user_safety_settings ADD CONSTRAINT ck_settings_seconds CHECK (protection_seconds IS NULL OR protection_seconds >= 0);
ALTER TABLE user_safety_settings ADD CONSTRAINT ck_settings_new_beneficiary CHECK (new_beneficiary_protection IN ('Y', 'N'));
ALTER TABLE user_safety_settings ADD CONSTRAINT ck_settings_additional CHECK (additional_verification_amount IS NULL OR additional_verification_amount >= 0);
ALTER TABLE user_safety_settings ADD CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id);

ALTER TABLE transactions ADD CONSTRAINT pk_transactions PRIMARY KEY (transaction_id);
ALTER TABLE transactions ADD CONSTRAINT uq_transaction_reference UNIQUE (transaction_reference);
ALTER TABLE transactions ADD CONSTRAINT uq_request_reference UNIQUE (request_reference);
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_amount CHECK (amount > 0);
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_currency CHECK (currency = 'INR');
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_status CHECK (status IN ('CREATED', 'AUTHORIZED', 'RISK_ASSESSED', 'PROTECTED', 'HARD_HOLD', 'PENDING_APPROVAL', 'CANCELLED', 'SETTLED', 'REJECTED', 'DISPUTED', 'FAILED'));
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_error_category CHECK (error_category IS NULL OR error_category IN ('BUSINESS', 'TECHNICAL', 'POLICY'));
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_risk_score CHECK (risk_score IS NULL OR risk_score >= 0);
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_risk_tier CHECK (risk_tier IS NULL OR risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'));
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_policy CHECK (policy_action IS NULL OR policy_action IN ('INSTANT_SETTLEMENT', 'SHORT_PROTECTION', 'PROTECTION_WINDOW', 'HARD_HOLD'));
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_protection CHECK (protection_required IN ('Y', 'N'));
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_prot_seconds CHECK (protection_seconds >= 0);
ALTER TABLE transactions ADD CONSTRAINT ck_transaction_prot_window CHECK (protection_end IS NULL OR protection_start IS NULL OR protection_end > protection_start);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_account FOREIGN KEY (sender_account_id) REFERENCES accounts (account_id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (beneficiary_id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_creator FOREIGN KEY (created_by) REFERENCES users (user_id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_rule FOREIGN KEY (applied_rule_id) REFERENCES protection_rules (rule_id);

ALTER TABLE transaction_context ADD CONSTRAINT pk_transaction_context PRIMARY KEY (context_id);
ALTER TABLE transaction_context ADD CONSTRAINT uq_context_transaction UNIQUE (transaction_id);
ALTER TABLE transaction_context ADD CONSTRAINT ck_context_device_known CHECK (device_known IN ('Y', 'N'));
ALTER TABLE transaction_context ADD CONSTRAINT ck_context_location_known CHECK (location_known IN ('Y', 'N'));
ALTER TABLE transaction_context ADD CONSTRAINT fk_context_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);

ALTER TABLE transaction_risk_factors ADD CONSTRAINT pk_transaction_risk_factors PRIMARY KEY (transaction_id, risk_factor_id);
ALTER TABLE transaction_risk_factors ADD CONSTRAINT ck_tx_risk_score_added CHECK (score_added >= 0);
ALTER TABLE transaction_risk_factors ADD CONSTRAINT fk_tx_risk_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);
ALTER TABLE transaction_risk_factors ADD CONSTRAINT fk_tx_risk_factor FOREIGN KEY (risk_factor_id) REFERENCES risk_factors (risk_factor_id);

ALTER TABLE approvals ADD CONSTRAINT pk_approvals PRIMARY KEY (approval_id);
ALTER TABLE approvals ADD CONSTRAINT uq_approvals_transaction UNIQUE (transaction_id);
ALTER TABLE approvals ADD CONSTRAINT ck_approval_action CHECK (action IN ('PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE approvals ADD CONSTRAINT ck_approval_roles CHECK (checker_id IS NULL OR checker_id <> maker_id);
ALTER TABLE approvals ADD CONSTRAINT ck_approval_checker CHECK (action = 'PENDING' OR checker_id IS NOT NULL);
ALTER TABLE approvals ADD CONSTRAINT ck_approval_rejection CHECK (action <> 'REJECTED' OR comments IS NOT NULL);
ALTER TABLE approvals ADD CONSTRAINT fk_approvals_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);
ALTER TABLE approvals ADD CONSTRAINT fk_approvals_maker FOREIGN KEY (maker_id) REFERENCES users (user_id);
ALTER TABLE approvals ADD CONSTRAINT fk_approvals_checker FOREIGN KEY (checker_id) REFERENCES users (user_id);

ALTER TABLE disputes ADD CONSTRAINT pk_disputes PRIMARY KEY (dispute_id);
ALTER TABLE disputes ADD CONSTRAINT uq_disputes_transaction UNIQUE (transaction_id);
ALTER TABLE disputes ADD CONSTRAINT ck_dispute_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED'));
ALTER TABLE disputes ADD CONSTRAINT fk_disputes_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);
ALTER TABLE disputes ADD CONSTRAINT fk_disputes_user FOREIGN KEY (user_id) REFERENCES users (user_id);

ALTER TABLE audit_log ADD CONSTRAINT pk_audit_log PRIMARY KEY (audit_id);
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM'));
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (user_id);
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);

CREATE INDEX idx_accounts_user_status ON accounts (user_id, status);
CREATE INDEX idx_beneficiaries_user_status ON beneficiaries (user_id, status);
CREATE INDEX idx_transactions_account_created ON transactions (sender_account_id, created_at);
CREATE INDEX idx_transactions_beneficiary ON transactions (beneficiary_id, created_at);
CREATE INDEX idx_transactions_creator_created ON transactions (created_by, created_at);
CREATE INDEX idx_transactions_status_created ON transactions (status, created_at);
CREATE INDEX idx_transactions_risk_status ON transactions (risk_tier, status, created_at);
CREATE INDEX idx_transactions_protection_end ON transactions (protection_end);
CREATE INDEX idx_tx_risk_factor ON transaction_risk_factors (risk_factor_id);
CREATE INDEX idx_approvals_action_created ON approvals (action, created_at);
CREATE INDEX idx_disputes_status_created ON disputes (status, created_at);
CREATE INDEX idx_audit_transaction_event ON audit_log (transaction_id, event_timestamp);
CREATE INDEX idx_audit_user_event ON audit_log (user_id, event_timestamp);
