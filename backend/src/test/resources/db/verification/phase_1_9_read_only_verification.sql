--------------------------------------------------------------------------------
-- SafePay Phase 1.9 read-only verification package
-- Run as SAFEPAY_OWNER only after Flyway has applied V9, V10, V11 and R__.
-- Every violation_count query must return 0.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Flyway history
--------------------------------------------------------------------------------

SELECT
    "installed_rank",
    "version",
    "description",
    "type",
    "success"
FROM "flyway_schema_history"
WHERE "version" IN ('9', '10', '11')
   OR "description" = 'safepay app grants'
ORDER BY "installed_rank";

--------------------------------------------------------------------------------
-- 2. Canonical objects and deprecated-name absence
--------------------------------------------------------------------------------

SELECT
    object_type,
    object_name,
    status
FROM user_objects
WHERE object_name IN (
    'RISK_REVIEW',
    'SEQ_RISK_REVIEW_ID',
    'TRG_RISK_REVIEW_FINAL_LOCK',
    'PR_VALIDATE_RISK_POLICY_BANDS',
    'TRG_RISK_POLICY_GUARD',
    'TRG_RISK_BAND_DRAFT_ONLY',
    'TRG_PROTECTION_POLICY_LOCK',
    'VW_TRANSACTION_DASHBOARD',
    'VW_RISK_SUMMARY',
    'VW_PENDING_APPROVALS'
)
ORDER BY object_type, object_name;

SELECT
    object_type,
    object_name
FROM user_objects
WHERE object_name IN (
    'MAKER_CHECKER_APPROVAL',
    'SEQ_MAKER_CHECKER_APPROVAL_ID',
    'TRG_MAKER_CHECKER_FINAL_LOCK'
);

SELECT
    object_type,
    object_name,
    status
FROM user_objects
WHERE status <> 'VALID'
  AND object_name NOT LIKE 'BIN$%'
ORDER BY object_type, object_name;

--------------------------------------------------------------------------------
-- 3. Policy-version datatype and required constraints
--------------------------------------------------------------------------------

SELECT
    table_name,
    column_name,
    data_type,
    char_length,
    char_used,
    nullable
FROM user_tab_columns
WHERE table_name = 'PAYMENT_TRANSACTION'
  AND column_name = 'POLICY_VERSION';

WITH expected_constraint (constraint_name) AS (
    SELECT 'UK_ACCOUNT_ID_OWNER' FROM dual UNION ALL
    SELECT 'UK_BENEFICIARY_ID_OWNER' FROM dual UNION ALL
    SELECT 'UK_PAYMENT_TX_ID_CUSTOMER' FROM dual UNION ALL
    SELECT 'UK_PAYMENT_TX_ID_RISK_BAND' FROM dual UNION ALL
    SELECT 'FK_PAYMENT_TX_ACCOUNT_OWNER' FROM dual UNION ALL
    SELECT 'FK_PAYMENT_TX_BEN_OWNER' FROM dual UNION ALL
    SELECT 'FK_PAYMENT_OTP_TX_CUSTOMER' FROM dual UNION ALL
    SELECT 'FK_RISK_REVIEW_TX_CUSTOMER' FROM dual UNION ALL
    SELECT 'FK_TX_RISK_FACTOR_SNAPSHOT' FROM dual UNION ALL
    SELECT 'UK_RISK_POLICY_ID_VERSION' FROM dual UNION ALL
    SELECT 'UK_RISK_BAND_SNAPSHOT' FROM dual UNION ALL
    SELECT 'FK_PAYMENT_TX_POLICY_VER' FROM dual UNION ALL
    SELECT 'FK_PAYMENT_TX_RISK_SNAPSHOT' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_RISK_SNAPSHOT' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_V1_RISK_ACTION' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_RISK_ROUTE' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_RESERVATION_TIME' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_RESERVE_STATE' FROM dual UNION ALL
    SELECT 'CK_PAYMENT_TX_DEADLINE_ORDER' FROM dual
)
SELECT
    expected.constraint_name,
    NVL(actual.status, 'MISSING') AS status,
    NVL(actual.validated, 'MISSING') AS validated
