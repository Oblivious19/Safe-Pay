-- SafePay Phase 1: Oracle reporting views for the admin dashboard.

CREATE OR REPLACE VIEW vw_transaction_dashboard AS
SELECT
    t.transaction_id,
    t.transaction_reference,
    u.user_name AS customer_name,
    b.beneficiary_name,
    t.amount,
    t.currency,
    t.risk_score,
    t.risk_tier,
    t.policy_action,
    t.status,
    t.protection_seconds,
    t.created_at
FROM transactions t
JOIN accounts a ON a.account_id = t.sender_account_id
JOIN users u ON u.user_id = a.user_id
JOIN beneficiaries b ON b.beneficiary_id = t.beneficiary_id;

CREATE OR REPLACE VIEW vw_risk_summary AS
SELECT
    NVL(risk_tier, 'NOT_ASSESSED') AS risk_tier,
    COUNT(*) AS transaction_count,
    SUM(amount) AS total_amount,
    AVG(amount) AS average_amount,
    SUM(CASE WHEN status = 'PROTECTED' THEN 1 ELSE 0 END) AS protected_count,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
FROM transactions
GROUP BY NVL(risk_tier, 'NOT_ASSESSED');

CREATE OR REPLACE VIEW vw_pending_approvals AS
SELECT
    a.approval_id,
    t.transaction_id,
    t.transaction_reference,
    maker.user_name AS maker_name,
    t.amount,
    t.currency,
    t.risk_score,
    t.risk_tier,
    t.status,
    a.created_at
FROM approvals a
JOIN transactions t ON t.transaction_id = a.transaction_id
JOIN users maker ON maker.user_id = a.maker_id
WHERE a.action = 'PENDING'
  AND t.status = 'PENDING_APPROVAL';

CREATE OR REPLACE VIEW vw_customer_protection_analysis AS
SELECT
    u.user_id,
    u.user_name AS customer_name,
    COUNT(t.transaction_id) AS total_transactions,
    SUM(CASE WHEN t.protection_required = 'Y' THEN 1 ELSE 0 END) AS protected_count,
    SUM(CASE WHEN t.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
    SUM(CASE WHEN t.status = 'SETTLED' THEN 1 ELSE 0 END) AS settled_count,
    SUM(CASE WHEN t.risk_tier IN ('HIGH', 'VERY_HIGH') THEN 1 ELSE 0 END) AS high_risk_count,
    NVL(SUM(t.amount), 0) AS total_amount
FROM users u
LEFT JOIN accounts a ON a.user_id = u.user_id
LEFT JOIN transactions t ON t.sender_account_id = a.account_id
GROUP BY u.user_id, u.user_name;
