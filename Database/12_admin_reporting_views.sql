-- REVIEW ONLY: do not execute automatically. No transaction data/constraints change.
-- Targets the 07 TRANSACTION_DB schema; compatible before/after migrations 10/11.
-- Run manually as owner with CREATE VIEW privilege, after review, in a clean session.
-- Oracle DDL commits implicitly; if partly applied, inspect before retrying.
-- Existing 05 reporting views target a different schema and are not replaced.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
    v_count NUMBER;
    v_aggregates VARCHAR2(3000) := q'[
        COUNT(*) AS total_transactions,
        COUNT(CASE WHEN state = 'SETTLED' THEN 1 END) AS settled_transactions,
        COUNT(CASE WHEN state = 'PROTECTED' THEN 1 END) AS protected_transactions,
        COUNT(CASE WHEN state = 'CANCELLED' THEN 1 END) AS cancelled_transactions,
        COUNT(CASE WHEN state = 'REJECTED' THEN 1 END) AS rejected_transactions,
        COUNT(CASE WHEN state = 'HARD_HOLD' THEN 1 END) AS hard_holds,
        COUNT(CASE WHEN risk_tier IN ('HIGH', 'VERY_HIGH', 'HARD_HOLD') THEN 1 END) AS high_risk_transactions,
        NVL(SUM(amount), 0) AS total_amount,
        NVL(SUM(CASE WHEN state = 'SETTLED' THEN amount ELSE 0 END), 0) AS settled_amount
    ]';
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'TRANSACTION_DB' AND (
        (column_name = 'STATE' AND data_type = 'VARCHAR2' AND nullable = 'N')
        OR (column_name = 'RISK_TIER' AND data_type = 'VARCHAR2' AND nullable = 'N')
        OR (column_name = 'AMOUNT' AND data_type = 'NUMBER'
            AND data_precision = 18 AND data_scale = 2 AND nullable = 'N')
        OR (column_name = 'CREATED_AT' AND data_type = 'TIMESTAMP(6)' AND nullable = 'N')
    );
    IF v_count <> 4 THEN
        RAISE_APPLICATION_ERROR(-20201, 'Expected Phase 1 TRANSACTION_DB reporting columns are missing.');
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_objects
    WHERE object_name IN ('VW_SP_TX_REPORT_TOTALS', 'VW_SP_TX_REPORT_DAILY');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20202, 'Reporting objects already exist. Review rather than overwrite.');
    END IF;

    -- One totals row, including zeros when TRANSACTION_DB is empty.
    EXECUTE IMMEDIATE 'CREATE VIEW vw_sp_tx_report_totals AS SELECT '
        || v_aggregates || ' FROM transaction_db WITH READ ONLY';
    -- Creation-day cohorts with CURRENT states, not historical transition counts.
    -- Days without transactions have no row; no timezone conversion is performed.
    EXECUTE IMMEDIATE 'CREATE VIEW vw_sp_tx_report_daily AS SELECT TRUNC(created_at) AS report_day, '
        || v_aggregates || ' FROM transaction_db GROUP BY TRUNC(created_at) WITH READ ONLY';
END;
/

SELECT object_name, status FROM user_objects
WHERE object_name IN ('VW_SP_TX_REPORT_TOTALS', 'VW_SP_TX_REPORT_DAILY');
