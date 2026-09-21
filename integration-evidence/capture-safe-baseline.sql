-- Run with F5 in a NEW, dedicated SQL Developer SAFEPAY_OWNER connection.
-- Do not use a connection with unsaved work. No DDL, DML, grants or migration runs.
-- Save Script Output as baseline-before-v2.txt, then run again after tests as baseline-after-v2.txt.
-- SET TRANSACTION gives the SELECTs one consistent read-only snapshot.
-- Secret/hash/key/raw-body columns and LOB contents are intentionally excluded.
-- V2 emits short hexadecimal chunks, preserving text even when Script Output wraps words.
SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
SET VERIFY OFF
SET TRANSACTION READ ONLY;
DECLARE
  row_cursor SYS_REFCURSOR;
  statement_text VARCHAR2(32767);
  column_text VARCHAR2(30000);
  row_json CLOB;
  row_count NUMBER;
  chunk_count NUMBER;
  chunk_bytes NUMBER;
  chunk_raw RAW(128);
  chunk_offset NUMBER;
  owner_name VARCHAR2(128) := 'SAFEPAY_OWNER';
BEGIN
  DBMS_OUTPUT.PUT_LINE('SAFEPAY_SNAPSHOT_V2|' || TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD"T"HH24:MI:SS.FF6TZH:TZM'));
  FOR t IN (
    SELECT table_name FROM all_tables WHERE owner = owner_name
      AND table_name IN ('APP_ROLE','APP_USER','USER_ROLE','AUTH_SESSION','ACCOUNT','BENEFICIARY',
        'RISK_POLICY','PROTECTION_POLICY','RISK_POLICY_BAND','PAYMENT_TRANSACTION',
        'TRANSACTION_RISK_FACTOR','IDEMPOTENCY_RECORD','PAYMENT_OTP_CHALLENGE','RISK_REVIEW',
        'LEDGER_POSTING','LEDGER_ENTRY','TRANSACTION_EXCEPTION','AUDIT_LOG','APP_NOTIFICATION')
    ORDER BY table_name
  ) LOOP
    column_text := NULL;
    FOR c IN (SELECT column_name, data_type FROM all_tab_columns
              WHERE owner = owner_name AND table_name = t.table_name ORDER BY column_id) LOOP
      IF REGEXP_LIKE(c.column_name, 'PASSWORD_HASH|REFRESH_TOKEN|TOKEN_FAMILY|OTP_HASH|SALT|SECRET|REQUEST_HASH|IDEMPOTENCY_KEY')
         OR c.data_type IN ('CLOB','NCLOB','BLOB','LONG','RAW','LONG RAW') THEN
        DBMS_OUTPUT.PUT_LINE('EXCLUDED|' || t.table_name || '|' || c.column_name);
      ELSE
        IF column_text IS NOT NULL THEN column_text := column_text || ','; END IF;
        column_text := column_text || '''' || c.column_name || ''' VALUE ';
        IF c.data_type = 'NUMBER' THEN
          column_text := column_text || 'TO_CHAR("' || c.column_name || '",''TM9'',''NLS_NUMERIC_CHARACTERS=''''.,'''''')';
        ELSIF c.data_type LIKE 'TIMESTAMP%WITH TIME ZONE' THEN
          column_text := column_text || 'TO_CHAR("' || c.column_name || '",''YYYY-MM-DD"T"HH24:MI:SS.FF6TZH:TZM'')';
        ELSIF c.data_type LIKE 'TIMESTAMP%' THEN
          column_text := column_text || 'TO_CHAR("' || c.column_name || '",''YYYY-MM-DD"T"HH24:MI:SS.FF6'')';
        ELSIF c.data_type = 'DATE' THEN
          column_text := column_text || 'TO_CHAR("' || c.column_name || '",''YYYY-MM-DD"T"HH24:MI:SS'')';
        ELSE column_text := column_text || '"' || c.column_name || '"';
        END IF;
      END IF;
    END LOOP;
    statement_text := 'SELECT JSON_OBJECT(' || column_text || ' NULL ON NULL RETURNING CLOB) FROM "' || owner_name || '"."' || t.table_name || '"';
    row_count := 0;
    OPEN row_cursor FOR statement_text;
    LOOP
      FETCH row_cursor INTO row_json;
      EXIT WHEN row_cursor%NOTFOUND;
      IF DBMS_LOB.GETLENGTH(row_json) > 16000 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Snapshot row too large: ' || t.table_name);
      END IF;
      chunk_count := 0;
      chunk_bytes := 0;
      chunk_offset := 1;
      WHILE chunk_offset <= DBMS_LOB.GETLENGTH(row_json) LOOP
        chunk_count := chunk_count + 1;
        chunk_raw := UTL_I18N.STRING_TO_RAW(DBMS_LOB.SUBSTR(row_json,24,chunk_offset),'AL32UTF8');
        chunk_bytes := chunk_bytes + UTL_RAW.LENGTH(chunk_raw);
        DBMS_OUTPUT.PUT_LINE('ROWHEX|' || t.table_name || '|' || chunk_count || '|' || RAWTOHEX(chunk_raw));
        chunk_offset := chunk_offset + 24;
      END LOOP;
      DBMS_OUTPUT.PUT_LINE('ROWEND|' || t.table_name || '|' || chunk_count || '|' || chunk_bytes);
      row_count := row_count + 1;
    END LOOP;
    CLOSE row_cursor;
    DBMS_OUTPUT.PUT_LINE('COUNT|' || t.table_name || '|' || row_count);
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('SAFEPAY_SNAPSHOT_END');
END;
/
ROLLBACK;
