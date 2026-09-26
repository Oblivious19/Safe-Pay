# SafePay — Team Setup & Run

Follow this order on a **fresh local database**. Keep the backend stopped until database setup and seeding finish.

## 1. Check prerequisites

Install beforehand:

- Oracle Database Free with service `FREEPDB1` running.
- Oracle SQL Developer.
- JDK **17** and Eclipse/STS with Maven support.
- Node.js **22** with npm. Some locked frontend dependencies require Node 22 despite the package’s older minimum declaration.

Check in PowerShell:

```powershell
java -version
node --version
npm.cmd --version
```

Throughout this guide, `<PROJECT>` means your local `Safe-Pay` root folder.

## 2. Create the SAFEPAY_TEAM schema

In SQL Developer, connect using:

| Setting | Value |
|---|---|
| Username | `SYSTEM` |
| Password | Your local Oracle SYSTEM password |
| Host | `localhost` |
| Port | `1521` |
| Connection type | Basic |
| Service name | `FREEPDB1` |
| Role | Default |

Open and run **the entire file with F5 / Run Script**:

```text
<PROJECT>\scripts\script_for_creating_safepayTeam_schema.sql
```

**Password warning:** At `New SAFEPAY_TEAM password:`, enter your own schema password—not another teammate’s password or the application login password. Follow the script’s permitted characters: letters, numbers, `#`, `$`, `_`.

Expected results:

- `User SAFEPAY_TEAM created`
- Grant succeeded
- `SAFEPAY_TEAM | OPEN`
- Exactly these four privileges:

```text
CREATE SEQUENCE
CREATE SESSION
CREATE TABLE
CREATE VIEW
```

Do not use `GRANT ALL` or grant DBA access.

**Terminal alternative**, if SQL*Plus is installed:

```powershell
cd "<PROJECT>"
sqlplus SYSTEM@//localhost:1521/FREEPDB1
```

Enter the SYSTEM password when prompted, then:

```sql
@"scripts/script_for_creating_safepayTeam_schema.sql"
EXIT
```

This runs the same schema creation and grants, with the same password prompt.

## 3. Connect as SAFEPAY_TEAM and create tables

Create another SQL Developer connection:

```text
Connection name: SafePay Team
Username:        SAFEPAY_TEAM
Password:        The schema password chosen above
Hostname:        localhost
Port:            1521
Service name:    FREEPDB1
Role:            Default
```

Click **Test → Success → Connect**.

Using this connection, run with **F5**:

```text
scripts\consolidated_manual_SQL_script.sql
```

Expected final marker:

```text
SAFEPAY_TEAM_STRUCTURE_COMPLETE
```

This creates **6 tables, 7 sequences, 2 reporting views**, constraints/indexes and the `CUSTOMER`/`ADMIN` roles.

**Run once on an empty schema.** If it fails, stop and inspect the first error. Oracle table-creation statements are not undone by rollback; do not blindly rerun or drop the schema.

## 4. Seed and verify

Still connected as **SAFEPAY_TEAM**, open and run the entire file with **F5**:

```text
scripts\data_seed_script.sql
```

Expected message:

```text
Seed successful: 1 admin, 4 customers, 4 savings accounts.
```

All five users are `ACTIVE`. The four customer accounts are `ACTIVE`, `SAVINGS`, with **₹2,00,000 each**.

| User | Role | Email | Mobile | Account number* |
|---|---|---|---|---|
| Vikram Bhat | ADMIN | vikram.bhat@gmail.com | 9876543210 | No account |
| Priya Nair | CUSTOMER | priya.nair@gmail.com | 9876543211 | 987600000001 |
| Kavya Sharma | CUSTOMER | kavya.sharma@gmail.com | 9876543212 | 987600000002 |
| Arjun Menon | CUSTOMER | arjun.menon@gmail.com | 9876543213 | 987600000003 |
| Neha Iyer | CUSTOMER | neha.iyer@gmail.com | 9876543214 | 987600000004 |

\*Expected on a fresh, uninterrupted setup with no earlier sequence usage.

**Shared demo login password:**

```text
SafePay@Test123
```

**Do not manually edit `c_password_hash` or replace it with plaintext.** There is no forgotten-password reset flow. Signed-in customers can change their password through the existing profile functionality.

