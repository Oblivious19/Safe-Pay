# SafePay — teammate setup and development guide

Run the complete SafePay application on your own machine: Oracle database, Spring Boot backend, and Oracle JET frontend. You do **not** need another teammate's database export, database password, JWT secret, or private credentials file. The repository contains versioned schema migrations, reference data, and an optional demonstration seed.

This guide uses **Windows PowerShell** and a **separate local development database**. Follow sections 1–7 in order before enabling email or background payment processing. On macOS/Linux, use the same database contract and configuration, but adapt shell commands (`./mvnw`, `npm`, and your shell's environment syntax) and choose an Oracle installation supported on your platform.

> This is a development/demo setup, not a production deployment guide. Setup instructions were checked against the current source; writing this README did not run a new Oracle installation or establish that every feature is defect-free. Existing integration reports describe earlier runs, not your new database.

## Contents

1. [Install prerequisites](#1-install-prerequisites)
2. [Get the project](#2-get-the-project)
3. [Create the Oracle users](#3-create-the-oracle-users)
4. [Configure the backend environment](#4-configure-the-backend-environment)
5. [Build, migrate and start the backend](#5-build-migrate-and-start-the-backend)
6. [Verify the database and find demo logins](#6-verify-the-database-and-find-demo-logins)
7. [Install and start the frontend](#7-install-and-start-the-frontend)
8. [Enable settlement, timers and notifications](#8-enable-settlement-timers-and-notifications)
9. [Enable real email OTP](#9-enable-real-email-otp)
10. [Test the application](#10-test-the-application)
11. [Daily development and team changes](#11-daily-development-and-team-changes)
12. [Troubleshooting](#12-troubleshooting)
13. [Project map and further reading](#13-project-map-and-further-reading)

## 1. Install prerequisites

| Tool | Use / version |
| --- | --- |
| [Git](https://git-scm.com/downloads/) | Clone and collaborate on the repository. |
| [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) | Install the **JDK**, not only a JRE. The backend targets Java 21. |
| [Node.js 24 LTS](https://nodejs.org/en/download) | Includes npm. The frontend declares Node `>=22 <25`; use 24 LTS, not a newer unsupported major. |
| [Oracle Database Free](https://www.oracle.com/database/free/get-started/) | The actual database server. Oracle currently distributes 26ai Free; an existing project-compatible 23ai installation can also be used. A fresh 26ai installation has not been acceptance-tested by this README task. |
| [Oracle SQL Developer](https://www.oracle.com/database/sqldeveloper/technologies/download/) | A database client for setup and inspection. Installing this alone does **not** install a database server. |
| Browser; optionally Eclipse/STS, IntelliJ or VS Code | The terminal workflow below does not require an IDE. |

For a new Windows database, download the Windows package, extract it, and follow Oracle's installer as an administrator. Choose and retain your own database administrator password. See the [official Windows installation guide](https://docs.oracle.com/en/database/oracle/oracle-database/26/xeinw/oracle-ai-database-free-installation-guide-microsoft-windows.pdf) for supported Windows editions and installation requirements.

The usual Oracle Free local connection is **host `localhost`, port `1521`, service `FREEPDB1`**. `FREEPDB1` is the pluggable database used by this project; `FREE` is the container database service. Use your installation's actual service name if it differs. [Oracle connection examples](https://www.oracle.com/database/free/get-started/)

Open a **new PowerShell window after installing tools**, then check:

```powershell
git --version
java -version
javac -version
node --version
npm.cmd --version
```

Java and `javac` should report 21; Node should report 24.x. Set Windows `JAVA_HOME` to the JDK installation directory (not its `bin` subdirectory) and ensure its `bin` is on `PATH`. Do not replace the rest of your system `PATH`. Reopen your terminal after changing these settings.

You do not need a global Maven or Oracle JET CLI installation. The backend includes a Maven wrapper, and the frontend pins its local CLI. The first builds need internet access to download dependencies.

## 2. Get the project

Example location: `C:\dev\SafePay`. You can choose another directory; substitute it consistently below.

```powershell
New-Item -ItemType Directory -Path C:\dev -Force
Set-Location C:\dev
git clone --branch safepay_aditya https://github.com/Oblivious19/Safe-Pay.git SafePay
Set-Location C:\dev\SafePay
git status
```

`safepay_aditya` is the branch inspected for this README. If the team later merges the consolidated application elsewhere, use that agreed branch. If you already cloned the correct repository, use it rather than cloning a second copy. Private-repository access requires your own GitHub access; do not put tokens in the clone URL.

You should have these paths:

```text
SafePay/
  backend/pom.xml
  backend/mvnw.cmd
  backend/src/main/resources/db/migration/
  backend/src/main/resources/db/showcase/
  frontend/SafePayJet/package.json
  frontend/SafePayJet/package-lock.json
  integration-evidence/compare-snapshots.cjs
  Documentation for SafePay/
```

Keep the complete clone. One frontend test imports `integration-evidence/compare-snapshots.cjs`; copying only `frontend/` is insufficient for the full test suite.

## 3. Create the Oracle users

### 3.1 Connect as the local database administrator

In SQL Developer, create a connection with:

| Field | Value |
| --- | --- |
| Connection name | `Local_SafePay_Setup` (any descriptive name) |
| Username | `SYSTEM` |
| Password | Your Oracle installation's administrator password |
| Role | `default` |
| Connection type | `Basic` |
| Hostname / port | `localhost` / `1521` |
| Connection choice | **Service name**, not SID |
| Service name | `FREEPDB1`, or your own development PDB service |

Click **Test**, then **Connect**. Open a worksheet for this connection and run:

```sql
SELECT USER AS connected_user,
       SYS_CONTEXT('USERENV', 'CON_NAME') AS container_name
FROM dual;

SELECT username
FROM dba_users
WHERE username IN ('SAFEPAY_OWNER', 'SAFEPAY_APP');

SELECT tablespace_name, contents
FROM dba_tablespaces
WHERE tablespace_name IN ('USERS', 'TEMP');
```

Confirm you are in your development PDB, **not `CDB$ROOT`**. A fresh setup should have no existing SafePay users, and should have permanent `USERS` and temporary `TEMP` tablespaces. If either user already exists, inspect that setup first; do not drop it or run the create-user block over an existing team's database. If your DBA uses different tablespaces, use those approved names below.

### 3.2 Create the owner and restricted runtime user

Replace both password placeholders with **two different passwords you choose**. The double quotes belong to the SQL syntax; choose passwords without a double-quote character for this example. Run this block **once**, using **F5 / Run Script** in the administrator worksheet:

```sql
SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE USER SAFEPAY_OWNER IDENTIFIED BY "REPLACE_WITH_YOUR_OWNER_PASSWORD"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA 250M ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE,
      CREATE VIEW, CREATE TRIGGER, CREATE PROCEDURE
TO SAFEPAY_OWNER;

CREATE USER SAFEPAY_APP IDENTIFIED BY "REPLACE_WITH_YOUR_APP_PASSWORD"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP;

GRANT CREATE SESSION TO SAFEPAY_APP;
```

`SET DEFINE OFF` prevents SQL Developer treating `&` in a password as a substitution variable. If a statement fails, stop and resolve the first error: Oracle DDL commits independently, so earlier successful statements can remain. Do not blindly rerun the whole block.

The application needs both users:

- **SAFEPAY_OWNER** owns tables, sequences, triggers and views. Flyway connects as this user to apply migrations.
- **SAFEPAY_APP** is the restricted runtime connection. The repository's repeatable migration, `R__safepay_app_grants.sql`, grants its required object permissions after the schema exists.

Do not grant `DBA`, `RESOURCE`, broad `ANY TABLE` permissions, or owner DDL privileges to `SAFEPAY_APP` to bypass an error. Keep these schema names unchanged: mappings, grants and tests depend on them.

### 3.3 Save two normal SQL Developer connections

Create `safepay_owner_access` and `safepay_app_access`, using the matching usernames and passwords above. Both use **Role: default**, the same host/port/PDB service, and **Service name** connection mode. Test each connection.

You can close SQL Developer afterwards. Spring Boot makes its own connections; SQL Developer connections do not need to stay open. **The Oracle database service and listener must stay running.**

Do not create application tables manually. The next steps let Flyway create and seed them in order.

## 4. Configure the backend environment

Open **Terminal A** in PowerShell. This terminal will run the backend. Environment settings made here belong to this terminal and its child processes; another terminal or an IDE will not automatically inherit them.

### 4.1 Required settings

```powershell
Set-Location C:\dev\SafePay\backend

$env:SAFEPAY_DB_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:SAFEPAY_DB_OWNER_USERNAME = 'SAFEPAY_OWNER'
$env:SAFEPAY_DB_APP_USERNAME = 'SAFEPAY_APP'

$ownerPassword = Read-Host 'Your SAFEPAY_OWNER password' -AsSecureString
$env:SAFEPAY_DB_OWNER_PASSWORD = [System.Net.NetworkCredential]::new('', $ownerPassword).Password
$appPassword = Read-Host 'Your SAFEPAY_APP password' -AsSecureString
$env:SAFEPAY_DB_APP_PASSWORD = [System.Net.NetworkCredential]::new('', $appPassword).Password
Remove-Variable ownerPassword, appPassword

$env:SAFEPAY_BROWSER_ORIGIN = 'http://localhost:8000'
$env:SAFEPAY_REFRESH_COOKIE_SECURE = 'false'
$env:SAFEPAY_OTP_EMAIL_ENABLED = 'false'
```

The password prompts keep literal passwords out of typed command history; the backend still receives them as process environment values. Do not print or share the complete environment.

`SAFEPAY_REFRESH_COOKIE_SECURE=false` is for this **local HTTP** setup. An HTTPS deployment should use secure cookies. Use `localhost` consistently in your browser rather than switching to `127.0.0.1`.

### 4.2 Generate your own JWT signing secret

Run once for this terminal, before starting the backend:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtGenerator.GetBytes($jwtBytes)
$env:SAFEPAY_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
$jwtGenerator.Dispose()
Remove-Variable jwtBytes, jwtGenerator
```

This generates a Base64-encoded random 256-bit secret. Do not use a memorable word or another teammate's secret. For repeat use, retain your own value in an approved private local secret configuration; changing it invalidates previously issued access tokens, so sign in again. If you simply generate a new secret for each local session, expect that re-login.

### 4.3 Environment reference

| Variable | Required value / purpose |
| --- | --- |
| `SAFEPAY_DB_URL` | JDBC URL for **your** PDB. |
| `SAFEPAY_DB_OWNER_USERNAME` | `SAFEPAY_OWNER`. |
| `SAFEPAY_DB_OWNER_PASSWORD` | Password you chose in section 3. |
| `SAFEPAY_DB_APP_USERNAME` | `SAFEPAY_APP`. |
| `SAFEPAY_DB_APP_PASSWORD` | Password you chose in section 3. |
| `SAFEPAY_JWT_SECRET_BASE64` | Random Base64 value containing at least 32 decoded bytes. |
| `SAFEPAY_BROWSER_ORIGIN` | `http://localhost:8000` for this frontend. |
| `SAFEPAY_REFRESH_COOKIE_SECURE` | `false` for local HTTP only. |
| `SAFEPAY_OTP_EMAIL_ENABLED` | `false` initially; section 9 covers email. |

Plain Spring Boot startup here does **not automatically load a root `.env` file**. Use these process variables, or deliberately configure an equivalent private IDE launch environment. No database, JWT or SMTP secrets belong in frontend configuration, Git commits or screenshots.

## 5. Build, migrate and start the backend

### 5.1 Build the application

In **Terminal A**, with section 4's environment still set:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd -DskipTests package
```

The wrapper selects Maven 3.9.16. Check that its Java version is 21. Wait for **BUILD SUCCESS** before continuing. This command compiles/packages the application and skips test execution; it is not proof of database connectivity or passing tests.

### 5.2 First startup, with demo data and background workers disabled

```powershell
$SafePayArgs = @(
    '--spring.flyway.locations=classpath:db/migration,classpath:db/showcase'
    '--safepay.protection-scheduler.enabled=false'
    '--safepay.settlement-processor.enabled=false'
    '--safepay.notification-dispatcher.enabled=false'
)

java -jar .\target\safepay-0.0.1-SNAPSHOT.jar @SafePayArgs
```

Keep this terminal open. On a fresh schema, Flyway applies the current versioned migrations **V1–V13**, including **V12 from `db/showcase`**, and the repeatable runtime grants. Hibernate then validates the schema and the server starts on **8080**.

**Keep both Flyway locations on every subsequent startup and database-backed test for this seeded database.** The default `application.properties` scans only `db/migration`. Starting with that default on a fresh database can apply V13 without V12, leaving out the demo dataset; switching locations later is not a safe substitute for applying them correctly the first time. A previously applied V12 can also fail validation when its location is omitted.

Do not manually run migration files with F5, edit their checksums, enable `out-of-order`, run Flyway `clean`, or turn on Hibernate schema creation to repair onboarding. Investigate the first migration error. Oracle DDL failures can leave partially created objects.

For this initial mode, payment timers, settlement processing and automatic notification dispatch are deliberately disabled. Authentication and read screens can be checked first. Full payment lifecycle tests need section 8, and OTP tests also need section 9.

### 5.3 Health check

In a separate PowerShell window:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health'
```

Expected status: **UP**. Also look for successful Flyway startup and `Started SafePayApplication` / Tomcat on port 8080 in Terminal A. A successful Maven build or Eclipse import alone does not mean the server is running.

## 6. Verify the database and find demo logins

Run these **read-only** checks in a `SAFEPAY_OWNER` SQL Developer worksheet after the first successful startup:

```sql
SELECT "installed_rank", "version", "description", "success"
FROM "flyway_schema_history"
ORDER BY "installed_rank";

SELECT object_name, object_type
FROM user_objects
WHERE status = 'INVALID';

SELECT COUNT(*) AS demo_user_count FROM app_user;
SELECT COUNT(*) AS account_count FROM account;

SELECT u.user_id, u.email, r.role_code
FROM app_user u
JOIN user_role ur ON ur.user_id = u.user_id
JOIN app_role r ON r.role_id = ur.role_id
ORDER BY r.role_code, u.email;

SELECT u.email, a.account_id, a.account_number,
       a.current_balance, a.reserved_amount, a.available_balance
FROM account a
JOIN app_user u ON u.user_id = a.owner_user_id
ORDER BY u.email;
```

On the untouched current showcase seed, expect **33 application users** (30 customers and 3 staff) and **32 accounts** (30 customer accounts and 2 system accounts). Migration history should include successful V12 and V13 and the repeatable grants. The invalid-object query should return no rows. Counts and balances naturally change as you register users or test payments.

From a `SAFEPAY_APP` worksheet, this should succeed:

```sql
SELECT COUNT(*) AS visible_accounts FROM SAFEPAY_OWNER.ACCOUNT;
```

The app user's unqualified `ACCOUNT` name is not its own table; use the owner prefix when inspecting directly.

### Application sign-in

Database users and website users are different. Do **not** enter `SAFEPAY_OWNER` or `SAFEPAY_APP` into the website login form.

| Persona | Seeded application email | Authority |
| --- | --- | --- |
| Customer | `priya.nair@gmail.com` | `CUSTOMER` |
| Second customer | `kavya.sharma@gmail.com` | `CUSTOMER` |
| Risk Officer | `rhea.malhotra@gmail.com` | `RISK_OFFICER` |
| System Admin | `vikram.bhat@gmail.com` | `SYSTEM_ADMIN` |
| Auditor | `anjali.thomas@gmail.com` | `AUDITOR` |

Use each persona's **documented demo password** from [the seed guide, sections 8.1 and 8.2](Documentation%20for%20SafePay/SafePay_V1_Data_Seed_Complete_Guide.md). That committed catalogue is sufficient for a fresh seeded database; you do not need a private `userCredentials.txt`. These are publicly documented demonstration credentials, suitable only for an isolated demo installation. Do not reuse them for database accounts or real services.

Use email to sign in. Numeric user, account, beneficiary and transaction IDs are generated independently on each database; do not copy IDs from another teammate's screenshots, previous evidence or SQL output. Registration creates a customer identity; it does **not** automatically provision/fund a bank account. Start payment tests with seeded customers.

## 7. Install and start the frontend

Open **Terminal B**:

```powershell
Set-Location C:\dev\SafePay\frontend\SafePayJet
npm.cmd ci
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run serve -- --build=false
```

Run these in order, stopping at any failure. `npm ci` installs the versions pinned by `package-lock.json`, including fonts and the local Oracle JET tooling. You do not need to install `ojet` globally or upgrade package versions to get started.

Open **http://localhost:8000** manually. This serve command uses server-only mode, so it may not open a browser automatically. Keep Terminal A and Terminal B running. Sign in with one of section 6's application users.

The current frontend connects to **http://localhost:8080/api/v1**. No frontend environment file is required for this local setup. Opening HTML files directly from disk or using the optional mock server on port 8001 is not a full-stack run.

LiveReload and file watching are disabled in the current serve script. After frontend source changes, stop serving with **Ctrl+C**, rebuild with `npm.cmd run build`, serve again, and refresh the browser. Do not launch a second server on the same port.

## 8. Enable settlement, timers and notifications

Do this only after initial setup works, on your own demo database. Turning workers on can process **eligible existing seeded payments and notifications as well as new tests**, changing reservations, balances and lifecycle records. Record your baseline first if you want an exact before/after comparison.

### 8.1 Find this database's clearing account

As `SAFEPAY_OWNER`, run:

```sql
SELECT account_id, account_number, account_type, owner_user_id,
       currency_code, status
FROM account
WHERE account_number = 'SAFEPAY_OUTBOUND_CLEARING';
```

Expect exactly one `ACTIVE`, `INR`, `OUTBOUND_CLEARING` account with no owner user. Use its returned `ACCOUNT_ID`. Do not substitute a customer's account or an ID from historical evidence. If no correct row exists, resolve the seed problem first.

### 8.2 Restart in full local demo mode

Stop the backend in Terminal A with **Ctrl+C**. In that same terminal, where the environment remains available:

```powershell
$clearingId = Read-Host 'ACCOUNT_ID from the clearing-account query'
if ($clearingId -notmatch '^[1-9][0-9]*$') {
    throw 'Enter the positive numeric clearing account ID from your database.'
}

$SafePayArgs = @(
    '--spring.flyway.locations=classpath:db/migration,classpath:db/showcase'
    '--safepay.protection-scheduler.enabled=true'
    '--safepay.settlement-processor.enabled=true'
    "--safepay.settlement-processor.outbound-clearing-account-id=$clearingId"
    '--safepay.notification-dispatcher.enabled=true'
)

java -jar .\target\safepay-0.0.1-SNAPSHOT.jar @SafePayArgs
```

| Setting | Effect |
| --- | --- |
| Protection scheduler | Releases eligible protected transactions when their server-owned deadlines expire. |
| Settlement processor | Attempts ledger settlement of eligible released transactions. Requires the correct clearing account ID. |
| Notification dispatcher | Dispatches pending in-app events; notification REST reads remain important after reconnect. |

The short name `SAFEPAY_OUTBOUND_CLEARING_ACCOUNT_ID` appears only in a **commented example** in `application.properties`; setting that alone does not configure the processor. Use the explicit property argument above.

The current V1 settlement model simulates an outbound payment by debiting the payer account and crediting the system outbound-clearing account. It does not execute real bank transfers or automatically credit another SafePay customer's account just because beneficiary details match that customer. Check expectations against this model before reporting a recipient balance defect.

To return to the initial mode, stop the backend, recreate the disabled `$SafePayArgs` array from section 5.2, and restart. Stopping workers does not reverse changes already made.

## 9. Enable real email OTP

Email is optional for startup but necessary to finish the real OTP verification flow. With email disabled, do not expect a usable code to appear in the UI or logs.

The current backend is configured for **Gmail SMTP on port 587 with STARTTLS**. Use your own permitted test sender account and an inbox you control. Google app passwords require 2-Step Verification and may be unavailable for some managed/security configurations; follow [Google's app-password instructions](https://support.google.com/accounts/answer/185833). A normal Gmail login password is not a substitute. If your account cannot use app passwords, coordinate a supported mail setup instead of weakening authentication.

Stop the backend first. In Terminal A:

```powershell
$env:SAFEPAY_OTP_EMAIL_ENABLED = 'true'
$env:SAFEPAY_OTP_EMAIL_USERNAME = Read-Host 'Your Gmail test sender address'
$env:SAFEPAY_OTP_EMAIL_FROM = $env:SAFEPAY_OTP_EMAIL_USERNAME
$env:SAFEPAY_OTP_EMAIL_ROUTING_MODE = 'FIXED_OVERRIDE'
$env:SAFEPAY_OTP_EMAIL_RECIPIENT_OVERRIDE = Read-Host 'A test inbox you control'

$mailPassword = Read-Host 'Gmail app password' -AsSecureString
$env:SAFEPAY_OTP_EMAIL_APP_PASSWORD = [System.Net.NetworkCredential]::new('', $mailPassword).Password
Remove-Variable mailPassword

java -jar .\target\safepay-0.0.1-SNAPSHOT.jar @SafePayArgs
```

Keep `FIXED_OVERRIDE` for the seeded dataset: all OTP mail goes to your controlled inbox rather than the fictional customers' real-looking email addresses. Do not select `STORED_USER` for showcase testing.

The configured policy is **6 digits**, **5-minute validity**, **3 verification attempts**, **30-second resend cooldown**, and **3 issues per verification cycle**. Use the latest code for the intended transaction. These policy values are validated by the backend; do not shorten them to make a test run faster.

Backend health can be UP even when SMTP delivery fails; an actual controlled OTP request and receipt are the delivery check. To turn email off again, stop the backend, set `$env:SAFEPAY_OTP_EMAIL_ENABLED = 'false'`, and restart with your intended worker arguments.

## 10. Test the application

### 10.1 Automated frontend checks — no Oracle required

From `frontend/SafePayJet`:

```powershell
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

These cover types, contract/recovery logic, UI checks and evidence-comparison helpers. They do not replace real browser + backend + Oracle + email tests.

### 10.2 Backend compile and test checks

Compile production and test sources without executing tests, from `backend`:

```powershell
.\mvnw.cmd -DskipTests test-compile
```

The full test suite contains **real Oracle integration tests**, including fixture writes and cleanup. Run it against your isolated development/test database, not a shared demonstration or production database. Stop your running backend first so its workers cannot race test fixtures. Configure section 4's environment in the terminal running Maven, then run:

```powershell
$SafePayTestArgs = @(
    '-Dspring.flyway.locations=classpath:db/migration,classpath:db/showcase'
    '-Dsafepay.protection-scheduler.enabled=false'
    '-Dsafepay.settlement-processor.enabled=false'
    '-Dsafepay.notification-dispatcher.enabled=false'
    '-Dsafepay.otp.email.enabled=false'
    'test'
)

.\mvnw.cmd @SafePayTestArgs
```

Read the final test summary and `backend/target/surefire-reports/` for failures. Do not present `-DskipTests` output as passing tests. For a separate Oracle test environment, use a separate database/PDB with the same schema names and point the test terminal at its JDBC URL.

### 10.3 Minimal end-to-end acceptance checklist

Start with a known baseline, use newly created test payments, and record actual IDs, expected result, observed result and changed balances. Use separate browser profiles for simultaneous customer/staff sessions; tabs in the same browser profile share authentication cookies.

- [ ] Health is UP; each of the four roles can sign in and sees its allowed workspace.
- [ ] Customer accounts and available/current/reserved balances match the read-only SQL query in section 6.
- [ ] Beneficiary creation, editing and disabling behave correctly for the signed-in owner.
- [ ] Low-value payment creation and settlement work; there is only one financial effect after refresh/recovery.
- [ ] Protected payment cancellation releases its reservation; a second new payment expires and progresses with the scheduler enabled.
- [ ] Very-high-value payment requests a real OTP, verifies it and reaches the Risk Officer workflow; test approval, rejection and re-verification on separate appropriate cases.
- [ ] Notifications update, survive refresh, and recover after a reconnect.
- [ ] Admin and Auditor screens enforce their distinct permissions. Use a disposable user for status/role changes; preserve a usable System Admin.
- [ ] Logout, reload, session expiry and an interrupted request recover without exposing another user's data or duplicating a payment.
- [ ] Invalid amount, insufficient funds, invalid OTP, stale action and unauthorized access return a controlled error without unwanted financial changes.

Current V1 amount boundaries, from the seeded policy:

| Amount in INR | Tier | Expected protection path |
| --- | --- | --- |
| `1.00`–`5000.00` | LOW | Immediate release; settlement still needs its worker. |
| `5000.01`–`25000.00` | MEDIUM | 10-second protection window. |
| `25000.01`–`100000.00` | HIGH | 60-second protection window. |
| `100000.01` and above | VERY_HIGH | OTP verification and Risk Officer review. |

Try boundaries one at a time with sufficient **available** funds. A reservation reduces available funds before settlement changes current balance. Timers use server deadlines; a countdown reaching zero in the browser does not itself settle a payment. Do not assume a fixed time-to-settle in the presence of retries or a manual-review exception.

The existing [pending acceptance checklist](integration-evidence/PENDING_USER_TESTS.txt) and [test results](integration-evidence/TEST_RESULTS.md) provide additional scenarios. Their IDs, balances and completion status belong to the previous machine/run: re-query your own database and do not rerun historical mutation scripts blindly. Preserve failed cases and redact credentials/tokens from shared reports.

## 11. Daily development and team changes

### Starting work again

1. Start Oracle if it is stopped. SQL Developer itself is optional.
2. Open Terminal A in `backend` and configure your environment again if it is a new terminal.
3. Choose the disabled or enabled worker arguments deliberately; always retain both Flyway locations for this seeded DB.
4. Rebuild the JAR after backend changes, then run it. Alternatively, during development use the wrapper command below with the same arguments.
5. Open Terminal B in `frontend/SafePayJet`, rebuild after source changes, and serve. Run `npm ci` after a clean clone or lockfile changes, not before every launch.
6. Verify health, then open `http://localhost:8000`.

Backend source-development alternative, with `$SafePayArgs` already set and no other backend running:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=$($SafePayArgs -join ' ')"
```

Use **Ctrl+C** in each terminal to stop its server. Do not run the JAR, Maven server and an IDE backend simultaneously on port 8080.

### Optional Eclipse / STS setup

Use an IDE workspace outside the cloned project. Import **Existing Maven Projects**, selecting `backend/pom.xml`, and configure JDK 21. Run `com.ofss.SafePayApplication` as a Spring Boot application. In that launch configuration:

- Enter the environment variables from sections 4 and, if needed, 9.
- Paste the actual `--property=value` program arguments from your chosen startup mode, including both Flyway locations. Do not paste PowerShell variable definitions into the IDE's program-arguments box.
- Stop any terminal-launched backend before starting the IDE launch.

If Eclipse says the project already exists, locate the existing imported project and verify its filesystem location. Do not rename the Maven artifact or change application code just to bypass an IDE import conflict.

### Making changes safely

- Work from the team's agreed branch and create your own feature branch. Check `git status` before pulling, switching or committing; do not overwrite someone else's uncommitted work.
- Edit frontend sources under `src/`, not generated `web/`, `node_modules/` or `.test-build/` output. Keep `package.json` and `package-lock.json` consistent for intentional dependency changes.
- Treat applied Flyway migrations as immutable. Add the next agreed forward migration for schema changes; never edit V1–V13 to change a database that already applied them. Coordinate the version number with teammates.
- Keep runtime permissions restricted. If a new feature needs a new object permission, review the repeatable grants along with its migration and authorization tests.
- Review backend contracts and role permissions before connecting a new UI action. The exact authorities are `CUSTOMER`, `SYSTEM_ADMIN`, `RISK_OFFICER`, and `AUDITOR`.
- Run the checks relevant to your change, including live flows when behavior crosses frontend/API/database boundaries. Record what was actually tested and what remains pending.
- Commit source, dependency manifests, migrations and useful sanitized documentation. Keep local credentials, JWT secrets, SMTP passwords, raw database snapshots, session tokens, personal IDE state and generated dependencies out of commits. Inspect the staged diff; an ignore rule does not untrack a file already committed.
- Do not delete the entire `integration-evidence` folder without checking dependencies: `compare-snapshots.cjs` is required by the current frontend tests. Historical raw snapshots and backups are not needed to initialize a teammate's database.

## 12. Troubleshooting

Find the **first relevant error** in backend logs or the command output; later dependency/bean errors often repeat the original problem. Never solve a startup error by granting unrestricted database access, disabling schema validation or deleting migration history.

| Symptom | Check / recovery |
| --- | --- |
| `java`, `javac`, `git` or `node` not recognized | Complete the corresponding install, reopen PowerShell and check `PATH`. For Java, verify both `java -version` and `mvnw.cmd --version` use JDK 21. |
| `release version 21 not supported` / Java class-version error | Maven/IDE is using the wrong JDK. Fix `JAVA_HOME` or the launch JDK; keep the repository's Java target unchanged. |
| `npm.ps1 cannot be loaded` | Use the documented `npm.cmd` commands; changing system execution policy is unnecessary. |
| Maven/npm cannot download dependencies | Check connectivity, proxy and certificate configuration. Re-run after resolving access. Do not disable TLS checks or randomly upgrade dependencies. |
| `npm ci` reports manifest/lock mismatch | Confirm both files came from the same committed branch. Coordinate a deliberate lockfile update; do not delete the lockfile to hide the mismatch. |
| SQL Developer test fails / `ORA-12541` | The listener or database service may be stopped, or host/port is wrong. Start the services for your Oracle installation and retry. |
| `ORA-12514` / unknown service | Confirm service name `FREEPDB1` rather than SID or `FREE`, and that the PDB is open and registered. Ask the local DBA to open/save its state if needed. |
| `ORA-65096` creating a user | You are likely connected to `CDB$ROOT`. Reconnect to the intended PDB; do not rename the users with `C##`. |
| `ORA-01017` | Wrong password/user, or correct credentials against the wrong PDB. Test the same connection in SQL Developer; re-enter the appropriate process variable. |
| `ORA-28000` / `ORA-28001` | Database user is locked or its password expired. Have your local administrator inspect/unlock/reset that user, then update the matching backend secret. This differs from a website login lock. |
| `ORA-01950` / insufficient tablespace quota | Check the owner's default tablespace and quota. The local DBA can grant an appropriate quota on the intended tablespace; app-wide administrator grants are unnecessary. |
| `ORA-01031` during migration | Check owner system privileges from section 3 and identify the specific failed operation. Do not grant DBA as a shortcut. |
| `ORA-00942` at runtime | Check PDB, successful migrations and the repeatable runtime grants. Runtime SQL uses `SAFEPAY_OWNER` objects through `SAFEPAY_APP`; the two connections are not interchangeable. |
| Flyway checksum mismatch / failed or missing migration | Restore the correct versioned source and investigate migration history and the first Oracle error with the team. Do not run `repair`, `clean`, baseline or out-of-order options blindly. |
| No demo users, V13 present but V12 absent | Startup omitted `db/showcase`. Do not manually seed after V13 or change migration versions. For a confirmed disposable personal install, coordinate a clean schema reprovision and rerun with both locations; preserve any data that matters first. |
| Flyway says applied V12 is not resolved locally | Supply **both** migration locations to this run/test/IDE configuration. |
| Missing placeholder or JWT configuration error | Section 4's variables must be present in the process launching Java; a `.env` file alone is insufficient. Generate valid Base64 with at least 32 decoded random bytes. |
| Port 8080 / 8000 already in use | Check for your other server/IDE terminal and stop that instance normally. Do not kill an unidentified process. Keep frontend API origin, CORS and server ports consistent if you deliberately change ports. |
| LiveReload error on 35729 | Use the current `npm.cmd run serve -- --build=false` script, which disables frontend LiveReload. Avoid launching plain `ojet serve` with conflicting defaults. |
| UI is stale after editing | Stop frontend serving, run `npm.cmd run build`, restart serve, then hard-refresh. File watching is disabled. |
| API connection refused / CORS error | Check backend health and the actual frontend origin. Use `http://localhost:8000` and backend 8080, not a file URL, 8001 mock server or a different hostname. |
| Login works but refresh fails on local HTTP | Check `SAFEPAY_REFRESH_COOKIE_SECURE=false`, consistent `localhost` URLs and that the new backend process received the setting. Clear only this site's stale session state and sign in again if needed. |
| Demo login fails | Use the matching seed-guide email/password. Verify V12 succeeded and check whether tests changed that user. Five failed logins trigger a temporary 15-minute lock; do not repeatedly guess. |
| HTTP 401 / 403 | 401 usually needs sign-in/session recovery; 403 indicates insufficient authority. Check the active persona and ownership rather than disabling security. |
| New registration has no account | Expected current behavior: registration does not create or fund an account. Use a seeded customer for payment testing. |
| Payment stays `PROTECTED` after countdown | Confirm the protection scheduler is enabled and inspect backend errors/server deadline. The browser timer is not authoritative. |
| Payment stays `RELEASED` | Confirm settlement is enabled, the correct clearing ID is configured, and inspect attempts/exceptions. A manual-review/retry-exhausted item is not automatically equivalent to a fresh eligible payment. |
| `outboundClearingAccountId is required when enabled` | Run section 8's query and pass its ID through the explicit property argument. A commented environment alias is not active configuration. |
| OTP not delivered / delivery unavailable | Check email-enabled flag, controlled recipient, sender/from, app password, SMTP access and backend delivery errors. Check spam. Health UP does not prove mail works; do not expose codes or mail passwords in reports. |
| OTP rejected / resend unavailable | Check latest transaction-specific code, 5-minute validity, attempt count, 30-second cooldown and issue limit. Do not disable these checks. |
| Frontend test cannot find `compare-snapshots.cjs` | Restore the tracked helper at `integration-evidence/compare-snapshots.cjs` from the same project revision. |
| Oracle integration tests fail while unit tests pass | Verify dedicated DB credentials, both Flyway locations, schema/grants and stopped background workers. Inspect the first failing Surefire report; a compile-only pass is not an integration pass. |

If something still fails, share the command used, tool versions, the first relevant exception, role/transaction state, and a redacted request/response. Omit passwords, authorization headers, cookies, JWT signing keys and OTP values.

## 13. Project map and further reading

| Path | Purpose |
| --- | --- |
| `backend/pom.xml` | Java/Spring dependencies, compiler and test configuration. |
| `backend/src/main/java/com/ofss/` | Controllers, services, repositories, security and schedulers. |
| `backend/src/main/resources/application.properties` | Runtime property names and defaults. |
| `backend/src/main/resources/db/migration/` | Versioned schema/reference-data migrations and repeatable grants. |
| `backend/src/main/resources/db/showcase/` | Optional V12 demonstration dataset. |
| `backend/src/test/` | Unit, API/security and Oracle integration tests. |
| `frontend/SafePayJet/src/ts/views/` | Frontend page templates. |
| `frontend/SafePayJet/src/ts/viewModels/` | Page behavior. |
| `frontend/SafePayJet/src/ts/services/` | API contracts, authentication and recovery adapters. |
| `frontend/SafePayJet/src/css/` | Shared presentation styles. |
| `frontend/SafePayJet/tests/` | Frontend verification. |
| `integration-evidence/` | Historical integration reports, pending checks and supporting tools. |

Useful references:

- [Backend and database context](SafePay_Backend_DB_Context.md)
- [Updated decision register](Documentation%20for%20SafePay/UPDATED_Decision_Register.md)
- [Updated implementation plan](Documentation%20for%20SafePay/UPDATED_implementation_plan.md)
- [Seed catalogue, demo passwords and reconciliation queries](Documentation%20for%20SafePay/SafePay_V1_Data_Seed_Complete_Guide.md)
- [Backend understanding guide](Documentation%20for%20SafePay/SafePay_Master_Backend_Understanding_Guide.md)
- [Integration implementation status](integration-evidence/IMPLEMENTATION_STATUS.md)
- [Pending live acceptance tests](integration-evidence/PENDING_USER_TESTS.txt)

Some historical documents reference old folder locations, earlier frontend versions, or IDs from the original database. Use this README's current startup sequence and the actual source/configuration for onboarding; treat historical test evidence as a record of that run rather than a fresh-machine guarantee.
