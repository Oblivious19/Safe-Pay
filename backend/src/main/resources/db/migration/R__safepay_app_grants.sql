-- SafePay restricted runtime grants
-- Executed by SAFEPAY_OWNER through Flyway.
-- SAFEPAY_APP receives no DDL or administrative privileges.

-----------------------------------------------------------------------
-- ROLE CATALOGUE
-----------------------------------------------------------------------

GRANT SELECT
ON APP_ROLE
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- USERS
-----------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
ON APP_USER
TO SAFEPAY_APP;

GRANT SELECT
ON SEQ_APP_USER_ID
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- USER-ROLE ASSIGNMENTS
-----------------------------------------------------------------------

GRANT SELECT, INSERT, DELETE
ON USER_ROLE
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- AUTHENTICATION SESSIONS
-----------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
ON AUTH_SESSION
TO SAFEPAY_APP;

GRANT SELECT
ON SEQ_AUTH_SESSION_ID
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- ACCOUNTS
-----------------------------------------------------------------------

-- Runtime may read balances and update reservations/settlement balances.
-- Account creation and deletion remain migration/admin controlled.
GRANT SELECT, UPDATE
ON ACCOUNT
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- BENEFICIARIES
-----------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
ON BENEFICIARY
TO SAFEPAY_APP;

GRANT SELECT
ON SEQ_BENEFICIARY_ID
TO SAFEPAY_APP;

-----------------------------------------------------------------------
-- VERSIONED RISK AND PROTECTION POLICY
-----------------------------------------------------------------------

-- V1 policy is read-only to the application.
GRANT SELECT
ON RISK_POLICY
TO SAFEPAY_APP;

GRANT SELECT
ON PROTECTION_POLICY
TO SAFEPAY_APP;

GRANT SELECT
ON RISK_POLICY_BAND
TO SAFEPAY_APP;

--------------------------------------------------------------------------------
-- V4 runtime grants: transaction and idempotency foundation
--------------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
    ON PAYMENT_TRANSACTION
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_PAYMENT_TRANSACTION_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT
    ON TRANSACTION_RISK_FACTOR
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_TX_RISK_FACTOR_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT, UPDATE
    ON IDEMPOTENCY_RECORD
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_IDEMPOTENCY_RECORD_ID
    TO SAFEPAY_APP;

--------------------------------------------------------------------------------
-- V5/V9 runtime grants: OTP verification and Risk Review
--------------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
    ON PAYMENT_OTP_CHALLENGE
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_PAYMENT_OTP_CHALLENGE_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT, UPDATE
    ON RISK_REVIEW
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_RISK_REVIEW_ID
    TO SAFEPAY_APP;

--------------------------------------------------------------------------------
-- V6 runtime grants: immutable ledger and transaction exceptions
--------------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE
    ON LEDGER_POSTING
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_LEDGER_POSTING_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT
    ON LEDGER_ENTRY
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_LEDGER_ENTRY_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT, UPDATE
    ON TRANSACTION_EXCEPTION
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_TRANSACTION_EXCEPTION_ID
    TO SAFEPAY_APP;

--------------------------------------------------------------------------------
-- V7 runtime grants: audit and durable in-app notifications
--------------------------------------------------------------------------------

GRANT SELECT, INSERT
    ON AUDIT_LOG
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_AUDIT_LOG_ID
    TO SAFEPAY_APP;

GRANT SELECT, INSERT, UPDATE
    ON APP_NOTIFICATION
    TO SAFEPAY_APP;

GRANT SELECT
    ON SEQ_APP_NOTIFICATION_ID
    TO SAFEPAY_APP;

--------------------------------------------------------------------------------
-- V8 runtime grants: read-only analytical and reconciliation views
--------------------------------------------------------------------------------

GRANT SELECT
    ON VW_TRANSACTION_DASHBOARD
    TO SAFEPAY_APP;

GRANT SELECT
    ON VW_RISK_SUMMARY
    TO SAFEPAY_APP;

GRANT SELECT
    ON VW_PENDING_APPROVALS
    TO SAFEPAY_APP;

GRANT SELECT
    ON VW_LEDGER_RECONCILIATION
    TO SAFEPAY_APP;

GRANT SELECT
    ON VW_RESERVATION_RECONCILIATION
    TO SAFEPAY_APP;