FROM expected_constraint expected
LEFT JOIN user_constraints actual
  ON actual.constraint_name = expected.constraint_name
ORDER BY expected.constraint_name;

WITH expected_index (index_name) AS (
    SELECT 'IDX_PAYMENT_TX_ACCOUNT_OWNER' FROM dual UNION ALL
    SELECT 'IDX_PAYMENT_TX_BEN_OWNER' FROM dual UNION ALL
    SELECT 'IDX_PAYMENT_OTP_TX_CUSTOMER' FROM dual UNION ALL
    SELECT 'IDX_RISK_REVIEW_TX_CUSTOMER' FROM dual UNION ALL
    SELECT 'IDX_TX_RISK_FACTOR_SNAPSHOT' FROM dual UNION ALL
    SELECT 'IDX_PAYMENT_TX_POLICY_VER' FROM dual UNION ALL
    SELECT 'IDX_PAYMENT_TX_RISK_SNAPSHOT' FROM dual UNION ALL
    SELECT 'UK_RISK_REVIEW_OPEN_TX' FROM dual
)
SELECT
    expected.index_name,
    NVL(actual.status, 'MISSING') AS status
FROM expected_index expected
LEFT JOIN user_indexes actual
  ON actual.index_name = expected.index_name
ORDER BY expected.index_name;

SELECT
    trigger_name,
    status
FROM user_triggers
WHERE trigger_name IN (
    'TRG_RISK_REVIEW_FINAL_LOCK',
    'TRG_RISK_POLICY_GUARD',
    'TRG_RISK_BAND_DRAFT_ONLY',
    'TRG_PROTECTION_POLICY_LOCK'
)
ORDER BY trigger_name;

--------------------------------------------------------------------------------
-- 4. Approved review vocabulary and reference-data cardinality
--------------------------------------------------------------------------------

SELECT
    'INVALID_RISK_REVIEW_STATUS' AS check_name,
    COUNT(*) AS violation_count
FROM RISK_REVIEW
WHERE status NOT IN (
    'PENDING',
    'APPROVED',
    'REJECTED',
    'REVERIFICATION_REQUESTED',
    'CANCELLED'
);

SELECT
    role_code,
    COUNT(*) AS row_count
FROM APP_ROLE
WHERE role_code IN (
    'CUSTOMER',
    'RISK_OFFICER',
    'SYSTEM_ADMIN',
    'AUDITOR'
)
GROUP BY role_code
ORDER BY role_code;

SELECT
    policy_version,
    algorithm_type,
    currency_code,
    status,
    effective_from,
    effective_to
FROM RISK_POLICY
WHERE policy_version = 'AMOUNT_ONLY_V1';

SELECT
    band.display_order,
    band.band_code,
    band.risk_tier,
    band.minimum_amount,
    band.maximum_amount,
    protection.protection_code,
    protection.release_mode,
    protection.protection_seconds,
    protection.customer_can_cancel,
    protection.auto_release,
    protection.otp_required,
    protection.risk_review_required
FROM RISK_POLICY_BAND band
JOIN RISK_POLICY policy
  ON policy.risk_policy_id = band.risk_policy_id
JOIN PROTECTION_POLICY protection
  ON protection.protection_policy_id =
     band.protection_policy_id
WHERE policy.policy_version = 'AMOUNT_ONLY_V1'
ORDER BY band.display_order;

SELECT
    'ACTIVE_POLICY_COUNT' AS check_name,
    COUNT(*) AS actual_count
FROM RISK_POLICY
WHERE status = 'ACTIVE';

SELECT
    'AMOUNT_ONLY_V1_BAND_COUNT' AS check_name,
    COUNT(*) AS actual_count
FROM RISK_POLICY_BAND band
JOIN RISK_POLICY policy
  ON policy.risk_policy_id = band.risk_policy_id
WHERE policy.policy_version = 'AMOUNT_ONLY_V1';

--------------------------------------------------------------------------------
-- 5. Band coverage and action consistency
--------------------------------------------------------------------------------