The seed requires empty `USERS` and `ACCOUNT` tables. It does not seed beneficiaries, transactions or audit events.

Now run:

```text
scripts\verification_script.sql
```

Expected results immediately after seeding, before using the application:

| Verification query | Expected output |
|---|---|
| Connected user / container | `SAFEPAY_TEAM / FREEPDB1` |
| Object counts | `SEQUENCE 7`, `TABLE 6`, `VIEW 2` |
| Invalid objects | No rows |
| Disabled/unvalidated constraints | No rows |
| Selected check constraints | Four rows: failed-logins ≥ 0, nonblank audit action, balance ≥ 0, correct risk policy |
| `PAYMENT_CATEGORY` nullable | `Y` |
| User/account listing | Five rows; administrator account fields are NULL |
| Report totals | One row; all nine totals are `0` |

The same verification file can run before seeding; its user listing will then be empty.

## 5. Run the backend in Eclipse / STS

1. **File → Import → Maven → Existing Maven Projects**.
2. Select `<PROJECT>\Backend`, select its `pom.xml`, then **Finish**.
3. Right-click project **SafePay → Maven → Update Project → OK**. Ensure its JRE is **JDK 17**.
4. Open **Run → Run Configurations → Spring Boot App → New**. If unavailable, use **Java Application → New**.
5. Set:

```text
Name:       SafePay-Team
Project:    SafePay
Main type:  com.ofss.SafePayApplication
JRE:        JDK 17
Working directory: <PROJECT>\Backend
```

6. In **Environment**, add each variable separately:

```ini
SPRING_PROFILES_ACTIVE=oracle
SPRING_DATASOURCE_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
SPRING_DATASOURCE_USERNAME=SAFEPAY_TEAM
SPRING_DATASOURCE_PASSWORD=YOUR_OWN_SCHEMA_PASSWORD
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false
SERVER_PORT=8080
```

Replace the password placeholder. **Do not surround environment values with quotes.**

7. Select **Append environment to native environment**. Leave program arguments empty; remove inherited arguments that override the datasource, profile or schema-generation settings. Keep the configuration local, not shared with credentials.
8. Keep **Allocate console** enabled, then **Apply → Run**.

Expected console messages include:

```text
The following ... profile is active: "oracle"
Tomcat started on port 8080
Started SafePayApplication ...
```

There must be no `APPLICATION FAILED TO START`, Oracle connection error or schema-validation exception.

These settings override the checked-in SYSTEM defaults and connect to **SAFEPAY_TEAM**. `validate` checks existing tables; it does not create them. No Flyway, JWT or email configuration is required for this setup.

Use the imported source project; avoid the root `start-backend.cmd`, which launches a prebuilt JAR.

## 6. Run the frontend in VS Code

Open a PowerShell terminal:

```powershell
cd "<PROJECT>\Frontend\SafePayJet"
Test-Path .\node_modules
Test-Path .\node_modules\.bin\ojet.cmd
```

If both return `True`:

```powershell
npm.cmd run serve
```

If dependencies are missing, first run:

```powershell
npm.cmd ci
npm.cmd run serve
```

Use **`npm.cmd run serve`** if `ojet serve` is not recognised. It uses the project’s local CLI and configured port **8000**.

Open:

```text
Customer: http://localhost:8000/login
Admin:    http://localhost:8000/admin/login
```

The server does not automatically open a browser, and live reload is disabled. Refresh manually after changes. Keep both frontend and backend running.

## 7. Quick troubleshooting

| Problem | Check |
|---|---|
| `ORA-01017` | SAFEPAY_TEAM username and your chosen schema password |
| `ORA-12514` / connection refused | Oracle listener/database running; service `FREEPDB1`, port `1521` |
| Schema already exists / not empty | Stop; inspect the existing setup instead of recreating it |
| Missing-table/schema-validation error | Run structure script under SAFEPAY_TEAM; check backend environment |
| Seed refuses to run | USERS or ACCOUNT already contains data; do not reseed over it |
| Port already in use | Stop the duplicate server; retain ports 8080/8000 |
| Frontend cannot reach backend | Use `localhost:8000`; verify backend is running on `localhost:8080` |
| npm engine/dependency error | Check Node 22 and run `npm.cmd ci` in `Frontend\SafePayJet`; retain the lockfile |

---