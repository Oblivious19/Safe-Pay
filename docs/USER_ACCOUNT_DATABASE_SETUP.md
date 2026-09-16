# SafePay User and Account database setup after file 7

This guide is for teammates who have already executed `Database/07_simplified_phase1_schema.sql` in their own Oracle schema. Follow it to add the Phase 1 User, Role and Account foundation while preserving existing users, accounts, balances and password hashes.

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

## Before starting

- Do not run file 7 again. Do not run the legacy files 01–06 against this schema.
- Use this guide as the step-by-step execution path. Do not also run the complete `08_user_account_foundation.sql`; that would repeat the same changes. The guide includes the balance-constraint name difference encountered during our manual migration.
- Stop the backend and pause writes during migration. Export a backup of the schema first. Oracle DDL commits automatically, so a later error does not undo earlier successful changes.
- Select only the SQL for the current step in SQL Developer and press F5. Include the final `/` for PL/SQL blocks. Run verification queries separately. Stop at the first error; SQL Developer may otherwise continue to later statements.
- This guide assumes the new User and Account columns have not been added yet. If you already ran part of the migration, inspect the columns and constraints before continuing. Do not rerun an `ALTER TABLE ADD` that already succeeded.
- Existing rows are allowed. An empty table is also allowed; its backfill will update zero rows. Do not create fake accounts or balances merely to make checks pass.

The current Java `User` entity still maps to `USERS.PASSWORD`, and `Account` does not map the new fields. After migration, registration and new account creation require a separate Java update. Leave the backend stopped until that update is ready. Keep `ddl-auto=validate`.

## 1 Confirm the connection and starting schema

```sql
SELECT USER AS connected_schema FROM dual;

SELECT table_name FROM user_tables
WHERE table_name IN ('USERS', 'ACCOUNT', 'ROLES')
ORDER BY table_name;

SELECT table_name, column_name, data_type, nullable
FROM user_tab_columns
WHERE table_name IN ('USERS', 'ACCOUNT')
ORDER BY table_name, column_id;

SELECT 'USERS' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'ACCOUNT', COUNT(*) FROM account;

SELECT account_id, user_id, balance, created_at
FROM account ORDER BY account_id;
```

The original schema has six User columns and four Account columns. Record the row counts and account balances for comparison at the end. Use the schema your backend connects to; do not switch connections between steps. `ROLES` may be absent at this point.

## 2 Create ROLES if missing

```sql
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables
    WHERE table_name = 'ROLES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE roles (
            role_id NUMBER PRIMARY KEY,
            role_name VARCHAR2(30) NOT NULL UNIQUE,
            description VARCHAR2(255)
        )';
    END IF;
END;
/

SELECT role_id, role_name, description FROM roles ORDER BY role_id;
```

`v_count` is a temporary variable holding the table count. An empty result after creating ROLES is expected. If ROLES already contains anything other than CUSTOMER with ID 1 and ADMIN with ID 2, stop and review it with the team. Do not overwrite existing role assignments.

## 3 Insert CUSTOMER and ADMIN

```sql
MERGE INTO roles r
USING (
    SELECT 1 role_id, 'CUSTOMER' role_name,
           'Retail customer' description FROM dual
    UNION ALL
    SELECT 2, 'ADMIN', 'SafePay administrator' FROM dual
) s
ON (r.role_name = s.role_name)
WHEN NOT MATCHED THEN
    INSERT (role_id, role_name, description)
    VALUES (s.role_id, s.role_name, s.description);

COMMIT;

SELECT role_id, role_name, description FROM roles ORDER BY role_id;
```

Expected: exactly two rows, CUSTOMER with ID 1 and ADMIN with ID 2. All current retail users will receive role 1. The ADMIN role row does not create an administrator user or any admin API.

## 4 Create the role sequence

```sql
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences
    WHERE sequence_name = 'SEQ_ROLE_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_role_id
            START WITH 3 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;
END;
/

SELECT sequence_name, increment_by, last_number
FROM user_sequences WHERE sequence_name = 'SEQ_ROLE_ID';
```

