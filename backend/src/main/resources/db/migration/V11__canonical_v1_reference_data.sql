--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V11
-- Phase 1.9 Part 3: canonical roles and V1 risk-policy reference data
--
-- Demo users are intentionally provisioned by a local-profile bootstrap so
-- BCrypt hashes and demo credentials never enter production migrations.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Stop on conflicting pre-existing reference data.
--------------------------------------------------------------------------------

DECLARE
    l_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_count
      FROM APP_ROLE
     WHERE role_code IN (
        'CUSTOMER',
        'RISK_OFFICER',
        'SYSTEM_ADMIN',
        'AUDITOR'
     );

    IF l_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20200,
            'V11 stopped: one or more canonical role codes already exist'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM PROTECTION_POLICY
     WHERE protection_code IN (
        'IMMEDIATE_RELEASE_V1',
        'MEDIUM_TIMER_10S_V1',
        'HIGH_TIMER_60S_V1',
        'VERY_HIGH_REVIEW_V1'
     );

    IF l_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20201,
            'V11 stopped: one or more canonical protection policies already exist'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM RISK_POLICY
     WHERE policy_version = 'AMOUNT_ONLY_V1';

    IF l_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20202,
            'V11 stopped: AMOUNT_ONLY_V1 already exists'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM RISK_POLICY
     WHERE status = 'ACTIVE';

    IF l_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20203,
            'V11 stopped: retire or reconcile the existing active policy first'
        );
    END IF;
END;
/

--------------------------------------------------------------------------------
-- 2. Four stable authorities; two demo personas will consume these authorities.
--------------------------------------------------------------------------------

INSERT INTO APP_ROLE (
    role_id,
    role_code,
    description
)
VALUES (
    SEQ_APP_ROLE_ID.NEXTVAL,
    'CUSTOMER',
    'Owns and operates only the customer''s SafePay resources'
);

INSERT INTO APP_ROLE (
    role_id,
    role_code,
    description
)
VALUES (
    SEQ_APP_ROLE_ID.NEXTVAL,
    'RISK_OFFICER',
    'Reviews eligible VERY_HIGH payments and records controlled decisions'
);

INSERT INTO APP_ROLE (
    role_id,
    role_code,
    description
)
VALUES (
    SEQ_APP_ROLE_ID.NEXTVAL,
    'SYSTEM_ADMIN',
    'Performs controlled identity, access and operational administration'
);

INSERT INTO APP_ROLE (
    role_id,
    role_code,
    description
)
VALUES (
    SEQ_APP_ROLE_ID.NEXTVAL,
    'AUDITOR',
    'Reads approved audit, policy, transaction and reconciliation information'
);

--------------------------------------------------------------------------------
-- 3. V1 protection actions.
--------------------------------------------------------------------------------

INSERT INTO PROTECTION_POLICY (
    protection_policy_id,
    protection_code,
    release_mode,
    protection_seconds,
    customer_can_cancel,
    auto_release,
    otp_required,
    risk_review_required,
    description
)
VALUES (
    SEQ_PROTECTION_POLICY_ID.NEXTVAL,
    'IMMEDIATE_RELEASE_V1',
    'IMMEDIATE',
    0,
    'N',
    'Y',
    'N',
    'N',
    'LOW risk: release immediately for simulated settlement'
);

INSERT INTO PROTECTION_POLICY (
    protection_policy_id,
    protection_code,
    release_mode,
    protection_seconds,
    customer_can_cancel,
    auto_release,
    otp_required,
    risk_review_required,
    description
)
VALUES (
    SEQ_PROTECTION_POLICY_ID.NEXTVAL,
    'MEDIUM_TIMER_10S_V1',
    'AFTER_TIMER',
    10,
    'Y',
    'Y',
    'N',
    'N',
    'MEDIUM risk: ten-second protection window with customer cancellation'
);

INSERT INTO PROTECTION_POLICY (
    protection_policy_id,
    protection_code,
    release_mode,
    protection_seconds,
    customer_can_cancel,
    auto_release,
    otp_required,
    risk_review_required,
    description
)
VALUES (
    SEQ_PROTECTION_POLICY_ID.NEXTVAL,
    'HIGH_TIMER_60S_V1',
    'AFTER_TIMER',
    60,
    'Y',
    'Y',
    'N',
    'N',
    'HIGH risk: sixty-second protection window with customer cancellation'
);

INSERT INTO PROTECTION_POLICY (
    protection_policy_id,
    protection_code,
    release_mode,
    protection_seconds,
    customer_can_cancel,
    auto_release,
    otp_required,
    risk_review_required,
    description
)
VALUES (
    SEQ_PROTECTION_POLICY_ID.NEXTVAL,
    'VERY_HIGH_REVIEW_V1',
    'AFTER_REVIEW',
    NULL,
    'Y',
    'N',
    'Y',
    'Y',
    'VERY_HIGH risk: untimed OTP and Risk Review hard hold'
);

