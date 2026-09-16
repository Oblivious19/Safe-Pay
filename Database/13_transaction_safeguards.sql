-- Existing Shreya Oracle schema only, after user/account foundation (08/09) and risk migration10.
-- REVIEW and run manually with writers stopped. Oracle DDL commits implicitly.
-- This adds fields only; it does not reset balances, reinterpret states or run migration11.
DECLARE
  n NUMBER;
  PROCEDURE add_col(col_name VARCHAR2, ddl VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name='TRANSACTION_DB' AND column_name=col_name;
    IF n=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD (' || ddl || ')'; END IF;
  END;
  PROCEDURE unique_col(col_name VARCHAR2, constraint_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO n FROM user_constraints c
     WHERE c.table_name='TRANSACTION_DB' AND c.constraint_type IN ('U','P')
     AND (SELECT COUNT(*) FROM user_cons_columns x WHERE x.constraint_name=c.constraint_name)=1
     AND EXISTS(SELECT 1 FROM user_cons_columns x WHERE x.constraint_name=c.constraint_name AND x.column_name=col_name);
    IF n=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD CONSTRAINT ' || constraint_name || ' UNIQUE (' || col_name || ')'; END IF;
  END;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name='TRANSACTION_DB';
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20001,'Existing Shreya TRANSACTION_DB required'); END IF;
  add_col('CANCEL_IDEMPOTENCY_KEY','cancel_idempotency_key VARCHAR2(100)');
  add_col('VERIFICATION_IDEMPOTENCY_KEY','verification_idempotency_key VARCHAR2(100)');
  add_col('VERIFIED_AT','verified_at TIMESTAMP');
  add_col('AUTHORIZED_AT','authorized_at TIMESTAMP');
  add_col('RELEASED_AT','released_at TIMESTAMP');
  EXECUTE IMMEDIATE 'ALTER TABLE transaction_db MODIFY (risk_reason VARCHAR2(2000))';
  unique_col('IDEMPOTENCY_KEY','sp_uq_tx_initiate_key');
  unique_col('CANCEL_IDEMPOTENCY_KEY','sp_uq_tx_cancel_key');
  unique_col('VERIFICATION_IDEMPOTENCY_KEY','sp_uq_tx_verify_key');
END;
/
-- Existing numeric columns must remain NUMBER(18,2); inspect instead of automatically converting money.
SELECT column_name,data_type,data_precision,data_scale FROM user_tab_columns
 WHERE table_name='TRANSACTION_DB' AND column_name='AMOUNT';
SELECT column_name,data_type,data_precision,data_scale FROM user_tab_columns
 WHERE table_name='ACCOUNT' AND column_name='BALANCE';

