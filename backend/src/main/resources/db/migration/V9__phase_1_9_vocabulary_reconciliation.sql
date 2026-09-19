--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V9
-- Phase 1.9 Part 1: canonical vocabulary and backend-mapping identities
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Fail before any DDL if existing rows need a business-level conversion.
--    Numeric values cannot be guessed into stable text policy identifiers.
--    IN_REVIEW and ESCALATED have no approved V1 target state.
--------------------------------------------------------------------------------

DECLARE
    l_policy_rows      PLS_INTEGER;
    l_review_rows      PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_policy_rows
      FROM PAYMENT_TRANSACTION
     WHERE policy_version IS NOT NULL;

    IF l_policy_rows > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20090,
            'V9 stopped: map existing numeric policy_version values to approved text identifiers before migration'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_review_rows
      FROM MAKER_CHECKER_APPROVAL
     WHERE status IN ('IN_REVIEW', 'ESCALATED');

    IF l_review_rows > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20091,
            'V9 stopped: reconcile existing IN_REVIEW or ESCALATED rows before migration'
        );
    END IF;
END;
/

--------------------------------------------------------------------------------
-- 2. Align the transaction policy snapshot with RISK_POLICY.policy_version.
--------------------------------------------------------------------------------

ALTER TABLE PAYMENT_TRANSACTION
    DROP CONSTRAINT CK_PAYMENT_TX_POLICY_VERSION;

ALTER TABLE PAYMENT_TRANSACTION
    MODIFY (
        policy_version VARCHAR2(50 CHAR)
    );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_POLICY_VERSION
        CHECK (
            policy_version IS NULL
            OR (
                TRIM(policy_version) IS NOT NULL
                AND policy_version = UPPER(TRIM(policy_version))
            )
        );

--------------------------------------------------------------------------------
-- 3. Rename the review table and sequence without destroying their data.
--    The existing approval_id column is retained as the stable record identity.
--------------------------------------------------------------------------------

RENAME MAKER_CHECKER_APPROVAL TO RISK_REVIEW;

RENAME SEQ_MAKER_CHECKER_APPROVAL_ID TO SEQ_RISK_REVIEW_ID;

--------------------------------------------------------------------------------
-- 4. Replace the old review vocabulary and lifecycle rules.
--------------------------------------------------------------------------------

DROP TRIGGER TRG_MAKER_CHECKER_FINAL_LOCK;

DROP INDEX UK_MAKER_CHECKER_OPEN_TX;

ALTER TABLE RISK_REVIEW
    DROP CONSTRAINT CK_MAKER_CHECKER_STATUS;

ALTER TABLE RISK_REVIEW
    DROP CONSTRAINT CK_MAKER_CHECKER_REASON;

ALTER TABLE RISK_REVIEW
    DROP CONSTRAINT CK_MAKER_CHECKER_LIFECYCLE;

ALTER TABLE RISK_REVIEW
    DROP CONSTRAINT CK_MAKER_CHECKER_SEPARATION;

ALTER TABLE RISK_REVIEW
    DROP CONSTRAINT CK_MAKER_CHECKER_CANCEL_ACTOR;

UPDATE RISK_REVIEW
   SET status = 'REVERIFICATION_REQUESTED'
 WHERE status = 'VERIFICATION_REQUESTED';

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT CK_RISK_REVIEW_STATUS
        CHECK (
            status IN (
                'PENDING',
                'APPROVED',
                'REJECTED',
                'REVERIFICATION_REQUESTED',
                'CANCELLED'
            )
        );

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT CK_RISK_REVIEW_REASON
        CHECK (
            status NOT IN (
                'REJECTED',
                'REVERIFICATION_REQUESTED'
            )
            OR TRIM(decision_reason) IS NOT NULL
        );

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT CK_RISK_REVIEW_LIFECYCLE
        CHECK (
            (
                status = 'PENDING'
                AND claimed_at IS NULL
                AND decided_at IS NULL
                AND decided_by_user_id IS NULL
            )
            OR
            (
                status IN (
                    'APPROVED',
                    'REJECTED',
                    'REVERIFICATION_REQUESTED'
                )
                AND assigned_risk_officer_id IS NOT NULL
                AND decided_at IS NOT NULL
                AND decided_by_user_id IS NOT NULL
            )
            OR
            (
                status = 'CANCELLED'
                AND decided_at IS NOT NULL
                AND decided_by_user_id IS NOT NULL
            )
        );

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT CK_RISK_REVIEW_SEPARATION
        CHECK (
            status NOT IN (
                'APPROVED',
                'REJECTED',
                'REVERIFICATION_REQUESTED'
            )
            OR decided_by_user_id <> customer_user_id
        );

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT CK_RISK_REVIEW_CANCEL_ACTOR
        CHECK (
            status <> 'CANCELLED'
            OR decided_by_user_id = customer_user_id
        );

