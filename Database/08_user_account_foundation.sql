-- SafePay Phase 1: User, Role and Account foundation.
-- Run ONCE in SQL Developer after 07_simplified_phase1_schema.sql.
-- 1. Confirm the 07 schema exists, then create ROLES only if needed.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'USERS';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'USERS table is missing. Run the 07 schema first.');
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'ACCOUNT';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'ACCOUNT table is missing. Run the 07 schema first.');
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'USERS' AND column_name = 'PASSWORD';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20003, 'USERS.PASSWORD is missing. This is not the 07 schema.');
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'ROLES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE roles (
                role_id     NUMBER PRIMARY KEY,
                role_name   VARCHAR2(30) NOT NULL UNIQUE,
                description VARCHAR2(255)
            )
        ]';
    ELSE
        EXECUTE IMMEDIATE q'[
            SELECT COUNT(*) FROM roles
             WHERE role_name NOT IN ('CUSTOMER', 'ADMIN')
        ]' INTO v_count;
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20004,
                'ROLES contains values other than CUSTOMER or ADMIN. Review them before running this migration.');
        END IF;
    END IF;
END;
/
-- 2. Seed the two Phase 1 roles. Existing matching rows are left unchanged.
MERGE INTO roles r
USING (
    SELECT 1 role_id, 'CUSTOMER' role_name, 'Retail customer' description FROM dual
    UNION ALL
    SELECT 2, 'ADMIN', 'SafePay administrator' FROM dual
) s
ON (r.role_name = s.role_name)
WHEN NOT MATCHED THEN
    INSERT (role_id, role_name, description)
    VALUES (s.role_id, s.role_name, s.description);

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM roles
     WHERE role_id = 1 AND role_name = 'CUSTOMER';
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20005, 'CUSTOMER must use ROLE_ID 1.');
    END IF;
END;
/
-- 3. Add the role-ID sequence if needed.
DECLARE
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_role_id START WITH 3 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE <> -955 THEN RAISE; END IF; -- -955 means the sequence already exists.
END;
/
-- 4. Add USERS columns; they are nullable first so current rows remain valid.
ALTER TABLE users ADD (
    role_id                NUMBER DEFAULT 1,
    password_hash          VARCHAR2(255),
    status                 VARCHAR2(20) DEFAULT 'ACTIVE',
    failed_login_attempts  NUMBER(3) DEFAULT 0,
    locked_until           TIMESTAMP,
    last_login_at          TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- 5. Backfill existing USERS without changing IDs, hashes, or creation dates.
UPDATE users
   SET password_hash = NVL(password_hash, password),
       role_id = NVL(role_id, 1),
       status = NVL(status, 'ACTIVE'),
       failed_login_attempts = NVL(failed_login_attempts, 0),
       updated_at = NVL(updated_at, created_at)
 WHERE password_hash IS NULL
    OR role_id IS NULL
    OR status IS NULL
    OR failed_login_attempts IS NULL
    OR updated_at IS NULL;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM users
     WHERE password_hash IS NULL
        OR role_id IS NULL
        OR status NOT IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'INACTIVE')
        OR failed_login_attempts < 0;
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20006, 'USERS contains null or invalid foundation data.');
    END IF;
END;
/

ALTER TABLE users MODIFY (
    role_id               NOT NULL,
    password_hash         NOT NULL,
    status                NOT NULL,
    failed_login_attempts NOT NULL,
    updated_at            NOT NULL
);

-- 6. Add ACCOUNT columns. ACCOUNT_ID remains the internal primary key.
ALTER TABLE account ADD (
    account_number VARCHAR2(30),
    account_type   VARCHAR2(20) DEFAULT 'SAVINGS',
    status         VARCHAR2(20) DEFAULT 'ACTIVE',
    updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- 7. Create independent ACCOUNT_NUMBER values.
DECLARE
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_account_number START WITH 500000000001 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE <> -955 THEN RAISE; END IF;
END;
/

-- 8. Backfill existing accounts. A number is generated only when missing.
UPDATE account
   SET account_number = TO_CHAR(seq_account_number.NEXTVAL)
 WHERE account_number IS NULL;

UPDATE account
   SET account_type = NVL(account_type, 'SAVINGS'),
       status = NVL(status, 'ACTIVE'),
       updated_at = NVL(updated_at, created_at)
 WHERE account_type IS NULL
    OR status IS NULL
    OR updated_at IS NULL;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM account
     WHERE account_number IS NULL
        OR account_type NOT IN ('SAVINGS', 'CURRENT')
        OR status NOT IN ('ACTIVE', 'BLOCKED', 'CLOSED')
        OR balance < 0;
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20007, 'ACCOUNT contains null or invalid foundation data.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (SELECT user_id FROM account GROUP BY user_id HAVING COUNT(*) > 1);
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20008, 'A user has more than one account. Resolve this before migration.');
    END IF;
END;
/

ALTER TABLE account MODIFY (
    account_number NOT NULL,
    account_type   NOT NULL,
    status         NOT NULL,
    updated_at     NOT NULL
);

-- 9. Add Phase 1 constraints; existing USER_ID/ACCOUNT_ID values are unchanged.
ALTER TABLE users ADD CONSTRAINT sp_fk_user_role
    FOREIGN KEY (role_id) REFERENCES roles(role_id);
ALTER TABLE users ADD CONSTRAINT sp_ck_user_status
    CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'INACTIVE'));
ALTER TABLE users ADD CONSTRAINT sp_ck_user_failed_logins
    CHECK (failed_login_attempts >= 0);

ALTER TABLE account ADD CONSTRAINT sp_uq_account_number UNIQUE (account_number);
ALTER TABLE account ADD CONSTRAINT sp_ck_account_type
    CHECK (account_type IN ('SAVINGS', 'CURRENT'));
ALTER TABLE account ADD CONSTRAINT sp_ck_account_status
    CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'));

-- 07 already has ACCOUNT.USER_ID FK and UNIQUE constraints.
ALTER TABLE account DROP CONSTRAINT sp_ck_acct_min_bal;
ALTER TABLE account ADD CONSTRAINT sp_ck_account_nonneg_bal CHECK (balance >= 0);

-- No triggers are created. Update the Java User and Account entities next so
-- new records explicitly supply the new required fields.