A new sequence shows 3 as its next value. Existing sequences are not reset. If an existing sequence could generate an ID already used in ROLES, stop for review. No additional roles are needed for this phase.

## 5 Add the User columns once

```sql
ALTER TABLE users ADD (
    role_id               NUMBER DEFAULT 1,
    password_hash         VARCHAR2(255),
    status                VARCHAR2(20) DEFAULT 'ACTIVE',
    failed_login_attempts NUMBER(3) DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT SYSTIMESTAMP
);

SELECT column_name, data_type, nullable
FROM user_tab_columns WHERE table_name = 'USERS'
ORDER BY column_id;
```

Expected: 13 columns. New columns initially allow nulls so existing rows can be filled before required-field rules are added. The original PASSWORD column remains intact.

## 6 Fill existing User values

```sql
UPDATE users
SET password_hash = NVL(password_hash, password),
    role_id = NVL(role_id, 1),
    status = NVL(status, 'ACTIVE'),
    failed_login_attempts = NVL(failed_login_attempts, 0),
    updated_at = NVL(updated_at, created_at)
WHERE password_hash IS NULL OR role_id IS NULL OR status IS NULL
   OR failed_login_attempts IS NULL OR updated_at IS NULL;

COMMIT;

SELECT user_id, role_id, status, failed_login_attempts,
       CASE WHEN password_hash = password THEN 'COPIED'
            WHEN password_hash IS NULL THEN 'MISSING'
            ELSE 'DIFFERENT' END AS password_copy_status,
       updated_at
FROM users ORDER BY user_id;
```

For existing rows, expect role 1, ACTIVE, 0 attempts and COPIED. Stop if a hash is missing or different; do not reset passwords. The update copies the existing BCrypt value exactly, without rehashing it or exposing it in the verification output. NVL keeps an existing value and supplies a replacement only if it is null.

LOCKED_UNTIL and LAST_LOGIN_AT remain null because there is no login history to migrate. UPDATED_AT normally contains the migration time supplied by its new default; CREATED_AT is preserved. No timestamp synchronization trigger is installed.

## 7 Make User fields mandatory

```sql
ALTER TABLE users MODIFY (
    role_id NOT NULL,
    password_hash NOT NULL,
    status NOT NULL,
    failed_login_attempts NOT NULL,
    updated_at NOT NULL
);

SELECT column_name, nullable FROM user_tab_columns
WHERE table_name = 'USERS'
  AND column_name IN ('ROLE_ID', 'PASSWORD_HASH', 'STATUS',
                      'FAILED_LOGIN_ATTEMPTS', 'UPDATED_AT');
```

All five rows must show N. This means those fields cannot be empty.

## 8 Add the Account columns once

```sql
ALTER TABLE account ADD (
    account_number VARCHAR2(30),
    account_type   VARCHAR2(20) DEFAULT 'SAVINGS',
    status         VARCHAR2(20) DEFAULT 'ACTIVE',
    updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP
);

SELECT column_name, data_type, nullable FROM user_tab_columns
WHERE table_name = 'ACCOUNT' ORDER BY column_id;
```

Expected: eight columns. The physical table stays ACCOUNT, singular. Existing account IDs, balances and creation dates are unchanged.

## 9 Create the independent account number sequence

```sql
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences
    WHERE sequence_name = 'SEQ_ACCOUNT_NUMBER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_account_number
            START WITH 500000000001 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;
END;
/

SELECT sequence_name, increment_by, last_number
FROM user_sequences WHERE sequence_name = 'SEQ_ACCOUNT_NUMBER';
```

A new sequence starts at 500000000001. ACCOUNT_ID still uses SEQ_ACCOUNT_ID as the internal primary key. ACCOUNT_NUMBER uses its own sequence and is not calculated from ACCOUNT_ID. Sequence values can have gaps and need not match across teammates' separate databases.

## 10 Fill existing Account values