--------------------------------------------------------------------------------
-- 5. Rename retained constraints and indexes.
--------------------------------------------------------------------------------

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT PK_MAKER_CHECKER_APPROVAL
    TO PK_RISK_REVIEW;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT UK_MAKER_CHECKER_TX_ROUND
    TO UK_RISK_REVIEW_TX_ROUND;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT FK_MAKER_CHECKER_TRANSACTION
    TO FK_RISK_REVIEW_TRANSACTION;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT FK_MAKER_CHECKER_CUSTOMER
    TO FK_RISK_REVIEW_CUSTOMER;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT FK_MAKER_CHECKER_ASSIGNEE
    TO FK_RISK_REVIEW_ASSIGNEE;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT FK_MAKER_CHECKER_DECIDER
    TO FK_RISK_REVIEW_DECIDER;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_ROUND
    TO CK_RISK_REVIEW_ROUND;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_CLAIMED_AT
    TO CK_RISK_REVIEW_CLAIMED_AT;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_DECIDED_AT
    TO CK_RISK_REVIEW_DECIDED_AT;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_TIME_ORDER
    TO CK_RISK_REVIEW_TIME_ORDER;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_UPDATED_AT
    TO CK_RISK_REVIEW_UPDATED_AT;

ALTER TABLE RISK_REVIEW
    RENAME CONSTRAINT CK_MAKER_CHECKER_VERSION
    TO CK_RISK_REVIEW_VERSION;

ALTER INDEX PK_MAKER_CHECKER_APPROVAL
    RENAME TO PK_RISK_REVIEW;

ALTER INDEX UK_MAKER_CHECKER_TX_ROUND
    RENAME TO UK_RISK_REVIEW_TX_ROUND;

ALTER INDEX IDX_MAKER_CHECKER_QUEUE
    RENAME TO IDX_RISK_REVIEW_QUEUE;

ALTER INDEX IDX_MAKER_CHECKER_ASSIGNEE
    RENAME TO IDX_RISK_REVIEW_ASSIGNEE;

ALTER INDEX IDX_MAKER_CHECKER_CUSTOMER
    RENAME TO IDX_RISK_REVIEW_CUSTOMER;

--------------------------------------------------------------------------------
-- 6. Only PENDING is an open V1 review state.
--------------------------------------------------------------------------------

CREATE UNIQUE INDEX UK_RISK_REVIEW_OPEN_TX
    ON RISK_REVIEW (
        CASE
            WHEN status = 'PENDING'
            THEN transaction_id
            ELSE NULL
        END
    );

--------------------------------------------------------------------------------
-- 7. Protect completed review rounds from mutation or deletion.
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_RISK_REVIEW_FINAL_LOCK
    BEFORE UPDATE OR DELETE
    ON RISK_REVIEW
    FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20044,
            'Risk review records cannot be deleted'
        );
    END IF;

    IF UPDATING
       AND :OLD.status IN (
           'APPROVED',
           'REJECTED',
           'REVERIFICATION_REQUESTED',
           'CANCELLED'
       )
    THEN
        RAISE_APPLICATION_ERROR(
            -20045,
            'A completed risk review round cannot be modified'
        );
    END IF;
END;
/

