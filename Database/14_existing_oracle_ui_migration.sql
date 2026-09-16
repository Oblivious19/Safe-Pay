-- SafePay: migrate the inspected legacy Oracle schema without losing its data.
-- Approved scope: retain 3 users and all 6 accounts, including 4 owned by user 103.
-- Run as the schema owner with ALL application writers/schedulers stopped.
-- This is a ONE-TIME migration for the inspected 2026-09-16 schema, not a
-- general-purpose upgrade. The first PL/SQL block is a standalone READ-ONLY
-- preflight and must succeed before running the remaining blocks.
--
-- Oracle DDL commits implicitly. ROLLBACK CANNOT undo this migration. Five CTAS
-- backups retain every original column/value, but not constraints/indexes/grants.
-- Keep an independent database backup too. If any statement fails after the
-- first CREATE TABLE, stop and inspect; never rerun or delete the backup tables.
-- All existing users become CUSTOMER; no password or administrator is invented.
-- Existing IDs, balances, timestamps, passwords and transaction decisions stay
-- unchanged. PASSWORD is retained but made nullable for new registrations.
-- New account numbers are assigned in account_id order using a new sequence.
-- Multiple accounts per user are intentional; do not add UNIQUE(account.user_id).
-- Execute 12_admin_reporting_views.sql afterwards, then start with ddl-auto=validate.
SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

-- BLOCK 1: standalone read-only preflight. No NEXTVAL, DML or DDL in this block.
DECLARE
  v_n NUMBER;
  PROCEDURE require_count(p_sql VARCHAR2, p_expected NUMBER, p_message VARCHAR2) IS
    v_actual NUMBER;
  BEGIN
    EXECUTE IMMEDIATE p_sql INTO v_actual;
    IF v_actual <> p_expected THEN
      RAISE_APPLICATION_ERROR(-20401, p_message || ' (expected ' || p_expected || ', found ' || v_actual || ')');
    END IF;
  END;
  PROCEDURE require_column(p_table VARCHAR2, p_column VARCHAR2, p_type VARCHAR2,
      p_length NUMBER DEFAULT NULL, p_precision NUMBER DEFAULT NULL,
      p_scale NUMBER DEFAULT NULL, p_nullable VARCHAR2 DEFAULT NULL) IS
    v_actual NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_actual FROM user_tab_columns
     WHERE table_name = p_table AND column_name = p_column AND data_type = p_type
       AND (p_length IS NULL OR char_length = p_length)
       AND (p_precision IS NULL OR data_precision = p_precision)
       AND (p_scale IS NULL OR data_scale = p_scale)
       AND (p_nullable IS NULL OR nullable = p_nullable);
    IF v_actual <> 1 THEN
      RAISE_APPLICATION_ERROR(-20402, 'Unexpected/missing legacy column: ' || p_table || '.' || p_column);
    END IF;
  END;
  PROCEDURE require_key(p_table VARCHAR2, p_columns VARCHAR2, p_type VARCHAR2) IS
    v_actual NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_actual FROM user_constraints c
     WHERE c.table_name = p_table AND c.constraint_type = p_type
       AND c.status = 'ENABLED' AND c.validated = 'VALIDATED'
       AND (SELECT LISTAGG(x.column_name, ',') WITHIN GROUP (ORDER BY x.position)
              FROM user_cons_columns x WHERE x.constraint_name = c.constraint_name) = p_columns;
    IF v_actual < 1 THEN
      RAISE_APPLICATION_ERROR(-20403, 'Expected validated key missing: ' || p_table || '(' || p_columns || ')');
    END IF;
  END;
  PROCEDURE require_check(p_name VARCHAR2, p_expression VARCHAR2) IS
    v_actual NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_actual FROM user_constraints
     WHERE table_name = 'TRANSACTION_DB' AND constraint_name = p_name
       AND constraint_type = 'C' AND status = 'ENABLED' AND validated = 'VALIDATED'
       AND REGEXP_REPLACE(UPPER(search_condition_vc), '[[:space:]"]', '') =
           REGEXP_REPLACE(UPPER(p_expression), '[[:space:]"]', '');
    IF v_actual <> 1 THEN
      RAISE_APPLICATION_ERROR(-20404, 'Legacy risk constraint differs; review before replacing: ' || p_name);
    END IF;
  END;
  PROCEDURE require_fk(p_table VARCHAR2, p_column VARCHAR2, p_parent VARCHAR2, p_parent_column VARCHAR2) IS
    v_actual NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_actual FROM user_constraints c JOIN user_constraints p
      ON p.constraint_name=c.r_constraint_name AND c.r_owner=USER
     WHERE c.table_name=p_table AND c.constraint_type='R' AND p.table_name=p_parent
       AND c.status='ENABLED' AND c.validated='VALIDATED'
       AND (SELECT LISTAGG(x.column_name, ',') WITHIN GROUP (ORDER BY x.position)
              FROM user_cons_columns x WHERE x.constraint_name=c.constraint_name)=p_column
       AND (SELECT LISTAGG(x.column_name, ',') WITHIN GROUP (ORDER BY x.position)
              FROM user_cons_columns x WHERE x.constraint_name=p.constraint_name)=p_parent_column;
    IF v_actual<1 THEN RAISE_APPLICATION_ERROR(-20413,'Expected foreign key missing: ' || p_table || '.' || p_column); END IF;
  END;
  PROCEDURE require_sequence(p_sequence VARCHAR2, p_table VARCHAR2, p_id VARCHAR2) IS
    v_actual NUMBER;
    v_max NUMBER;
  BEGIN
    EXECUTE IMMEDIATE 'SELECT NVL(MAX(' || p_id || '),0) FROM ' || p_table INTO v_max;
    SELECT COUNT(*) INTO v_actual FROM user_sequences
     WHERE sequence_name = p_sequence AND increment_by = 1 AND cycle_flag = 'N'
       AND cache_size = 0 AND last_number > v_max;
    IF v_actual <> 1 THEN
      RAISE_APPLICATION_ERROR(-20405, 'Sequence requires review (no automatic reset): ' || p_sequence);
    END IF;
  END;