```sql
UPDATE account
SET account_number = TO_CHAR(seq_account_number.NEXTVAL)
WHERE account_number IS NULL;

UPDATE account
SET account_type = NVL(account_type, 'SAVINGS'),
    status = NVL(status, 'ACTIVE'),
    updated_at = NVL(updated_at, created_at)
WHERE account_type IS NULL OR status IS NULL OR updated_at IS NULL;

COMMIT;

SELECT account_id, user_id, account_number, account_type,
       balance, status, created_at, updated_at
FROM account ORDER BY account_id;
```

Each account must now have a distinct number, SAVINGS type and ACTIVE status. Compare IDs, balances and CREATED_AT against step 1. Existing non-null account numbers are preserved. The future Java service must obtain SEQ_ACCOUNT_NUMBER.NEXTVAL for each new account; creating the sequence alone does not automatically populate future inserts.

## 11 Make Account fields mandatory

```sql
ALTER TABLE account MODIFY (
    account_number NOT NULL,
    account_type NOT NULL,
    status NOT NULL,
    updated_at NOT NULL
);

SELECT column_name, nullable FROM user_tab_columns
WHERE table_name = 'ACCOUNT'
  AND column_name IN ('ACCOUNT_NUMBER', 'ACCOUNT_TYPE',
                      'STATUS', 'UPDATED_AT');
```

All four rows must show N.

## 12 Add the new constraints once

A constraint is a rule Oracle enforces when data is inserted or changed. These statements check existing rows too. If any statement fails, stop and inspect the error instead of deleting data.

```sql
ALTER TABLE users ADD CONSTRAINT sp_fk_user_role
    FOREIGN KEY (role_id) REFERENCES roles(role_id);
ALTER TABLE users ADD CONSTRAINT sp_ck_user_status
    CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'INACTIVE'));
ALTER TABLE users ADD CONSTRAINT sp_ck_user_failed_logins
    CHECK (failed_login_attempts >= 0);

ALTER TABLE account ADD CONSTRAINT sp_uq_account_number
    UNIQUE (account_number);
ALTER TABLE account ADD CONSTRAINT sp_ck_account_type
    CHECK (account_type IN ('SAVINGS', 'CURRENT'));
ALTER TABLE account ADD CONSTRAINT sp_ck_account_status
    CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'));
ALTER TABLE account ADD CONSTRAINT sp_ck_account_nonneg_bal
    CHECK (balance >= 0);
```

File 7 already defines the User email and phone uniqueness rules, the Account owner foreign key, the ACCOUNT.USER_ID unique rule and both primary keys. Keep these. BALANCE remains NUMBER(18,2). Do not add duplicate constraints just because an existing rule has a different name.

## 13 Remove the old minimum balance rule by its actual name

First inspect the checks. Confirm the new non-negative rule is ENABLED and VALIDATED before removing the old rule.

```sql
SELECT constraint_name, search_condition, status, validated
FROM user_constraints
WHERE table_name = 'ACCOUNT' AND constraint_type = 'C'
ORDER BY constraint_name;
```

The repository's file 7 names the old balance >= 5000 rule SP_CK_ACCT_MIN_BAL. Another existing team database used CK_ACCOUNTS_MIN_BALANCE. Run only the statement whose name and condition appear in your results:

```sql
-- Use ONLY if this existing constraint enforces balance >= 5000:
ALTER TABLE account DROP CONSTRAINT sp_ck_acct_min_bal;
```

Or, if the other name appears:

```sql
-- Use ONLY if this existing constraint enforces balance >= 5000:
ALTER TABLE account DROP CONSTRAINT CK_ACCOUNTS_MIN_BALANCE;
```

If neither appears and there is no other minimum-5000 check, skip removal. If both appear with that condition, both must be removed after confirming the new rule. Do not drop unrelated checks. Rerun the inspection query: balance >= 0 must remain, and no balance >= 5000 rule should remain. This changes the permitted range, not the stored balances.

## 14 Verify the finished schema and existing data