--------------------------------------------------------------------------------
-- 8. Rebuild views invalidated by the table rename/type correction.
--------------------------------------------------------------------------------

CREATE OR REPLACE VIEW VW_TRANSACTION_DASHBOARD AS
SELECT
    pt.transaction_id,
    pt.transaction_reference,

    pt.customer_user_id,
    customer.full_name AS customer_name,

    pt.source_account_id,
    CASE
        WHEN LENGTH(source_account.account_number) <= 4
        THEN RPAD('*', LENGTH(source_account.account_number), '*')
        ELSE
            RPAD(
                '*',
                LENGTH(source_account.account_number) - 4,
                '*'
            )
            || SUBSTR(source_account.account_number, -4)
    END AS masked_source_account,
    source_account.account_type AS source_account_type,

    pt.beneficiary_id,
    beneficiary.beneficiary_name,
    beneficiary.nickname AS beneficiary_nickname,
    beneficiary.payment_method,

    CASE
        WHEN beneficiary.payment_method = 'BANK_ACCOUNT'
        THEN
            CASE
                WHEN LENGTH(beneficiary.bank_account_number) <= 4
                THEN RPAD(
                    '*',
                    LENGTH(beneficiary.bank_account_number),
                    '*'
                )
                ELSE
                    RPAD(
                        '*',
                        LENGTH(beneficiary.bank_account_number) - 4,
                        '*'
                    )
                    || SUBSTR(
                        beneficiary.bank_account_number,
                        -4
                    )
            END

        WHEN beneficiary.payment_method = 'UPI'
        THEN
            CASE
                WHEN INSTR(beneficiary.upi_id, '@') > 1
                THEN
                    SUBSTR(beneficiary.upi_id, 1, 1)
                    || '***'
                    || SUBSTR(
                        beneficiary.upi_id,
                        INSTR(beneficiary.upi_id, '@')
                    )
                ELSE '***'
            END
    END AS masked_beneficiary_destination,

    beneficiary.bank_name AS beneficiary_bank_name,

    pt.amount,
    pt.currency_code,
    pt.state,

    pt.risk_tier,
    pt.risk_score,
    pt.policy_version,
    pt.matched_band_code,
    pt.risk_explanation,

    pt.reserved_amount,
    pt.protection_seconds,
    pt.protected_until,

    latest_review.approval_id,
    latest_review.review_round,
    latest_review.approval_status,
    latest_review.assigned_risk_officer_id,

    pt.created_at,
    pt.updated_at,
    pt.risk_assessed_at,
    pt.released_at,
    pt.settled_at,
    pt.cancelled_at,
    pt.failed_at

FROM PAYMENT_TRANSACTION pt

JOIN APP_USER customer
    ON customer.user_id = pt.customer_user_id

JOIN ACCOUNT source_account
    ON source_account.account_id = pt.source_account_id

JOIN BENEFICIARY beneficiary
    ON beneficiary.beneficiary_id = pt.beneficiary_id

LEFT JOIN (
    SELECT
        ranked_review.transaction_id,
        ranked_review.approval_id,
        ranked_review.review_round,
        ranked_review.status AS approval_status,
        ranked_review.assigned_risk_officer_id
    FROM (
        SELECT
            review.transaction_id,
            review.approval_id,
            review.review_round,
            review.status,
            review.assigned_risk_officer_id,
            ROW_NUMBER() OVER (
                PARTITION BY review.transaction_id
                ORDER BY
                    review.review_round DESC,
                    review.approval_id DESC
            ) AS row_position
        FROM RISK_REVIEW review
    ) ranked_review
    WHERE ranked_review.row_position = 1
) latest_review
    ON latest_review.transaction_id = pt.transaction_id

WITH READ ONLY;