BEGIN
  require_count(q'[SELECT COUNT(*) FROM user_tables WHERE table_name IN
    ('USERS','ACCOUNT','BENEFICIARIES','TRANSACTION_DB','AUDIT_LOG')]', 5,
    'The five inspected legacy tables are required');
  require_count(q'[SELECT COUNT(*) FROM user_objects WHERE object_name IN
    ('SPBK_20260916_USERS','SPBK_20260916_ACCOUNT','SPBK_20260916_BENEFICIARIES',
     'SPBK_20260916_TRANSACTION_DB','SPBK_20260916_AUDIT_LOG','ROLES','SEQ_ROLE_ID',
     'SEQ_ACCOUNT_NUMBER','VW_SP_TX_REPORT_TOTALS','VW_SP_TX_REPORT_DAILY',
     'IDX_SP14_EXPIRY','IDX_SP14_ACCOUNT_STATE','IDX_SP14_TX_HISTORY')]', 0,
    'A backup, new schema object or report view already exists; review instead of overwriting');
  require_count(q'[SELECT COUNT(*) FROM user_constraints WHERE constraint_name IN
    ('SP_PK_ROLE','SP_UQ_ROLE_NAME','SP_FK_USER_ROLE','SP_CK_USER_STATUS',
     'SP_CK_USER_LOGIN_ATTEMPTS','SP_UQ_ACCOUNT_NUMBER','SP_CK_ACCT_TYPE',
     'SP_CK_ACCT_STATUS','SP_UQ_TX_CANCEL_KEY','SP_UQ_TX_VERIFY_KEY','SP_CK_TX_RISK_POLICY')]', 0,
    'A new constraint name is already used; inspect the schema');
  require_count('SELECT COUNT(*) FROM users', 3, 'Users changed since inspection');
  require_count('SELECT COUNT(*) FROM account', 6, 'Accounts changed since inspection');
  require_count('SELECT COUNT(*) FROM beneficiaries', 2, 'Beneficiaries changed since inspection');
  require_count('SELECT COUNT(*) FROM transaction_db', 4, 'Transactions changed since inspection');
  require_count('SELECT COUNT(*) FROM audit_log', 7, 'Audit rows changed since inspection');
  require_count('SELECT COUNT(*) FROM account WHERE user_id = 101', 1, 'User 101 account count changed');
  require_count('SELECT COUNT(*) FROM account WHERE user_id = 102', 1, 'User 102 account count changed');
  require_count('SELECT COUNT(*) FROM account WHERE user_id = 103', 4, 'User 103 account count changed');
  require_count(q'[SELECT COUNT(*) FROM user_tab_columns WHERE table_name='USERS']', 6, 'USERS layout changed');
  require_count(q'[SELECT COUNT(*) FROM user_tab_columns WHERE table_name='ACCOUNT']', 4, 'ACCOUNT layout changed');
  require_count(q'[SELECT COUNT(*) FROM user_tab_columns WHERE table_name='BENEFICIARIES']', 7, 'BENEFICIARIES layout changed');
  require_count(q'[SELECT COUNT(*) FROM user_tab_columns WHERE table_name='TRANSACTION_DB']', 20, 'TRANSACTION_DB layout changed');
  require_count(q'[SELECT COUNT(*) FROM user_tab_columns WHERE table_name='AUDIT_LOG']', 7, 'AUDIT_LOG layout changed');

  require_column('USERS','USER_ID','NUMBER',p_nullable=>'N');
  require_column('USERS','NAME','VARCHAR2',100,p_nullable=>'N');
  require_column('USERS','EMAIL','VARCHAR2',150,p_nullable=>'N');
  require_column('USERS','PHONE','VARCHAR2',10,p_nullable=>'N');
  require_column('USERS','PASSWORD','VARCHAR2',255,p_nullable=>'N');
  require_column('USERS','CREATED_AT','TIMESTAMP(6)',p_nullable=>'N');
  require_column('ACCOUNT','ACCOUNT_ID','NUMBER',p_nullable=>'N');
  require_column('ACCOUNT','USER_ID','NUMBER',p_nullable=>'N');
  require_column('ACCOUNT','BALANCE','NUMBER',p_precision=>18,p_scale=>2,p_nullable=>'N');
  require_column('ACCOUNT','CREATED_AT','TIMESTAMP(6)',p_nullable=>'N');
  require_column('BENEFICIARIES','BENEFICIARY_ID','NUMBER',p_nullable=>'N');
  require_column('BENEFICIARIES','ACCOUNT_ID','NUMBER',p_nullable=>'N');
  require_column('BENEFICIARIES','BENEFICIARY_NAME','VARCHAR2',100,p_nullable=>'N');
  require_column('BENEFICIARIES','BANK_ACCOUNT_NUMBER','VARCHAR2',30,p_nullable=>'N');
  require_column('BENEFICIARIES','IFSC','VARCHAR2',20,p_nullable=>'N');
  require_column('BENEFICIARIES','STATUS','VARCHAR2',10,p_nullable=>'N');
  require_column('BENEFICIARIES','CREATED_AT','TIMESTAMP(6)',p_nullable=>'N');
  require_column('TRANSACTION_DB','TRANSACTION_ID','NUMBER',p_nullable=>'N');
  require_column('TRANSACTION_DB','TRANSACTION_REF','VARCHAR2',50,p_nullable=>'N');
  require_column('TRANSACTION_DB','IDEMPOTENCY_KEY','VARCHAR2',100,p_nullable=>'N');
  require_column('TRANSACTION_DB','FROM_ACCOUNT_ID','NUMBER',p_nullable=>'N');
  require_column('TRANSACTION_DB','BENEFICIARY_ID','NUMBER',p_nullable=>'N');
  require_column('TRANSACTION_DB','AMOUNT','NUMBER',p_precision=>18,p_scale=>2,p_nullable=>'N');
  require_column('TRANSACTION_DB','PURPOSE','VARCHAR2',255,p_nullable=>'Y');
  require_column('TRANSACTION_DB','STATE','VARCHAR2',20,p_nullable=>'N');
  require_column('TRANSACTION_DB','RISK_TIER','VARCHAR2',15,p_nullable=>'N');
  require_column('TRANSACTION_DB','PROTECTION_SECONDS','NUMBER',p_nullable=>'N');
  require_column('TRANSACTION_DB','AUTHENTICATION_REQUIRED','CHAR',1,p_nullable=>'N');
  require_column('TRANSACTION_DB','RISK_REASON','VARCHAR2',500,p_nullable=>'N');
  require_column('TRANSACTION_DB','PROTECTION_EXPIRES_AT','TIMESTAMP(6)',p_nullable=>'Y');
  require_column('TRANSACTION_DB','VERSION','NUMBER',p_nullable=>'N');
  require_column('TRANSACTION_DB','CREATED_AT','TIMESTAMP(6)',p_nullable=>'N');
  require_column('TRANSACTION_DB','SETTLED_AT','TIMESTAMP(6)',p_nullable=>'Y');
  require_column('TRANSACTION_DB','CANCELLED_AT','TIMESTAMP(6)',p_nullable=>'Y');
  require_column('TRANSACTION_DB','AUTHORIZED_AT','TIMESTAMP(6)',p_nullable=>'Y');
  require_column('TRANSACTION_DB','CANCEL_IDEMPOTENCY_KEY','VARCHAR2',100,p_nullable=>'Y');
  require_column('TRANSACTION_DB','RELEASED_AT','TIMESTAMP(6)',p_nullable=>'Y');
  require_column('AUDIT_LOG','AUDIT_ID','NUMBER',p_nullable=>'N');
  require_column('AUDIT_LOG','TRANSACTION_ID','NUMBER',p_nullable=>'Y');
  require_column('AUDIT_LOG','USER_ID','NUMBER',p_nullable=>'Y');
  require_column('AUDIT_LOG','ACTION','VARCHAR2',255,p_nullable=>'N');
  require_column('AUDIT_LOG','OLD_STATE','VARCHAR2',255,p_nullable=>'Y');
  require_column('AUDIT_LOG','NEW_STATE','VARCHAR2',255,p_nullable=>'Y');
  require_column('AUDIT_LOG','CREATED_AT','TIMESTAMP(6)',p_nullable=>'N');
  require_count(q'[SELECT COUNT(*) FROM user_constraints WHERE table_name IN
    ('USERS','ACCOUNT','BENEFICIARIES','TRANSACTION_DB','AUDIT_LOG')
    AND (status<>'ENABLED' OR validated<>'VALIDATED')]', 0, 'Legacy constraints must be enabled and validated');
  require_count(q'[SELECT COUNT(*) FROM user_triggers WHERE table_name IN
    ('USERS','ACCOUNT','BENEFICIARIES','TRANSACTION_DB','AUDIT_LOG') AND status='ENABLED']', 0,
    'An enabled trigger needs review before migration');
  require_key('USERS','USER_ID','P');
  require_key('USERS','EMAIL','U');
  require_key('USERS','PHONE','U');
  require_key('ACCOUNT','ACCOUNT_ID','P');
  require_key('BENEFICIARIES','BENEFICIARY_ID','P');
  require_key('BENEFICIARIES','ACCOUNT_ID,BANK_ACCOUNT_NUMBER,IFSC','U');
  require_key('TRANSACTION_DB','TRANSACTION_ID','P');
  require_key('TRANSACTION_DB','TRANSACTION_REF','U');
  require_key('TRANSACTION_DB','IDEMPOTENCY_KEY','U');
  require_key('AUDIT_LOG','AUDIT_ID','P');
  require_fk('ACCOUNT','USER_ID','USERS','USER_ID');
  require_fk('BENEFICIARIES','ACCOUNT_ID','ACCOUNT','ACCOUNT_ID');
  require_fk('TRANSACTION_DB','FROM_ACCOUNT_ID','ACCOUNT','ACCOUNT_ID');
  require_fk('TRANSACTION_DB','BENEFICIARY_ID','BENEFICIARIES','BENEFICIARY_ID');
  require_fk('AUDIT_LOG','TRANSACTION_ID','TRANSACTION_DB','TRANSACTION_ID');
  require_fk('AUDIT_LOG','USER_ID','USERS','USER_ID');
  require_count(q'[SELECT COUNT(*) FROM user_constraints c WHERE c.table_name='ACCOUNT'
    AND c.constraint_type IN ('P','U') AND (SELECT LISTAGG(x.column_name, ',')
    WITHIN GROUP (ORDER BY x.position) FROM user_cons_columns x
    WHERE x.constraint_name=c.constraint_name)='USER_ID']', 0,
    'ACCOUNT.USER_ID must not have a single-account uniqueness constraint');

  require_count(q'[SELECT COUNT(*) FROM users WHERE NOT REGEXP_LIKE(password,
    '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$','c')
    OR TO_NUMBER(SUBSTR(password,5,2) DEFAULT 0 ON CONVERSION ERROR) NOT BETWEEN 4 AND 31]', 0,
    'Every legacy password must be a supported 60-character bcrypt hash');
  require_count(q'[SELECT COUNT(*) FROM (SELECT LOWER(TRIM(email)) FROM users
    GROUP BY LOWER(TRIM(email)) HAVING COUNT(*)>1)]', 0, 'Case-insensitive duplicate email');
  -- Preserve the original case; the current login uses exact email matching.
  require_count(q'[SELECT COUNT(*) FROM users WHERE email<>TRIM(email)
    OR TRIM(email) IS NULL OR NOT REGEXP_LIKE(phone,'^[0-9]{10}$','c')]', 0,
    'Legacy email whitespace/phone format needs explicit review');
  require_count(q'[SELECT COUNT(*) FROM (SELECT phone FROM users GROUP BY phone HAVING COUNT(*)>1)]', 0,
    'Duplicate phone');
  require_count('SELECT COUNT(*) FROM account WHERE balance < 5000 OR balance IS NULL', 0,
    'Legacy account balance violates the retained minimum');
  require_count(q'[SELECT COUNT(*) FROM transaction_db WHERE state<>'SETTLED' OR amount<=0
    OR amount IS NULL OR settled_at IS NULL OR TRIM(risk_reason) IS NULL]', 0,
    'Transactions changed or contain unsupported pending/history values');
  require_count(q'[SELECT COUNT(*) FROM (SELECT account_id,bank_account_number,ifsc FROM beneficiaries
    GROUP BY account_id,bank_account_number,ifsc HAVING COUNT(*)>1)]', 0, 'Duplicate beneficiary key');
  require_count(q'[SELECT COUNT(*) FROM beneficiaries WHERE status NOT IN ('ACTIVE','INACTIVE')]', 0,
    'Unsupported beneficiary status');
  require_count(q'[SELECT COUNT(*) FROM (SELECT cancel_idempotency_key FROM transaction_db
    WHERE cancel_idempotency_key IS NOT NULL GROUP BY cancel_idempotency_key HAVING COUNT(*)>1)]', 0,
    'Duplicate cancellation key');
  require_count('SELECT COUNT(*) FROM account a WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.user_id=a.user_id)', 0,
    'Orphaned account');
  require_count('SELECT COUNT(*) FROM beneficiaries b WHERE NOT EXISTS (SELECT 1 FROM account a WHERE a.account_id=b.account_id)', 0,
    'Orphaned beneficiary');
  require_count('SELECT COUNT(*) FROM transaction_db t WHERE NOT EXISTS (SELECT 1 FROM account a WHERE a.account_id=t.from_account_id)', 0,
    'Orphaned transaction account');
  require_count('SELECT COUNT(*) FROM transaction_db t WHERE NOT EXISTS (SELECT 1 FROM beneficiaries b WHERE b.beneficiary_id=t.beneficiary_id AND b.account_id=t.from_account_id)', 0,
    'Transaction beneficiary belongs to a different account or is missing');
  require_count('SELECT COUNT(*) FROM audit_log l WHERE l.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.user_id=l.user_id)', 0,
    'Orphaned audit user');
  require_count('SELECT COUNT(*) FROM audit_log l WHERE l.transaction_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM transaction_db t WHERE t.transaction_id=l.transaction_id)', 0,
    'Orphaned audit transaction');
  require_check('SP_CK_TX_TIER', q'[risk_tier IN ('LOW','MEDIUM','HIGH','HARD_HOLD')]');
  require_check('SP_CK_TX_RISK_RULE', q'[
    (amount <= 10000.00 AND risk_tier = 'LOW' AND protection_seconds = 0 AND authentication_required = 'N')
    OR (amount > 10000.00 AND amount <= 50000.00 AND risk_tier = 'MEDIUM' AND protection_seconds = 10 AND authentication_required = 'N')
    OR (amount > 50000.00 AND amount <= 100000.00 AND risk_tier = 'HIGH' AND protection_seconds = 60 AND authentication_required = 'N')
    OR (amount > 100000.00 AND risk_tier = 'HARD_HOLD' AND protection_seconds = 0 AND authentication_required = 'Y')
  ]');
  require_sequence('SEQ_USER_ID','USERS','USER_ID');
  require_sequence('SEQ_ACCOUNT_ID','ACCOUNT','ACCOUNT_ID');
  require_sequence('SEQ_BENEFICIARY_ID','BENEFICIARIES','BENEFICIARY_ID');
  require_sequence('SEQ_TRANSACTION_ID','TRANSACTION_DB','TRANSACTION_ID');
  require_sequence('SEQ_AUDIT_ID','AUDIT_LOG','AUDIT_ID');
  SELECT COUNT(*) INTO v_n FROM session_privs WHERE privilege IN ('CREATE TABLE','CREATE SEQUENCE','CREATE VIEW');
  IF v_n<>3 THEN RAISE_APPLICATION_ERROR(-20406, 'Owner needs CREATE TABLE, CREATE SEQUENCE and CREATE VIEW privileges'); END IF;
  DBMS_OUTPUT.PUT_LINE('PRECHECK PASSED: 3 users, 6 accounts, 2 beneficiaries, 4 settled transactions, 7 audit rows. No data changed.');