```sql
SELECT table_name, constraint_name, constraint_type, status, validated
FROM user_constraints
WHERE table_name IN ('ROLES', 'USERS', 'ACCOUNT')
ORDER BY table_name, constraint_type, constraint_name;

SELECT c.table_name, c.constraint_name, c.constraint_type, cc.column_name
FROM user_constraints c
JOIN user_cons_columns cc
  ON cc.constraint_name = c.constraint_name
WHERE c.table_name IN ('ROLES', 'USERS', 'ACCOUNT')
  AND c.constraint_type IN ('P', 'U', 'R')
ORDER BY c.table_name, c.constraint_name, cc.position;

SELECT 'USERS' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'ACCOUNT', COUNT(*) FROM account;

SELECT account_id, user_id, balance, created_at
FROM account ORDER BY account_id;

SELECT account_number, COUNT(*) FROM account
GROUP BY account_number HAVING COUNT(*) > 1;

SELECT user_id, COUNT(*) FROM account
GROUP BY user_id HAVING COUNT(*) > 1;

SELECT u.user_id, u.name
FROM users u JOIN roles r ON r.role_id = u.role_id
WHERE r.role_name = 'CUSTOMER'
  AND NOT EXISTS (SELECT 1 FROM account a WHERE a.user_id = u.user_id);
```

Required results:

- All required constraints are ENABLED and VALIDATED. P means primary key, U unique, R foreign key and C check or NOT NULL.
- Confirm User email and phone uniqueness, Account number uniqueness, a single-column ACCOUNT.USER_ID unique constraint, the Account owner foreign key and the User role foreign key.
- User and Account row counts, existing IDs, balances and CREATED_AT values match step 1. Copied password checks from step 6 are all COPIED.
- Both duplicate queries return no rows.
- The last query identifies customers without accounts. It must return no rows to satisfy the complete business rule. A unique ACCOUNT.USER_ID constraint enforces at most one account; it does not create a missing account. Review any returned users with the team and resolve their account provisioning in the next approved task. Do not invent an opening balance or delete the user.

These checks confirm schema and data conditions. They do not test authentication, authorization or lifecycle enforcement in Java.

## Common errors and what to do

| Error or result | Meaning and next action |
| --- | --- |
| ORA-00942 referring to ROLES inside a DECLARE block | A static query may be compiled before ROLES exists. Step 2 uses dynamic CREATE and a separate SELECT afterward. Check the connected schema as well. |
| ORA-01430 column already exists | That ADD step was already applied. Inspect all columns before resuming; do not drop them. |
| ORA-02264 constraint name already used | Inspect the existing constraint and verify its condition, table and status before skipping the statement. |
| ORA-02443 cannot drop nonexistent constraint | Check the actual constraint name and condition using step 13. Do not guess another name. |
| Unique or foreign-key validation error | Existing duplicates or orphan references need review. Do not delete records to force migration through. |
| NOT NULL validation error | Some required values are still missing. Inspect the relevant backfill and affected rows. |
| Zero rows updated | Normal for empty tables or already-filled values; verify the results before continuing. |
| New registration fails after migration | Current Java code does not populate all new required fields. Complete the Java update before testing registration. |

## Handoff to the Java task

Share your final verification results and any errors with the team. No SQL in this guide implements login, JWT, Spring Security, admin APIs, transactions, beneficiaries or risk logic.

The next approved task must map Role and the new User/Account fields, generate account numbers through SEQ_ACCOUNT_NUMBER, update timestamps and create each customer with their account atomically. Existing USERS.PASSWORD and PASSWORD_HASH are separate columns with no synchronization trigger: the later task must explicitly plan the legacy column transition, including its existing NOT NULL rule. Do not assume changing the Java annotation alone is enough for new inserts.

The Java Account service also still checks a minimum balance of 5000; the next task must align it with the new database rule. Keep ddl-auto=validate and do not resume ordinary application writes with the old mappings.

This guide was reviewed against the repository's file 7, current migration and current User/Account entities. Its SQL has not been executed by the assistant against teammates' databases.
