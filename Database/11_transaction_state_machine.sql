-- REVIEW ONLY. Do not execute before review and coordinated application deployment.
-- Run once, as table owner, AFTER migration 10. Back up data and constraint DDL.
-- Stop ALL writers/schedulers and use a clean dedicated SQL Developer script session.
-- Oracle DDL commits implicitly: this is not an atomic/rollbackable migration.
-- No rows, state values, audit history, column types or sequences are changed.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables
    WHERE table_name = 'TRANSACTION_DB';
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20101, 'Expected TRANSACTION_DB is missing.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'TRANSACTION_DB' AND column_name = 'STATE'
      AND data_type = 'VARCHAR2' AND char_length = 20 AND nullable = 'N';
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20102, 'Expected STATE VARCHAR2(20) NOT NULL is missing.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'TRANSACTION_DB' AND constraint_type = 'C'
      AND status = 'ENABLED' AND validated = 'VALIDATED'
      AND constraint_name IN ('SP_CK_TX_STATE', 'SP_CK_TX_TIER_V2', 'SP_CK_TX_RISK_POLICY');
    IF v_count <> 3 THEN
        RAISE_APPLICATION_ERROR(-20103, 'Expected checks missing; review and complete migration 10 first.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE constraint_name = 'SP_CK_TX_STATE_V2';
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20104, 'STATE_V2 already exists. Inspect prior/partial execution; do not rerun blindly.');
    END IF;

    -- RISK_ASSESSED is not an alias for AUTHORIZED, RELEASED or SETTLED.
    -- Its safe next state cannot be inferred without reviewing the transaction.
    -- Stop BEFORE DDL. Preserve the row/state and its audit history unchanged.
    EXECUTE IMMEDIATE
        'SELECT COUNT(*) FROM transaction_db WHERE state = ''RISK_ASSESSED'''
        INTO v_count;
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20105, 'Found ' || v_count ||
            ' legacy RISK_ASSESSED rows. Review rows and audit history; a separately approved resolution is required.');
    END IF;

    EXECUTE IMMEDIATE q'[
        SELECT COUNT(*) FROM transaction_db
        WHERE state NOT IN ('CREATED', 'AUTHORIZED', 'PROTECTED', 'HARD_HOLD',
                            'RELEASED', 'CANCELLED', 'REJECTED', 'SETTLED')
    ]' INTO v_count;
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20106, 'Unexpected transaction states exist. No automatic state conversion.');
    END IF;

    -- Validate replacement before removing the existing guard.
    EXECUTE IMMEDIATE q'[
        ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_state_v2
        CHECK (state IN ('CREATED', 'AUTHORIZED', 'PROTECTED', 'HARD_HOLD',
                        'RELEASED', 'CANCELLED', 'REJECTED', 'SETTLED')) ENABLE VALIDATE
    ]';
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_state';
    DBMS_OUTPUT.PUT_LINE('State constraint updated; all existing rows and values preserved.');
END;
/

SELECT constraint_name, status, validated
FROM user_constraints
WHERE table_name = 'TRANSACTION_DB' AND constraint_name = 'SP_CK_TX_STATE_V2';

SELECT state, COUNT(*) AS row_count
FROM transaction_db GROUP BY state ORDER BY state;

-- If preflight reports RISK_ASSESSED, review separately (read-only):
-- SELECT transaction_id, state, risk_tier, authentication_required,
--        protection_seconds, protection_expires_at, settled_at, cancelled_at
-- FROM transaction_db WHERE state = 'RISK_ASSESSED';
-- Inspect matching AUDIT_LOG rows too. Do not guess a replacement state.
