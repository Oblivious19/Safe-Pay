-- MANUAL ONLY. This script is not registered with an application migration runner.
-- Run in SQL Developer (Run Script/F5) or SQL*Plus as the existing table owner.
-- First back up TRANSACTION_DB and its constraint DDL; stop ALL application writers
-- and schedulers. Oracle DDL commits implicitly: a ROLLBACK cannot undo DDL.
-- Expected baseline: Database/14_existing_oracle_ui_migration.sql (also fresh schema 00).
-- If preflight fails, STOP and inspect the schema; do not bypass the checks.
-- If any DDL fails after preflight, keep writers stopped and inspect the partial
-- changes before recovery. This is deliberately a one-time script, not a blind rerun.
-- No rows, balances, purposes, risk reasons, states or saved deadlines are rewritten.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS target_schema FROM dual;
SELECT constraint_name, status, validated, search_condition_vc
  FROM user_constraints WHERE table_name = 'TRANSACTION_DB' AND constraint_type = 'C';

DECLARE
    v_count NUMBER;
    v_condition VARCHAR2(4000);
    v_expected VARCHAR2(4000) := q'[
      (risk_tier='LOW' AND protection_seconds=0 AND authentication_required='N') OR
      (risk_tier='MEDIUM' AND protection_seconds=10 AND authentication_required='N') OR
      (risk_tier='HIGH' AND protection_seconds=60 AND authentication_required='N') OR
      (risk_tier IN ('VERY_HIGH','HARD_HOLD') AND protection_seconds=0
        AND authentication_required='Y' AND protection_expires_at IS NULL)
    ]';
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='TRANSACTION_DB';
    IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20501, 'Expected owner table TRANSACTION_DB not found'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
      WHERE table_name='TRANSACTION_DB' AND column_name='PAYMENT_CATEGORY';
    IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20502, 'PAYMENT_CATEGORY already exists: inspect, do not rerun'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_constraints WHERE constraint_name IN
      ('SP_CK_TX_CAT_VALUES','SP_CK_TX_CAT_AMOUNT','SP_CK_TX_CAT_PURPOSE','SP_CK_TX_RISK_POLICY_V2');
    IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20503, 'New constraint name already exists: inspect partial migration'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_constraints WHERE table_name='TRANSACTION_DB'
      AND constraint_name='SP_CK_TX_RISK_POLICY' AND constraint_type='C'
      AND status='ENABLED' AND validated='VALIDATED';
    IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20504, 'Expected validated SP_CK_TX_RISK_POLICY not found'); END IF;
    SELECT search_condition_vc INTO v_condition FROM user_constraints
      WHERE table_name='TRANSACTION_DB' AND constraint_name='SP_CK_TX_RISK_POLICY';
    IF REGEXP_REPLACE(UPPER(v_condition), '[[:space:]()"]', '') <>
       REGEXP_REPLACE(UPPER(v_expected), '[[:space:]()"]', '') THEN
      RAISE_APPLICATION_ERROR(-20505, 'Risk policy differs from inspected baseline; review before changing it');
    END IF;
    -- Another check on these columns could still reject the new duration.
    SELECT COUNT(DISTINCT c.constraint_name) INTO v_count
      FROM user_constraints c JOIN user_cons_columns cc
        ON cc.constraint_name=c.constraint_name AND cc.table_name=c.table_name
      WHERE c.table_name='TRANSACTION_DB' AND c.constraint_type='C'
        AND c.constraint_name<>'SP_CK_TX_RISK_POLICY'
        AND cc.column_name='PROTECTION_SECONDS'
        AND NOT REGEXP_LIKE(c.search_condition_vc, '^"?PROTECTION_SECONDS"? IS NOT NULL$', 'i');
    IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20506, 'Additional duration constraint exists; inspect before migration'); END IF;
    DBMS_OUTPUT.PUT_LINE('Preflight passed. Adding category and allowing HIGH durations 30 (new) / 60 (historical).');
END;
/

ALTER TABLE transaction_db ADD (payment_category VARCHAR2(20 CHAR));
ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_cat_values CHECK
  (payment_category IN ('MEDICAL','LOAN','FRIENDS_FAMILY','INVESTMENTS','OTHERS'));
ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_cat_amount CHECK
  (payment_category IS NULL OR amount > 100000.00);
ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_cat_purpose CHECK (
  payment_category IS NULL OR payment_category <> 'OTHERS' OR
  (purpose IS NOT NULL AND LENGTH(purpose) BETWEEN 1 AND 140
    AND purpose=TRIM(purpose) AND REGEXP_LIKE(purpose, '[^[:space:]]')));

-- Add the compatible constraint BEFORE dropping the old one; keep all other checks.
ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_risk_policy_v2 CHECK (
  (risk_tier='LOW' AND protection_seconds=0 AND authentication_required='N') OR
  (risk_tier='MEDIUM' AND protection_seconds=10 AND authentication_required='N') OR
  (risk_tier='HIGH' AND protection_seconds IN (30,60) AND authentication_required='N') OR
  (risk_tier IN ('VERY_HIGH','HARD_HOLD') AND protection_seconds=0
    AND authentication_required='Y' AND protection_expires_at IS NULL));
ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_risk_policy;
ALTER TABLE transaction_db RENAME CONSTRAINT sp_ck_tx_risk_policy_v2 TO sp_ck_tx_risk_policy;

SELECT column_name, data_type, char_length, char_used, nullable FROM user_tab_columns
 WHERE table_name='TRANSACTION_DB' AND column_name='PAYMENT_CATEGORY';
SELECT constraint_name, status, validated, search_condition_vc FROM user_constraints
 WHERE table_name='TRANSACTION_DB' AND constraint_name IN
 ('SP_CK_TX_CAT_VALUES','SP_CK_TX_CAT_AMOUNT','SP_CK_TX_CAT_PURPOSE','SP_CK_TX_RISK_POLICY');
SELECT risk_tier, protection_seconds, COUNT(*) AS existing_payments
 FROM transaction_db GROUP BY risk_tier, protection_seconds ORDER BY risk_tier, protection_seconds;
-- Deploy the updated backend/frontend together only after successful verification.
-- Start Oracle with ddl-auto=validate (not update); do not restart an old writer
-- that creates very-high payments without category. Old rows remain reviewable.