WITH ordered_band AS (
    SELECT
        band.display_order,
        band.risk_tier,
        band.minimum_amount,
        band.maximum_amount,
        LAG(band.maximum_amount) OVER (
            ORDER BY band.display_order
        ) AS previous_maximum
    FROM RISK_POLICY_BAND band
    JOIN RISK_POLICY policy
      ON policy.risk_policy_id = band.risk_policy_id
    WHERE policy.policy_version = 'AMOUNT_ONLY_V1'
)
SELECT
    'POLICY_BAND_COVERAGE' AS check_name,
    COUNT(*) AS violation_count
FROM ordered_band
WHERE (
        display_order = 1
        AND minimum_amount <> 1.00
      )
   OR (
        display_order > 1
        AND (
            previous_maximum IS NULL
            OR minimum_amount <> previous_maximum + 0.01
        )
      )
   OR (
        display_order < 4
        AND maximum_amount IS NULL
      )
   OR (
        display_order = 4
        AND maximum_amount IS NOT NULL
      );

SELECT
    'POLICY_PROTECTION_MAPPING' AS check_name,
    COUNT(*) AS violation_count
FROM RISK_POLICY_BAND band
JOIN RISK_POLICY policy
  ON policy.risk_policy_id = band.risk_policy_id
JOIN PROTECTION_POLICY protection
  ON protection.protection_policy_id =
     band.protection_policy_id
WHERE policy.policy_version = 'AMOUNT_ONLY_V1'
  AND (
      (
        band.risk_tier = 'LOW'
        AND (
            protection.release_mode <> 'IMMEDIATE'
            OR protection.protection_seconds <> 0
        )
      )
      OR
      (
        band.risk_tier = 'MEDIUM'
        AND (
            protection.release_mode <> 'AFTER_TIMER'
            OR protection.protection_seconds <> 10
        )
      )
      OR
      (
        band.risk_tier = 'HIGH'
        AND (
            protection.release_mode <> 'AFTER_TIMER'
            OR protection.protection_seconds <> 60
        )
      )
      OR
      (
        band.risk_tier = 'VERY_HIGH'
        AND (
            protection.release_mode <> 'AFTER_REVIEW'
            OR protection.protection_seconds IS NOT NULL
        )
      )
  );

--------------------------------------------------------------------------------
-- 6. Ownership and policy-snapshot consistency
--------------------------------------------------------------------------------

SELECT
    'PAYMENT_ACCOUNT_OWNER' AS check_name,
    COUNT(*) AS violation_count
FROM PAYMENT_TRANSACTION payment
LEFT JOIN ACCOUNT source_account
  ON source_account.account_id = payment.source_account_id
 AND source_account.owner_user_id = payment.customer_user_id
WHERE source_account.account_id IS NULL

UNION ALL

SELECT
    'PAYMENT_BENEFICIARY_OWNER',
    COUNT(*)
FROM PAYMENT_TRANSACTION payment
LEFT JOIN BENEFICIARY beneficiary
  ON beneficiary.beneficiary_id = payment.beneficiary_id
 AND beneficiary.owner_user_id = payment.customer_user_id
WHERE beneficiary.beneficiary_id IS NULL

UNION ALL

SELECT
    'OTP_TRANSACTION_CUSTOMER',
    COUNT(*)
FROM PAYMENT_OTP_CHALLENGE challenge
JOIN PAYMENT_TRANSACTION payment
  ON payment.transaction_id = challenge.transaction_id
WHERE challenge.customer_user_id <> payment.customer_user_id

UNION ALL

SELECT
    'REVIEW_TRANSACTION_CUSTOMER',
    COUNT(*)
FROM RISK_REVIEW review
JOIN PAYMENT_TRANSACTION payment
  ON payment.transaction_id = review.transaction_id
WHERE review.customer_user_id <> payment.customer_user_id

UNION ALL

SELECT
    'RISK_FACTOR_SELECTED_BAND',
    COUNT(*)
