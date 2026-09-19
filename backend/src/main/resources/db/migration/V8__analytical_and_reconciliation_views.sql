--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V8
-- Read-only analytical, operational and reconciliation views
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Transaction dashboard
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

    latest_approval.approval_id,
    latest_approval.review_round,
    latest_approval.approval_status,
    latest_approval.assigned_risk_officer_id,

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
        ranked_approval.transaction_id,
        ranked_approval.approval_id,
        ranked_approval.review_round,
        ranked_approval.status AS approval_status,
        ranked_approval.assigned_risk_officer_id
    FROM (
        SELECT
            approval.transaction_id,
            approval.approval_id,
            approval.review_round,
            approval.status,
            approval.assigned_risk_officer_id,
            ROW_NUMBER() OVER (
                PARTITION BY approval.transaction_id
                ORDER BY
                    approval.review_round DESC,
                    approval.approval_id DESC
            ) AS row_position
        FROM MAKER_CHECKER_APPROVAL approval
    ) ranked_approval
    WHERE ranked_approval.row_position = 1
) latest_approval
    ON latest_approval.transaction_id = pt.transaction_id

WITH READ ONLY;

--------------------------------------------------------------------------------
-- 2. UTC daily risk summary
--------------------------------------------------------------------------------

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
        TO_CHAR(pt.policy_version),
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
        TO_CHAR(pt.policy_version),
        'UNASSIGNED'
    )

WITH READ ONLY;

--------------------------------------------------------------------------------
-- 3. Pending Risk Officer approval queue
--------------------------------------------------------------------------------

CREATE OR REPLACE VIEW VW_PENDING_APPROVALS AS
SELECT
    approval.approval_id,
    approval.review_round,
    approval.status AS approval_status,

    approval.transaction_id,
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

    approval.assigned_risk_officer_id,
    risk_officer.full_name AS assigned_risk_officer_name,

    approval.requested_at,
    approval.claimed_at,
    approval.updated_at

FROM MAKER_CHECKER_APPROVAL approval

JOIN PAYMENT_TRANSACTION payment
    ON payment.transaction_id = approval.transaction_id

JOIN APP_USER customer
    ON customer.user_id = payment.customer_user_id

JOIN ACCOUNT source_account
    ON source_account.account_id = payment.source_account_id

JOIN BENEFICIARY beneficiary
    ON beneficiary.beneficiary_id = payment.beneficiary_id

LEFT JOIN APP_USER risk_officer
    ON risk_officer.user_id =
        approval.assigned_risk_officer_id

WHERE approval.status IN (
    'PENDING',
    'IN_REVIEW'
)
AND payment.state = 'PENDING_RISK_REVIEW'
AND payment.risk_tier = 'VERY_HIGH'

WITH READ ONLY;

--------------------------------------------------------------------------------
-- 4. Ledger reconciliation
--------------------------------------------------------------------------------

CREATE OR REPLACE VIEW VW_LEDGER_RECONCILIATION AS
SELECT
    posting.posting_id,
    posting.posting_reference,
    posting.posting_type,
    posting.transaction_id,
    posting.source_system,
    posting.idempotency_key,

    posting.status AS posting_status,
    posting.amount AS posting_amount,
    posting.currency_code,

    posting.expected_entry_count,
    NVL(entry_summary.actual_entry_count, 0)
        AS actual_entry_count,

    NVL(entry_summary.debit_count, 0)
        AS debit_count,

    NVL(entry_summary.credit_count, 0)
        AS credit_count,

    NVL(entry_summary.debit_total, 0)
        AS debit_total,

    NVL(entry_summary.credit_total, 0)
        AS credit_total,

    NVL(entry_summary.distinct_account_count, 0)
        AS distinct_account_count,

    CASE
        WHEN NVL(
            entry_summary.actual_entry_count,
            0
        ) < posting.expected_entry_count
        THEN 'INCOMPLETE'

        WHEN NVL(
            entry_summary.actual_entry_count,
            0
        ) = posting.expected_entry_count
        AND NVL(entry_summary.debit_count, 0) = 1
        AND NVL(entry_summary.credit_count, 0) = 1
        AND NVL(entry_summary.debit_total, 0)
            = NVL(entry_summary.credit_total, 0)
        AND NVL(entry_summary.debit_total, 0)
            = posting.amount
        AND NVL(entry_summary.credit_total, 0)
            = posting.amount
        AND NVL(
            entry_summary.distinct_account_count,
            0
        ) = 2
        THEN 'BALANCED'

        ELSE 'MISMATCH'
    END AS reconciliation_status,

    posting.created_at,
    posting.posted_at,
    posting.failed_at,
    posting.failure_code,
    posting.updated_at

FROM LEDGER_POSTING posting

LEFT JOIN (
    SELECT
        entry.posting_id,

        COUNT(*) AS actual_entry_count,

        SUM(
            CASE
                WHEN entry.entry_type = 'DEBIT' THEN 1
                ELSE 0
            END
        ) AS debit_count,

        SUM(
            CASE
                WHEN entry.entry_type = 'CREDIT' THEN 1
                ELSE 0
            END
        ) AS credit_count,

        SUM(
            CASE
                WHEN entry.entry_type = 'DEBIT'
                THEN entry.amount
                ELSE 0
            END
        ) AS debit_total,

        SUM(
            CASE
                WHEN entry.entry_type = 'CREDIT'
                THEN entry.amount
                ELSE 0
            END
        ) AS credit_total,

        COUNT(DISTINCT entry.account_id)
            AS distinct_account_count

    FROM LEDGER_ENTRY entry

    GROUP BY entry.posting_id
) entry_summary
    ON entry_summary.posting_id = posting.posting_id

WITH READ ONLY;

--------------------------------------------------------------------------------
-- 5. Account reservation reconciliation
--------------------------------------------------------------------------------

CREATE OR REPLACE VIEW VW_RESERVATION_RECONCILIATION AS
SELECT
    account.account_id,
    account.owner_user_id,
    account.account_type,

    CASE
        WHEN LENGTH(account.account_number) <= 4
        THEN RPAD('*', LENGTH(account.account_number), '*')
        ELSE
            RPAD(
                '*',
                LENGTH(account.account_number) - 4,
                '*'
            )
            || SUBSTR(account.account_number, -4)
    END AS masked_account_number,

    account.currency_code,
    account.status AS account_status,

    account.current_balance,
    account.reserved_amount AS stored_reserved_amount,

    NVL(
        active_reservations.calculated_reserved_amount,
        0
    ) AS calculated_reserved_amount,

    account.available_balance,

    account.reserved_amount
        - NVL(
            active_reservations.calculated_reserved_amount,
            0
        ) AS reservation_difference,

    CASE
        WHEN account.reserved_amount =
            NVL(
                active_reservations.calculated_reserved_amount,
                0
            )
        THEN 'MATCH'
        ELSE 'MISMATCH'
    END AS reconciliation_status,

    account.updated_at

FROM ACCOUNT account

LEFT JOIN (
    SELECT
        payment.source_account_id,
        SUM(payment.reserved_amount)
            AS calculated_reserved_amount

    FROM PAYMENT_TRANSACTION payment

    WHERE payment.state IN (
        'PROTECTED',
        'VERIFICATION_REQUIRED',
        'PENDING_RISK_REVIEW',
        'RELEASED'
    )

    GROUP BY payment.source_account_id
) active_reservations
    ON active_reservations.source_account_id =
        account.account_id

WITH READ ONLY;