END;
/

-- BLOCK 2: one-time changes. Never run this block without the successful preflight.
DECLARE
  v_n NUMBER;
  PROCEDURE add_tx_column(p_column VARCHAR2, p_definition VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_tab_columns WHERE table_name='TRANSACTION_DB' AND column_name=p_column;
    IF v_n=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD (' || p_definition || ')'; END IF;
  END;
  PROCEDURE ensure_tx_unique(p_column VARCHAR2, p_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_constraints c
     WHERE c.table_name='TRANSACTION_DB' AND c.constraint_type IN ('P','U')
       AND (SELECT LISTAGG(x.column_name, ',') WITHIN GROUP (ORDER BY x.position)
              FROM user_cons_columns x WHERE x.constraint_name=c.constraint_name)=p_column;
    IF v_n=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE transaction_db ADD CONSTRAINT ' || p_name || ' UNIQUE (' || p_column || ')'; END IF;
  END;
  PROCEDURE ensure_tx_index(p_columns VARCHAR2, p_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_indexes i WHERE i.table_name='TRANSACTION_DB'
      AND i.status='VALID' AND (SELECT LISTAGG(x.column_name, ',')
        WITHIN GROUP (ORDER BY x.column_position) FROM user_ind_columns x
        WHERE x.index_name=i.index_name)=p_columns;
    IF v_n=0 THEN EXECUTE IMMEDIATE 'CREATE INDEX ' || p_name || ' ON transaction_db(' || p_columns || ')'; END IF;
  END;
BEGIN
  -- Protect against accidental execution after any previous/partial attempt.
  SELECT COUNT(*) INTO v_n FROM user_objects WHERE object_name IN
    ('SPBK_20260916_USERS','SPBK_20260916_ACCOUNT','SPBK_20260916_BENEFICIARIES',
     'SPBK_20260916_TRANSACTION_DB','SPBK_20260916_AUDIT_LOG','ROLES','SEQ_ROLE_ID',
     'SEQ_ACCOUNT_NUMBER','VW_SP_TX_REPORT_TOTALS','VW_SP_TX_REPORT_DAILY');
  IF v_n<>0 THEN RAISE_APPLICATION_ERROR(-20407, 'Backup/new objects already exist; stop and inspect'); END IF;
  EXECUTE IMMEDIATE 'CREATE TABLE SPBK_20260916_USERS AS SELECT * FROM users';
  EXECUTE IMMEDIATE 'CREATE TABLE SPBK_20260916_ACCOUNT AS SELECT * FROM account';
  EXECUTE IMMEDIATE 'CREATE TABLE SPBK_20260916_BENEFICIARIES AS SELECT * FROM beneficiaries';
  EXECUTE IMMEDIATE 'CREATE TABLE SPBK_20260916_TRANSACTION_DB AS SELECT * FROM transaction_db';
  EXECUTE IMMEDIATE 'CREATE TABLE SPBK_20260916_AUDIT_LOG AS SELECT * FROM audit_log';

  EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_role_id START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
  EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_account_number START WITH 500000000001 INCREMENT BY 1 NOCACHE NOCYCLE';
  EXECUTE IMMEDIATE 'CREATE TABLE roles (role_id NUMBER(19,0) CONSTRAINT sp_pk_role PRIMARY KEY,
    role_name VARCHAR2(30) NOT NULL CONSTRAINT sp_uq_role_name UNIQUE, description VARCHAR2(255))';
  EXECUTE IMMEDIATE q'[INSERT INTO roles(role_id,role_name,description)
    VALUES(seq_role_id.NEXTVAL,'CUSTOMER','Retail customer')]';
  EXECUTE IMMEDIATE q'[INSERT INTO roles(role_id,role_name,description)
    VALUES(seq_role_id.NEXTVAL,'ADMIN','Administration')]';

  EXECUTE IMMEDIATE q'[ALTER TABLE users ADD (
    role_id NUMBER(19,0), password_hash VARCHAR2(255), status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    failed_login_attempts NUMBER(10,0) DEFAULT 0 NOT NULL, locked_until TIMESTAMP,
    last_login_at TIMESTAMP, updated_at TIMESTAMP)]';
  EXECUTE IMMEDIATE q'[UPDATE users SET role_id=(SELECT role_id FROM roles WHERE role_name='CUSTOMER'),
    password_hash=password, updated_at=created_at]';
  EXECUTE IMMEDIATE 'ALTER TABLE users MODIFY (role_id NOT NULL, password_hash NOT NULL,
    updated_at DEFAULT LOCALTIMESTAMP NOT NULL, password NULL)';
  EXECUTE IMMEDIATE 'ALTER TABLE users ADD CONSTRAINT sp_fk_user_role FOREIGN KEY (role_id) REFERENCES roles(role_id)';
  EXECUTE IMMEDIATE q'[ALTER TABLE users ADD CONSTRAINT sp_ck_user_status CHECK (status IN ('ACTIVE','LOCKED','SUSPENDED','INACTIVE'))]';
  EXECUTE IMMEDIATE 'ALTER TABLE users ADD CONSTRAINT sp_ck_user_login_attempts CHECK (failed_login_attempts>=0)';

  EXECUTE IMMEDIATE q'[ALTER TABLE account ADD (account_number VARCHAR2(30),
    account_type VARCHAR2(20) DEFAULT 'SAVINGS' NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL, updated_at TIMESTAMP)]';
  FOR r IN (SELECT account_id FROM account ORDER BY account_id) LOOP
    EXECUTE IMMEDIATE 'UPDATE account SET account_number=TO_CHAR(seq_account_number.NEXTVAL),
      updated_at=created_at WHERE account_id=:account_id' USING r.account_id;
  END LOOP;
  EXECUTE IMMEDIATE 'ALTER TABLE account MODIFY (account_number NOT NULL, updated_at DEFAULT LOCALTIMESTAMP NOT NULL)';
  EXECUTE IMMEDIATE 'ALTER TABLE account ADD CONSTRAINT sp_uq_account_number UNIQUE (account_number)';
  EXECUTE IMMEDIATE q'[ALTER TABLE account ADD CONSTRAINT sp_ck_acct_type CHECK (account_type IN ('SAVINGS','CURRENT'))]';
  EXECUTE IMMEDIATE q'[ALTER TABLE account ADD CONSTRAINT sp_ck_acct_status CHECK (status IN ('ACTIVE','BLOCKED','CLOSED'))]';
  -- Existing account->user and beneficiary composite keys remain untouched.

  add_tx_column('CANCEL_IDEMPOTENCY_KEY','cancel_idempotency_key VARCHAR2(100)');
  add_tx_column('VERIFICATION_IDEMPOTENCY_KEY','verification_idempotency_key VARCHAR2(100)');
  add_tx_column('VERIFIED_AT','verified_at TIMESTAMP');
  add_tx_column('AUTHORIZED_AT','authorized_at TIMESTAMP');
  add_tx_column('RELEASED_AT','released_at TIMESTAMP');
  EXECUTE IMMEDIATE 'ALTER TABLE transaction_db MODIFY (risk_reason VARCHAR2(2000))';
  ensure_tx_unique('CANCEL_IDEMPOTENCY_KEY','sp_uq_tx_cancel_key');
  ensure_tx_unique('VERIFICATION_IDEMPOTENCY_KEY','sp_uq_tx_verify_key');
  -- Only these two precisely checked legacy constraints are replaced.
  -- Persisted history is never rescored. Java enforces current amount bands;
  -- this database policy also permits the legacy HARD_HOLD tier spelling.
  EXECUTE IMMEDIATE 'ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_risk_rule';
  EXECUTE IMMEDIATE 'ALTER TABLE transaction_db DROP CONSTRAINT sp_ck_tx_tier';
  EXECUTE IMMEDIATE q'[ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_tier
    CHECK (risk_tier IN ('LOW','MEDIUM','HIGH','VERY_HIGH','HARD_HOLD'))]';
  EXECUTE IMMEDIATE q'[ALTER TABLE transaction_db ADD CONSTRAINT sp_ck_tx_risk_policy CHECK (
    (risk_tier='LOW' AND protection_seconds=0 AND authentication_required='N') OR
    (risk_tier='MEDIUM' AND protection_seconds=10 AND authentication_required='N') OR
    (risk_tier='HIGH' AND protection_seconds=60 AND authentication_required='N') OR
    (risk_tier IN ('VERY_HIGH','HARD_HOLD') AND protection_seconds=0
      AND authentication_required='Y' AND protection_expires_at IS NULL))]';
  -- Do not replace SP_CK_TX_STATE; the live seven-state model is unchanged.
  ensure_tx_index('STATE,PROTECTION_EXPIRES_AT','idx_sp14_expiry');
  ensure_tx_index('FROM_ACCOUNT_ID,STATE','idx_sp14_account_state');
  ensure_tx_index('FROM_ACCOUNT_ID,SETTLED_AT','idx_sp14_tx_history');
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Migration applied. Run preservation checks below before starting the app.');
END;
/

