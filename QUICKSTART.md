# SafePay — quick setup

**Windows PowerShell + your own fresh Oracle database.** Example project path: `C:\dev\SafePay`; replace it with your actual path. Follow steps 1–5, then complete the checks in step 6. For troubleshooting, see the unchanged [full README](README.md#12-troubleshooting).

## 1. Check installed tools and get the project

```powershell
java -version
javac -version
node --version
npm.cmd --version
git --version
```

| Requirement | Expected |
| --- | --- |
| JDK / javac | **21.x**; set `JAVA_HOME` to the JDK folder |
| Node.js | **24.x** recommended; project supports `>=22 <25` |
| npm / Git | Both commands print a version without errors |
| Oracle Database Free | Installed and running; project setup uses **23ai / 26ai**, PDB service **FREEPDB1**, port **1521** |
| SQL Developer | Installed; this is the client, not the database server |
| Maven | No separate install: repository wrapper supplies **3.9.16** |

Missing a tool? Use the [installation links](README.md#1-install-prerequisites), then reopen PowerShell. A new Oracle installation must be verified with the checks below.

If you do not already have the complete project:

```powershell
New-Item -ItemType Directory -Path C:\dev -Force
Set-Location C:\dev
git clone --branch safepay_aditya https://github.com/Oblivious19/Safe-Pay.git SafePay
```

Use the team's agreed branch if it changes. Keep `backend`, `frontend` and `integration-evidence/compare-snapshots.cjs` together.

## 2. Create the two database users

In SQL Developer, create this connection and click **Test → Connect**:

| Name | Username | Password | Role | Host | Port | Service name |
| --- | --- | --- | --- | --- | --- | --- |
| `Local_SafePay_Setup` | `SYSTEM` | Your Oracle installation password | `default` | `localhost` | `1521` | `FREEPDB1` |

Choose **Basic → Service name**, not SID. Use your actual PDB name if different, consistently throughout this guide. Do not create these users in `CDB$ROOT`. If either SafePay user already exists, stop and inspect that setup instead of recreating/deleting it.

In this **SYSTEM worksheet**, replace the two password placeholders with different passwords you choose, then press **F5 / Run Script** once:

```sql
SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE
CREATE USER SAFEPAY_OWNER IDENTIFIED BY "REPLACE_OWNER_PASSWORD"
  DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA 250M ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE,
      CREATE VIEW, CREATE TRIGGER, CREATE PROCEDURE TO SAFEPAY_OWNER;
CREATE USER SAFEPAY_APP IDENTIFIED BY "REPLACE_APP_PASSWORD"
  DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP;
GRANT CREATE SESSION TO SAFEPAY_APP;
```

**Expected messages, in order:** `User created.` → `Grant succeeded.` → `User created.` → `Grant succeeded.` No `ORA-...` errors. Stop on an error; earlier successful DDL is not rolled back. Use passwords without double quotes for this example.

Create these two additional SQL Developer connections, with the **same host, port, service and default role**:

| Connection name | Username | Password |
| --- | --- | --- |
| `safepay_owner_access` | `SAFEPAY_OWNER` | Owner password chosen above |
| `safepay_app_access` | `SAFEPAY_APP` | App password chosen above |

**Test must say `Success` for both.** Owner runs migrations; app runs application queries. Do not grant either user DBA. Leave database/listener services running; SQL Developer itself can be closed.

## 3. Set backend environment — Terminal A

Copy this into PowerShell; enter **your new database passwords** at the prompts:

```powershell
Set-Location C:\dev\SafePay\backend
$env:SAFEPAY_DB_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:SAFEPAY_DB_OWNER_USERNAME = 'SAFEPAY_OWNER'
$env:SAFEPAY_DB_APP_USERNAME = 'SAFEPAY_APP'
$secret = Read-Host 'SAFEPAY_OWNER password' -AsSecureString
$env:SAFEPAY_DB_OWNER_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$secret = Read-Host 'SAFEPAY_APP password' -AsSecureString
$env:SAFEPAY_DB_APP_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
Remove-Variable secret
$env:SAFEPAY_BROWSER_ORIGIN = 'http://localhost:8000'
$env:SAFEPAY_REFRESH_COOKIE_SECURE = 'false'
$env:SAFEPAY_OTP_EMAIL_ENABLED = 'false'
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:SAFEPAY_JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$rng.Dispose()
Remove-Variable bytes, rng
```

Keep this terminal open. New terminals need these settings again; `.env` is not automatically loaded. A newly generated JWT secret requires re-login. Never commit these secrets.

## 4. Run migrations and backend — same Terminal A

```powershell
.\mvnw.cmd --version
.\mvnw.cmd -DskipTests package
```

After **BUILD SUCCESS**, run:

```powershell
$SafePayArgs = @(
  '--spring.flyway.locations=classpath:db/migration,classpath:db/showcase'
  '--safepay.protection-scheduler.enabled=false'
  '--safepay.settlement-processor.enabled=false'
  '--safepay.notification-dispatcher.enabled=false'
)
java -jar .\target\safepay-0.0.1-SNAPSHOT.jar @SafePayArgs
```

**This startup runs the migrations automatically:** V1–V13, including V12 demo data, then runtime grants. Do not run migration SQL manually. **Retain both Flyway locations on every later startup/test.** Wait for `Started SafePayApplication` on port **8080**.

Workers/email remain disabled so the fresh-seed checks stay repeatable. After checking setup, enable full payment timers/settlement using [README section 8](README.md#8-enable-settlement-timers-and-notifications), and email OTP using [section 9](README.md#9-enable-real-email-otp). Without those, automatic settlement and real OTP completion are unavailable.

## 5. Run frontend — Terminal B

```powershell
Set-Location C:\dev\SafePay\frontend\SafePayJet
npm.cmd ci
npm.cmd run build
npm.cmd run serve -- --build=false
```

Stop at any failed command. Open **http://localhost:8000**. **Do not sign in yet**: first run the fresh-data checks below. Keep both server terminals running.

## 6. Final checks — expected outputs

These are **expected results for a fresh V1–V13 + V12-showcase installation**, derived from the repository, not a claim that your database has already passed. Run SQL checks **before login, registration, API tests or enabling workers**. No fixed numeric user/account IDs are assumed.

### A. Users and privileges — SYSTEM connection

```sql
SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS pdb FROM dual;

SELECT u.username, u.account_status,
       (SELECT COUNT(*) FROM dba_sys_privs p WHERE p.grantee = u.username) AS system_privileges,
       (SELECT COUNT(*) FROM dba_role_privs r WHERE r.grantee = u.username) AS granted_roles
FROM dba_users u
WHERE u.username IN ('SAFEPAY_APP', 'SAFEPAY_OWNER')
ORDER BY u.username;

SELECT COUNT(DISTINCT table_name) AS granted_objects, COUNT(*) AS object_privileges
FROM dba_tab_privs
WHERE owner = 'SAFEPAY_OWNER' AND grantee = 'SAFEPAY_APP';
```

Expected result grids, respectively:

| PDB |
| --- |
| FREEPDB1 |

| USERNAME | ACCOUNT_STATUS | SYSTEM_PRIVILEGES | GRANTED_ROLES |
| --- | --- | ---: | ---: |
| SAFEPAY_APP | OPEN | 1 | 0 |
| SAFEPAY_OWNER | OPEN | 6 | 0 |

| GRANTED_OBJECTS | OBJECT_PRIVILEGES |
| ---: | ---: |
| 37 | 63 |

The owner's six system privileges are exactly those granted in step 2; the app's single system privilege is `CREATE SESSION`. Its 63 object privileges come from Flyway's repeatable grant script.

### B. Migrations and schema — SAFEPAY_OWNER connection

```sql
SELECT COUNT(CASE WHEN "version" IS NOT NULL AND "success" = 1 THEN 1 END) AS versions_ok,
       COUNT(CASE WHEN "version" = '12' AND "success" = 1 THEN 1 END) AS seed_ok,
       COUNT(CASE WHEN "version" = '13' AND "success" = 1 THEN 1 END) AS v13_ok,
       COUNT(CASE WHEN "script" = 'R__safepay_app_grants.sql' AND "success" = 1 THEN 1 END) AS grants_ok,
       COUNT(CASE WHEN "success" = 0 THEN 1 END) AS failed
FROM "flyway_schema_history";

SELECT (SELECT COUNT(*) FROM user_tables) AS tables,
       (SELECT COUNT(*) FROM user_views) AS views,
       (SELECT COUNT(*) FROM user_objects WHERE status = 'INVALID') AS invalid_objects
FROM dual;
```

| VERSIONS_OK | SEED_OK | V13_OK | GRANTS_OK | FAILED |
| ---: | ---: | ---: | ---: | ---: |
| 13 | 1 | 1 | 1 | 0 |

| TABLES | VIEWS | INVALID_OBJECTS |
| ---: | ---: | ---: |
| 20 | 5 | 0 |

The 20 tables are **19 application tables + Flyway history**. If V12 is absent, stop: do not manually insert seed data, use out-of-order migration, or edit migration history.

### C. Seeded rows — SAFEPAY_OWNER connection

```sql
SELECT 'ACCOUNT' AS table_name, COUNT(*) AS rows_found FROM account
UNION ALL SELECT 'APP_NOTIFICATION', COUNT(*) FROM app_notification
UNION ALL SELECT 'APP_ROLE', COUNT(*) FROM app_role
UNION ALL SELECT 'APP_USER', COUNT(*) FROM app_user
UNION ALL SELECT 'AUDIT_LOG', COUNT(*) FROM audit_log
UNION ALL SELECT 'AUTH_SESSION', COUNT(*) FROM auth_session
UNION ALL SELECT 'BENEFICIARY', COUNT(*) FROM beneficiary
UNION ALL SELECT 'IDEMPOTENCY_RECORD', COUNT(*) FROM idempotency_record
UNION ALL SELECT 'LEDGER_ENTRY', COUNT(*) FROM ledger_entry
UNION ALL SELECT 'LEDGER_POSTING', COUNT(*) FROM ledger_posting
UNION ALL SELECT 'PAYMENT_OTP_CHALLENGE', COUNT(*) FROM payment_otp_challenge
UNION ALL SELECT 'PAYMENT_TRANSACTION', COUNT(*) FROM payment_transaction
UNION ALL SELECT 'PROTECTION_POLICY', COUNT(*) FROM protection_policy
UNION ALL SELECT 'RISK_POLICY', COUNT(*) FROM risk_policy
UNION ALL SELECT 'RISK_POLICY_BAND', COUNT(*) FROM risk_policy_band
UNION ALL SELECT 'RISK_REVIEW', COUNT(*) FROM risk_review
UNION ALL SELECT 'TRANSACTION_EXCEPTION', COUNT(*) FROM transaction_exception
UNION ALL SELECT 'TRANSACTION_RISK_FACTOR', COUNT(*) FROM transaction_risk_factor
UNION ALL SELECT 'USER_ROLE', COUNT(*) FROM user_role
ORDER BY table_name;
```

Expected **19 result rows**:

| TABLE_NAME | ROWS_FOUND |
| --- | ---: |
| ACCOUNT | 32 |
| APP_NOTIFICATION | 10 |
| APP_ROLE | 4 |
| APP_USER | 33 |
| AUDIT_LOG | 12 |
| AUTH_SESSION | 0 |
| BENEFICIARY | 30 |
| IDEMPOTENCY_RECORD | 0 |
| LEDGER_ENTRY | 76 |
| LEDGER_POSTING | 38 |
| PAYMENT_OTP_CHALLENGE | 6 |
| PAYMENT_TRANSACTION | 25 |
| PROTECTION_POLICY | 4 |
| RISK_POLICY | 1 |
| RISK_POLICY_BAND | 4 |
| RISK_REVIEW | 5 |
| TRANSACTION_EXCEPTION | 2 |
| TRANSACTION_RISK_FACTOR | 22 |
| USER_ROLE | 33 |

### D. Roles and financial consistency — SAFEPAY_OWNER connection

```sql
SELECT r.role_code, COUNT(*) AS users_found
FROM app_role r JOIN user_role ur ON ur.role_id = r.role_id
GROUP BY r.role_code ORDER BY r.role_code;

SELECT 'LEDGER' AS check_name, COUNT(*) AS mismatches
FROM vw_ledger_reconciliation WHERE reconciliation_status <> 'BALANCED'
UNION ALL
SELECT 'RESERVATIONS', COUNT(*)
FROM vw_reservation_reconciliation WHERE reconciliation_status <> 'MATCH';
```

| ROLE_CODE | USERS_FOUND |
| --- | ---: |
| AUDITOR | 1 |
| CUSTOMER | 30 |
| RISK_OFFICER | 1 |
| SYSTEM_ADMIN | 1 |

| CHECK_NAME | MISMATCHES |
| --- | ---: |
| LEDGER | 0 |
| RESERVATIONS | 0 |

### E. Runtime database access — SAFEPAY_APP connection

```sql
SELECT USER AS connected_user,
       (SELECT COUNT(*) FROM SAFEPAY_OWNER.APP_USER) AS users_visible,
       (SELECT COUNT(*) FROM SAFEPAY_OWNER.ACCOUNT) AS accounts_visible
FROM dual;
```

| CONNECTED_USER | USERS_VISIBLE | ACCOUNTS_VISIBLE |
| --- | ---: | ---: |
| SAFEPAY_APP | 33 | 32 |

An `ORA-00942` here means wrong connection/schema or missing migrations/grants; do not fix it by granting DBA.

### F. Backend, frontend and sign-in

In **Terminal C**, run:

```powershell
(Invoke-RestMethod 'http://localhost:8080/actuator/health').status
Set-Location C:\dev\SafePay\frontend\SafePayJet
npm.cmd run typecheck
npm.cmd test
```

| Check | Pass result |
| --- | --- |
| Backend health | Prints `UP` |
| Backend package command, step 4 | `BUILD SUCCESS`, exit code **0**; tests were skipped |
| Frontend typecheck | No TypeScript errors; exit code **0** |
| Frontend tests | **0 failures**; exit code **0** |
| Frontend build, step 5 | Completes successfully; exit code **0** |
| Browser | `http://localhost:8000` loads the login page and sign-in opens the correct role workspace |

Now sign in with an email below and its password from the [seed guide, sections 8.1–8.2](Documentation%20for%20SafePay/SafePay_V1_Data_Seed_Complete_Guide.md):

| Role | Demo email |
| --- | --- |
| Customer | `priya.nair@gmail.com` |
| Second customer | `kavya.sharma@gmail.com` |
| Risk Officer | `rhea.malhotra@gmail.com` |
| System Admin | `vikram.bhat@gmail.com` |
| Auditor | `anjali.thomas@gmail.com` |

Website credentials are separate from the Oracle passwords. After sign-in/testing, session/audit/notification counts can change; do not compare an exercised database with untouched seed counts.

### G. Full backend tests — after the fresh-data checks

Stop the backend with **Ctrl+C** in Terminal A. In that same terminal, retaining its environment and `backend` working directory:

```powershell
$TestArgs = @(
  '-Dspring.flyway.locations=classpath:db/migration,classpath:db/showcase'
  '-Dsafepay.protection-scheduler.enabled=false'
  '-Dsafepay.settlement-processor.enabled=false'
  '-Dsafepay.notification-dispatcher.enabled=false'
  '-Dsafepay.otp.email.enabled=false'
  'test'
)
.\mvnw.cmd @TestArgs
```

Expected: **BUILD SUCCESS**, **Failures: 0**, **Errors: 0**, exit code **0**. Inspect any skipped tests; a skip is not a pass. These tests include real Oracle writes/cleanup: use only your isolated test database. Reports are in `backend/target/surefire-reports/`.

Restart afterwards with `java -jar .\target\safepay-0.0.1-SNAPSHOT.jar @SafePayArgs`. For real OTP, timed release and settlement acceptance, use [README sections 8–10](README.md#8-enable-settlement-timers-and-notifications). Automatic checks above do not prove those live flows.