--------------------------------------------------------------------------------
-- 4. Create the policy as DRAFT so all four bands exist before activation.
--------------------------------------------------------------------------------

INSERT INTO RISK_POLICY (
    risk_policy_id,
    policy_version,
    policy_name,
    algorithm_type,
    currency_code,
    status,
    effective_from,
    effective_to,
    description
)
VALUES (
    SEQ_RISK_POLICY_ID.NEXTVAL,
    'AMOUNT_ONLY_V1',
    'SafePay V1 Amount-Only Policy',
    'AMOUNT_ONLY',
    'INR',
    'DRAFT',
    SYSTIMESTAMP,
    NULL,
    'Deterministic SafePay V1 policy using only the authorized payment amount'
);

--------------------------------------------------------------------------------
-- 5. Exact, contiguous V1 amount bands.
--------------------------------------------------------------------------------

INSERT INTO RISK_POLICY_BAND (
    risk_policy_band_id,
    risk_policy_id,
    protection_policy_id,
    band_code,
    risk_tier,
    minimum_amount,
    maximum_amount,
    display_order,
    explanation_template
)
SELECT
    SEQ_RISK_POLICY_BAND_ID.NEXTVAL,
    policy.risk_policy_id,
    protection.protection_policy_id,
    'AMOUNT_LOW_V1',
    'LOW',
    1.00,
    5000.00,
    1,
    'The payment amount matched the SafePay V1 LOW band.'
FROM RISK_POLICY policy
JOIN PROTECTION_POLICY protection
  ON protection.protection_code = 'IMMEDIATE_RELEASE_V1'
WHERE policy.policy_version = 'AMOUNT_ONLY_V1';

INSERT INTO RISK_POLICY_BAND (
    risk_policy_band_id,
    risk_policy_id,
    protection_policy_id,
    band_code,
    risk_tier,
    minimum_amount,
    maximum_amount,
    display_order,
    explanation_template
)
SELECT
    SEQ_RISK_POLICY_BAND_ID.NEXTVAL,
    policy.risk_policy_id,
    protection.protection_policy_id,
    'AMOUNT_MEDIUM_V1',
    'MEDIUM',
    5000.01,
    25000.00,
    2,
    'The payment amount matched the SafePay V1 MEDIUM band.'
FROM RISK_POLICY policy
JOIN PROTECTION_POLICY protection
  ON protection.protection_code = 'MEDIUM_TIMER_10S_V1'
WHERE policy.policy_version = 'AMOUNT_ONLY_V1';

INSERT INTO RISK_POLICY_BAND (
    risk_policy_band_id,
    risk_policy_id,
    protection_policy_id,
    band_code,
    risk_tier,
    minimum_amount,
    maximum_amount,
    display_order,
    explanation_template
)
SELECT
    SEQ_RISK_POLICY_BAND_ID.NEXTVAL,
    policy.risk_policy_id,
    protection.protection_policy_id,
    'AMOUNT_HIGH_V1',
    'HIGH',
    25000.01,
    100000.00,
    3,
    'The payment amount matched the SafePay V1 HIGH band.'
FROM RISK_POLICY policy
JOIN PROTECTION_POLICY protection
  ON protection.protection_code = 'HIGH_TIMER_60S_V1'
WHERE policy.policy_version = 'AMOUNT_ONLY_V1';

INSERT INTO RISK_POLICY_BAND (
    risk_policy_band_id,
    risk_policy_id,
    protection_policy_id,
    band_code,
    risk_tier,
    minimum_amount,
    maximum_amount,
    display_order,
    explanation_template
)
SELECT
    SEQ_RISK_POLICY_BAND_ID.NEXTVAL,
    policy.risk_policy_id,
    protection.protection_policy_id,
    'AMOUNT_VERY_HIGH_V1',
    'VERY_HIGH',
    100000.01,
    NULL,
    4,
    'The payment amount matched the SafePay V1 VERY_HIGH band.'
FROM RISK_POLICY policy
JOIN PROTECTION_POLICY protection
  ON protection.protection_code = 'VERY_HIGH_REVIEW_V1'
WHERE policy.policy_version = 'AMOUNT_ONLY_V1';

--------------------------------------------------------------------------------
-- 6. Explicit activation invokes V10 completeness and action validation.
--------------------------------------------------------------------------------

UPDATE RISK_POLICY
   SET status = 'ACTIVE'
 WHERE policy_version = 'AMOUNT_ONLY_V1'
   AND status = 'DRAFT';