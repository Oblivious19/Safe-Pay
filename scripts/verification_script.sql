--Run as SAFEPAY_TEAM, after creation of this schema, to verify that the schema is created correctly and all objects are valid.

SELECT USER AS connected_user,
       SYS_CONTEXT('USERENV','CON_NAME') AS container_name
FROM dual;

SELECT object_type, COUNT(*) AS object_count
FROM user_objects
WHERE object_type IN ('TABLE','SEQUENCE','VIEW')
GROUP BY object_type
ORDER BY object_type;

SELECT object_name, object_type
FROM user_objects
WHERE status <> 'VALID';

SELECT constraint_name, table_name, status, validated
FROM user_constraints
WHERE status <> 'ENABLED' OR validated <> 'VALIDATED';

SELECT constraint_name, search_condition_vc
FROM user_constraints
WHERE constraint_name IN (
    'SP_CK_USER_FAILED_LOGINS',
    'SP_CK_AUDIT_ACTION',
    'SP_CK_ACCOUNT_NONNEG_BAL',
    'SP_CK_TX_RISK_POLICY'
)
ORDER BY constraint_name;

SELECT column_name, nullable
FROM user_tab_columns
WHERE table_name='TRANSACTION_DB'
  AND column_name='PAYMENT_CATEGORY';

SELECT u.name, u.email, r.role_name,
       a.account_id, a.account_number, a.balance
FROM users u
JOIN roles r ON r.role_id=u.role_id
LEFT JOIN account a ON a.user_id=u.user_id
ORDER BY u.user_id;

SELECT * FROM vw_sp_tx_report_totals;