CREATE OR REPLACE VIEW VW_RISK_SUMMARY AS
SELECT
    TRUNC(
        CAST(
            SYS_EXTRACT_UTC(pt.created_at)
            AS DATE
        )
    ) AS transaction_date_utc,

    NVL(pt.risk_tier, 'UNASSESSED') AS risk_tier,
    pt.state,

    NVL(
        pt.policy_version,
        'UNASSIGNED'
    ) AS policy_version,

    COUNT(*) AS transaction_count,

    SUM(pt.amount) AS total_amount,
    AVG(pt.amount) AS average_amount,
    MIN(pt.amount) AS minimum_amount,
    MAX(pt.amount) AS maximum_amount,

    SUM(pt.reserved_amount) AS total_reserved_amount,

    SUM(
        CASE
            WHEN pt.state = 'SETTLED' THEN 1
            ELSE 0
        END
    ) AS settled_count,

    SUM(
        CASE
            WHEN pt.state = 'CANCELLED' THEN 1
            ELSE 0
        END
    ) AS cancelled_count,

    SUM(
        CASE
            WHEN pt.state = 'FAILED' THEN 1
            ELSE 0
        END
    ) AS failed_count

FROM PAYMENT_TRANSACTION pt

GROUP BY
    TRUNC(
        CAST(
            SYS_EXTRACT_UTC(pt.created_at)
            AS DATE
        )
    ),
    NVL(pt.risk_tier, 'UNASSESSED'),
    pt.state,
    NVL(
        pt.policy_version,
        'UNASSIGNED'
    )

WITH READ ONLY;

CREATE OR REPLACE VIEW VW_PENDING_APPROVALS AS
SELECT
    review.approval_id,
    review.review_round,
    review.status AS approval_status,

    review.transaction_id,
    payment.transaction_reference,
    payment.state AS transaction_state,

    payment.customer_user_id,
    customer.full_name AS customer_name,

    payment.source_account_id,
    CASE
        WHEN LENGTH(source_account.account_number) <= 4
        THEN RPAD('*', LENGTH(source_account.account_number), '*')
        ELSE
            RPAD(
                '*',
                LENGTH(source_account.account_number) - 4,
                '*'
            )
            || SUBSTR(source_account.account_number, -4)
    END AS masked_source_account,

    payment.beneficiary_id,
    beneficiary.beneficiary_name,
    beneficiary.payment_method,

    CASE
        WHEN beneficiary.payment_method = 'BANK_ACCOUNT'
        THEN
            CASE
                WHEN LENGTH(beneficiary.bank_account_number) <= 4
                THEN RPAD(
                    '*',
                    LENGTH(beneficiary.bank_account_number),
                    '*'
                )
                ELSE
                    RPAD(
                        '*',
                        LENGTH(beneficiary.bank_account_number) - 4,
                        '*'
                    )
                    || SUBSTR(
                        beneficiary.bank_account_number,
                        -4
                    )
            END

        WHEN beneficiary.payment_method = 'UPI'
        THEN
            CASE
                WHEN INSTR(beneficiary.upi_id, '@') > 1
                THEN
                    SUBSTR(beneficiary.upi_id, 1, 1)
                    || '***'
                    || SUBSTR(
                        beneficiary.upi_id,
                        INSTR(beneficiary.upi_id, '@')
                    )
                ELSE '***'
            END
    END AS masked_beneficiary_destination,

    payment.amount,
    payment.currency_code,
    payment.risk_tier,
    payment.risk_score,
    payment.risk_explanation,

    review.assigned_risk_officer_id,
    risk_officer.full_name AS assigned_risk_officer_name,

    review.requested_at,
    review.claimed_at,
    review.updated_at

FROM RISK_REVIEW review

JOIN PAYMENT_TRANSACTION payment
    ON payment.transaction_id = review.transaction_id

JOIN APP_USER customer
    ON customer.user_id = payment.customer_user_id

JOIN ACCOUNT source_account
    ON source_account.account_id = payment.source_account_id

JOIN BENEFICIARY beneficiary
    ON beneficiary.beneficiary_id = payment.beneficiary_id

LEFT JOIN APP_USER risk_officer
    ON risk_officer.user_id =
        review.assigned_risk_officer_id

WHERE review.status = 'PENDING'
AND payment.state = 'PENDING_RISK_REVIEW'
AND payment.risk_tier = 'VERY_HIGH'

WITH READ ONLY;