-- SafePay: persistent retry key for an explicitly requested simulated admin credit.
-- Additive only: no row, balance, identity, password or transaction decision is rewritten.
-- Apply as schema owner before starting the updated backend with ddl-auto=validate.
-- Oracle DDL commits implicitly. Existing correctly installed objects are retained.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='AUDIT_LOG'
      AND column_name IN ('AUDIT_ID','USER_ID','ACTION','OLD_STATE','NEW_STATE','CREATED_AT');
    IF v_count<>6 THEN RAISE_APPLICATION_ERROR(-20501,'Expected SafePay AUDIT_LOG schema is missing'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='AUDIT_LOG' AND column_name='REQUEST_KEY';
    IF v_count>0 THEN
        SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='AUDIT_LOG' AND column_name='REQUEST_KEY'
          AND data_type='VARCHAR2' AND char_length=100 AND nullable='Y';
        IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20502,'Existing REQUEST_KEY has an unexpected definition'); END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_constraints c WHERE constraint_name='SP_UQ_AUDIT_REQUEST_KEY'
      AND NOT (table_name='AUDIT_LOG' AND constraint_type='U' AND status='ENABLED' AND validated='VALIDATED'
        AND (SELECT LISTAGG(cc.column_name, ',') WITHIN GROUP (ORDER BY cc.position)
               FROM user_cons_columns cc WHERE cc.constraint_name=c.constraint_name)='REQUEST_KEY');
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20503,'Constraint name is already used incompatibly'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='AUDIT_LOG' AND column_name='REQUEST_KEY';
    IF v_count=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE audit_log ADD (request_key VARCHAR2(100))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_constraints c WHERE c.table_name='AUDIT_LOG'
      AND c.constraint_type='U' AND c.status='ENABLED' AND c.validated='VALIDATED'
      AND (SELECT LISTAGG(cc.column_name, ',') WITHIN GROUP (ORDER BY cc.position)
             FROM user_cons_columns cc WHERE cc.constraint_name=c.constraint_name)='REQUEST_KEY';
    IF v_count=0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE audit_log ADD CONSTRAINT sp_uq_audit_request_key UNIQUE (request_key)';
    END IF;
    DBMS_OUTPUT.PUT_LINE('Admin-credit request key is ready. Existing rows remain unchanged.');
END;
/