FROM TRANSACTION_RISK_FACTOR factor
JOIN PAYMENT_TRANSACTION payment
  ON payment.transaction_id = factor.transaction_id
WHERE payment.risk_policy_band_id IS NULL
   OR factor.risk_policy_band_id <>
      payment.risk_policy_band_id

UNION ALL

SELECT
    'TRANSACTION_POLICY_SNAPSHOT',
    COUNT(*)
FROM PAYMENT_TRANSACTION payment
LEFT JOIN RISK_POLICY policy
  ON policy.risk_policy_id = payment.risk_policy_id
 AND policy.policy_version = payment.policy_version
LEFT JOIN RISK_POLICY_BAND band
  ON band.risk_policy_band_id = payment.risk_policy_band_id
 AND band.risk_policy_id = payment.risk_policy_id
 AND band.protection_policy_id = payment.protection_policy_id
 AND band.risk_tier = payment.risk_tier
 AND band.band_code = payment.matched_band_code
WHERE payment.risk_assessed_at IS NOT NULL
  AND (
      policy.risk_policy_id IS NULL
      OR band.risk_policy_band_id IS NULL
  );

--------------------------------------------------------------------------------
-- 7. State, risk, deadline and reservation consistency
--------------------------------------------------------------------------------

SELECT
    'PROTECTED_ROUTE' AS check_name,
    COUNT(*) AS violation_count
FROM PAYMENT_TRANSACTION
WHERE state = 'PROTECTED'
  AND (
      risk_tier NOT IN ('MEDIUM', 'HIGH')
      OR protected_until IS NULL
  )

UNION ALL

SELECT
    'VERY_HIGH_VERIFICATION_ROUTE',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state IN (
    'VERIFICATION_REQUIRED',
    'PENDING_RISK_REVIEW'
)
AND risk_tier <> 'VERY_HIGH'

UNION ALL

SELECT
    'PENDING_REVIEW_VERIFIED',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state = 'PENDING_RISK_REVIEW'
  AND verification_completed_at IS NULL

UNION ALL

SELECT
    'MEDIUM_HIGH_RELEASE_PROOF',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state IN ('RELEASED', 'SETTLED')
  AND risk_tier IN ('MEDIUM', 'HIGH')
  AND protected_until IS NULL

UNION ALL

SELECT
    'VERY_HIGH_RELEASE_PROOF',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state IN ('RELEASED', 'SETTLED')
  AND risk_tier = 'VERY_HIGH'
  AND verification_completed_at IS NULL

UNION ALL

SELECT
    'RESERVATION_BEARING_STATE',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state IN (
    'PROTECTED',
    'VERIFICATION_REQUIRED',
    'PENDING_RISK_REVIEW',
    'RELEASED'
)
AND (
    reserved_amount <> amount
    OR reserved_at IS NULL
    OR reservation_ended_at IS NOT NULL
)

UNION ALL

SELECT
    'NON_RESERVATION_STATE',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE state NOT IN (
    'PROTECTED',
    'VERIFICATION_REQUIRED',
    'PENDING_RISK_REVIEW',
    'RELEASED'
)
AND reserved_amount <> 0

UNION ALL

SELECT
    'DEADLINE_ORDER',
    COUNT(*)
FROM PAYMENT_TRANSACTION
WHERE protected_until IS NOT NULL
  AND (
      reserved_at IS NULL
      OR protected_until <= reserved_at
  );

--------------------------------------------------------------------------------
-- 8. Runtime grants after the physical rename
--------------------------------------------------------------------------------

SELECT
    grantee,
    table_name,
    privilege
FROM user_tab_privs_made
WHERE grantee = 'SAFEPAY_APP'
  AND table_name IN (
      'RISK_REVIEW',
      'SEQ_RISK_REVIEW_ID'
  )
ORDER BY table_name, privilege;

SELECT
    grantee,
    table_name,
    privilege
FROM user_tab_privs_made
WHERE grantee = 'SAFEPAY_APP'
  AND table_name IN (
      'MAKER_CHECKER_APPROVAL',
      'SEQ_MAKER_CHECKER_APPROVAL_ID'
  );