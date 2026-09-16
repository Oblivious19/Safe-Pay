-- REVIEW ONLY: not executed by the application or by this task.
-- One-time migration in the owner schema after 07 (and existing migrations).
-- Before manual execution: back up data and constraint DDL; stop ALL writers,
-- including the application and scheduler. Use a dedicated clean session.
-- Do not restart the old transaction writer afterward: it still emits HARD_HOLD
-- as a risk tier. Deploy the separately reviewed engine integration first.
-- Oracle DDL commits implicitly. This block is NOT atomic; after any DDL failure,
-- keep writers stopped and inspect constraints/data before recovery. Do not rerun
-- blindly. No DELETE, INSERT, trigger, sequence or column-type changes are made.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_count NUMBER;
    v_policy VARCHAR2(2000) := q'[
        (risk_tier = 'LOW' AND protection_seconds = 0
            AND authentication_required = 'N')
        OR (risk_tier = 'MEDIUM' AND protection_seconds = 10
            AND authentication_required = 'N')
        OR (risk_tier = 'HIGH' AND protection_seconds = 60
            AND authentication_required = 'N')
        OR (risk_tier = 'VERY_HIGH' AND protection_seconds = 0
            AND authentication_required = 'Y'
            AND protection_expires_at IS NULL)
    ]';
BEGIN
    -- 1. Check the owner schema before referencing application tables dynamically.
    SELECT COUNT(*) INTO v_count FROM user_tables
    WHERE table_name = 'TRANSACTION_DB';
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Expected Phase 1 TRANSACTION_DB table is missing.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'TRANSACTION_DB' AND (
        (column_name = 'TRANSACTION_ID' AND data_type = 'NUMBER' AND nullable = 'N')
        OR (column_name = 'AMOUNT' AND data_type = 'NUMBER'
            AND data_precision = 18 AND data_scale = 2 AND nullable = 'N')
        OR (column_name = 'STATE' AND data_type = 'VARCHAR2' AND char_length = 20 AND nullable = 'N')
        OR (column_name = 'RISK_TIER' AND data_type = 'VARCHAR2' AND char_length = 15 AND nullable = 'N')
        OR (column_name = 'PROTECTION_SECONDS' AND data_type = 'NUMBER' AND nullable = 'N')
        OR (column_name = 'AUTHENTICATION_REQUIRED' AND data_type = 'CHAR'
            AND char_length = 1 AND nullable = 'N')
        OR (column_name = 'RISK_REASON' AND data_type = 'VARCHAR2' AND char_length = 500 AND nullable = 'N')
        OR (column_name = 'PROTECTION_EXPIRES_AT' AND data_type = 'TIMESTAMP(6)' AND nullable = 'Y')
    );
    IF v_count <> 8 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Unexpected Phase 1 transaction column types/nullability. Review schema.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'TRANSACTION_DB' AND constraint_type = 'C'
      AND status = 'ENABLED' AND validated = 'VALIDATED'
      AND constraint_name IN ('SP_CK_TX_TIER', 'SP_CK_TX_RISK_RULE',
          'SP_CK_TX_AUTH', 'SP_CK_TX_STATE', 'SP_CK_TX_AMOUNT', 'SP_CK_TX_REASON');
    IF v_count <> 6 THEN
        RAISE_APPLICATION_ERROR(-20003, 'Expected enabled/validated Phase 1 checks are missing.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE constraint_name IN ('SP_CK_TX_TIER_V2', 'SP_CK_TX_RISK_POLICY');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20004, 'New constraints already exist. Migration may be applied or partial.');
    END IF;

    -- Unknown update triggers could change other columns or tables during backfill.
    SELECT COUNT(*) INTO v_count FROM user_triggers
    WHERE table_name = 'TRANSACTION_DB' AND status = 'ENABLED'
      AND triggering_event LIKE '%UPDATE%';
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20005, 'Review enabled transaction UPDATE triggers before migration.');
    END IF;

    -- 2. Test the proposed values WITHOUT modifying any existing row.
    EXECUTE IMMEDIATE q'[
        SELECT COUNT(*) FROM (
            SELECT CASE WHEN risk_tier = 'HARD_HOLD' THEN 'VERY_HIGH'
                        ELSE risk_tier END AS risk_tier,
                   protection_seconds, authentication_required, protection_expires_at
            FROM transaction_db
        ) WHERE NOT (]' || v_policy || ')' INTO v_count;
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20006,
            'Existing rows conflict with the new tier/duration/authentication/expiry policy. No automatic repair.');
    END IF;
    EXECUTE IMMEDIATE
        'SELECT COUNT(*) FROM transaction_db WHERE risk_tier = ''HARD_HOLD'''
        INTO v_count;
    DBMS_OUTPUT.PUT_LINE('Rows to relabel HARD_HOLD -> VERY_HIGH: ' || v_count);

    -- 3. Install replacement guards before removing old checks.
    -- NOVALIDATE temporarily leaves historical rows unchecked, but enforces
    -- the new policy on writes. Both checks are fully validated below.
    EXECUTE IMMEDIATE q'[
        ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_tier_v2
        CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')) ENABLE NOVALIDATE
    ]';
    EXECUTE IMMEDIATE
        'ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_risk_policy CHECK ('
        || v_policy || ') ENABLE NOVALIDATE';
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_risk_rule';
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_tier';

    -- 4. The ONLY data change. The actual state column is STATE, not TRANSACTION_STATE.
    EXECUTE IMMEDIATE q'[
        UPDATE transaction_db SET risk_tier = 'VERY_HIGH' WHERE risk_tier = 'HARD_HOLD'
    ]';
    DBMS_OUTPUT.PUT_LINE('Risk tiers relabeled: ' || SQL%ROWCOUNT);

    -- 5. Validate ALL historical rows. The first DDL also commits the backfill.
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ENABLE VALIDATE CONSTRAINT sp_ck_tx_tier_v2';
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ENABLE VALIDATE CONSTRAINT sp_ck_tx_risk_policy';
    DBMS_OUTPUT.PUT_LINE('Migration complete. Keep old transaction writers stopped.');
END;
/

-- Read-only verification; existing state/authentication checks remain unchanged.
SELECT constraint_name, status, validated
FROM user_constraints
WHERE table_name = 'TRANSACTION_DB'
  AND constraint_name IN ('SP_CK_TX_TIER_V2', 'SP_CK_TX_RISK_POLICY',
                          'SP_CK_TX_AUTH', 'SP_CK_TX_STATE')
ORDER BY constraint_name;

SELECT risk_tier, state, COUNT(*) AS row_count
FROM transaction_db
GROUP BY risk_tier, state
ORDER BY risk_tier, state;
