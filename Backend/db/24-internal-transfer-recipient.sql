-- Additive Oracle migration. Run once before starting the updated backend.
-- No existing balances or historical settled payments are changed.
DECLARE
  present NUMBER;
BEGIN
  SELECT COUNT(*) INTO present FROM user_tab_columns
    WHERE table_name = 'TRANSACTION_DB' AND column_name = 'TO_ACCOUNT_ID';
  IF present = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD (to_account_id NUMBER(19,0))';
  END IF;
  SELECT COUNT(*) INTO present FROM user_constraints WHERE constraint_name = 'SP_FK_TX_RECEIVER';
  IF present = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD CONSTRAINT sp_fk_tx_receiver FOREIGN KEY (to_account_id) REFERENCES account(account_id)';
  END IF;
  SELECT COUNT(*) INTO present FROM user_indexes WHERE index_name = 'SP_IX_TX_RECEIVER';
  IF present = 0 THEN
    EXECUTE IMMEDIATE 'CREATE INDEX sp_ix_tx_receiver ON transaction_db(to_account_id, state)';
  END IF;
END;
/
