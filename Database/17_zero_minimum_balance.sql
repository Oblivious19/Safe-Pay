-- MANUAL ONLY, existing current SafePay Oracle schema owner. Not executed by the app.
-- Back up first and stop application writers. Oracle DDL commits implicitly.
-- No rows/balances are updated, and no new constraint or balance default is added.
-- Registration supplies 200000.00 explicitly in Java for new customers only.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

-- Inspect the current checks before proceeding. If another positive minimum-balance
-- check exists, review it separately; this script removes only the known old check.
SELECT constraint_name, search_condition_vc
FROM user_constraints WHERE table_name = 'ACCOUNT' AND constraint_type = 'C';

DECLARE
  old_condition VARCHAR2(4000);
BEGIN
  BEGIN
    SELECT search_condition_vc INTO old_condition FROM user_constraints
    WHERE table_name = 'ACCOUNT' AND constraint_name = 'SP_CK_ACCT_MIN_BAL' AND constraint_type = 'C';
  EXCEPTION WHEN NO_DATA_FOUND THEN old_condition := NULL;
  END;
  IF old_condition IS NOT NULL THEN
    IF NOT REGEXP_LIKE(REGEXP_REPLACE(UPPER(old_condition), '[[:space:]"()]', ''), '^BALANCE>=5000([.]0+)?$') THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unexpected SP_CK_ACCT_MIN_BAL definition. Review manually; no change made.');
    END IF;
    EXECUTE IMMEDIATE 'ALTER TABLE account DROP CONSTRAINT sp_ck_acct_min_bal';
  END IF;
END;
/

-- Oracle DEFAULT NULL removes the old 5000 default. It does NOT change any row
-- or remove NOT NULL; inserts must supply their balance explicitly.
ALTER TABLE account MODIFY (balance DEFAULT NULL);

SELECT column_name, data_default, nullable FROM user_tab_columns
WHERE table_name = 'ACCOUNT' AND column_name = 'BALANCE';
SELECT constraint_name, search_condition_vc FROM user_constraints
WHERE table_name = 'ACCOUNT' AND constraint_type = 'C';