-- BLOCK 3: preservation checks. No private field values are printed.
-- The bidirectional MINUS checks EVERY original column, not only counts/sums.
-- Original IDs are primary keys, so row-count plus set equality proves rows kept.
DECLARE
  v_columns VARCHAR2(32767);
  v_difference NUMBER;
  v_source_count NUMBER;
  v_backup_count NUMBER;
  PROCEDURE assert_zero(p_sql VARCHAR2, p_message VARCHAR2) IS
    v_actual NUMBER;
  BEGIN
    EXECUTE IMMEDIATE p_sql INTO v_actual;
    IF v_actual<>0 THEN RAISE_APPLICATION_ERROR(-20408,p_message); END IF;
  END;
BEGIN
  FOR r IN (SELECT 'USERS' table_name FROM dual UNION ALL SELECT 'ACCOUNT' FROM dual
    UNION ALL SELECT 'BENEFICIARIES' FROM dual UNION ALL SELECT 'TRANSACTION_DB' FROM dual
    UNION ALL SELECT 'AUDIT_LOG' FROM dual) LOOP
    SELECT LISTAGG('"' || column_name || '"', ',') WITHIN GROUP (ORDER BY column_id)
      INTO v_columns FROM user_tab_columns WHERE table_name='SPBK_20260916_' || r.table_name;
    IF v_columns IS NULL THEN RAISE_APPLICATION_ERROR(-20409,'Missing backup for ' || r.table_name); END IF;
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || r.table_name INTO v_source_count;
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM SPBK_20260916_' || r.table_name INTO v_backup_count;
    IF v_source_count<>v_backup_count THEN RAISE_APPLICATION_ERROR(-20410,'Row count changed: ' || r.table_name); END IF;
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT ' || v_columns || ' FROM ' || r.table_name ||
      ' MINUS SELECT ' || v_columns || ' FROM SPBK_20260916_' || r.table_name || ')' INTO v_difference;
    IF v_difference<>0 THEN RAISE_APPLICATION_ERROR(-20411,'Original values changed: ' || r.table_name); END IF;
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT ' || v_columns || ' FROM SPBK_20260916_' || r.table_name ||
      ' MINUS SELECT ' || v_columns || ' FROM ' || r.table_name || ')' INTO v_difference;
    IF v_difference<>0 THEN RAISE_APPLICATION_ERROR(-20412,'Original values missing: ' || r.table_name); END IF;
    DBMS_OUTPUT.PUT_LINE(r.table_name || ': ' || v_source_count || ' original rows preserved exactly.');
  END LOOP;
  assert_zero(q'[SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id=u.role_id
    WHERE u.password_hash<>u.password OR u.updated_at<>u.created_at OR r.role_name<>'CUSTOMER'
      OR u.status<>'ACTIVE' OR u.failed_login_attempts<>0]', 'Migrated user fields differ from the approved mapping');
  assert_zero(q'[SELECT COUNT(*) FROM account WHERE updated_at<>created_at
    OR account_type<>'SAVINGS' OR status<>'ACTIVE' OR account_number IS NULL]',
    'Migrated account fields differ from the approved mapping');
  assert_zero(q'[SELECT COUNT(*) FROM user_constraints WHERE table_name IN
    ('USERS','ACCOUNT','BENEFICIARIES','TRANSACTION_DB','AUDIT_LOG','ROLES')
    AND (status<>'ENABLED' OR validated<>'VALIDATED')]', 'A constraint is not enabled and validated');
  DBMS_OUTPUT.PUT_LINE('PRESERVATION PASSED. Run 12_admin_reporting_views.sql; then use the Oracle profile with schema validation.');
END;
/
