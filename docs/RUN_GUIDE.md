# SafePay: complete run and usage guide

Use **C:\Shreya\Safe-Pay**. This is the delivered, updated backend and frontend.
All commands below are for Windows **Command Prompt (cmd.exe)**. Keep backend
and frontend in separate terminals. This application simulates payments.

## 1. Check tools

Java 17 and Node/npm are already installed on this machine. The verified versions
are Java 17.0.11, Node 24.21.0 and npm 11.19.0.

```cmd
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
node --version
npm --version
```

These environment settings affect this terminal only. Run the Java settings in a
new backend terminal if its JAVA_HOME points to another installation.

## 2. Start/check Oracle

Open Windows Services with `services.msc`. Ensure these services are Running:

- OracleServiceFREE
- OracleOraDB23Home2TNSListener (the listener in use on this machine)

They were running when this guide was prepared. If stopped, an Administrator
Command Prompt can start them:

```cmd
net start OracleServiceFREE
net start OracleOraDB23Home2TNSListener
```

The backend reads `Backend/src/main/resources/application.properties`, including:

```properties
server.port=8080
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:FREE
```

Keep your existing username/password in that file. The `oracle` profile validates
the migrated schema and persists application data in Oracle. Do not use `local`
for the existing Oracle users: that profile is a disposable in-memory H2 database.
Migrations 12, 14 and 15 have already been applied to this configured database.
Do not rerun old schema/creation scripts on it.

## 3. Start the backend (terminal 1)

```cmd
cd /d C:\Shreya\Safe-Pay
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
start-backend.cmd
```

This runs the supplied `dist/SafePay.jar` with external database settings and the
Oracle profile. Wait for the Spring Boot startup completion and Tomcat port 8080
messages. Leave this terminal open. A startup stack trace means the service has
not started successfully, even if the JPA/database connection initialized.

To check from another terminal:

```cmd
curl.exe -i http://localhost:8080/api/accounts
```

A 401 response is expected without a login cookie and confirms that the protected
API is reachable. The backend does not provide the UI at its root URL.

### Development alternative: run current Java source

Use this instead of the packaged launcher; do not run both on port 8080.

```cmd
cd /d C:\Shreya\Safe-Pay\Backend
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=oracle
```

The packaged launcher keeps running the supplied JAR after source edits; the Maven
command compiles and runs current source. The Maven wrapper may download its tools
and dependencies on the first use. In CMD, do not surround `-D...` with single quotes.

## 4. Start the frontend (terminal 2)

```cmd
cd /d C:\Shreya\Safe-Pay
start-ui.cmd
```

Equivalent direct command:

```cmd
cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
npx ojet serve --server-port=8000
```

Use `--server-port=8000`, not `--port=8000`. Wait for build/serve success and leave
this terminal running. Dependencies already exist in the delivered folder.
If a copied project reports that Oracle JET is not installed, restore from here:

```cmd
cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
npm ci
npx ojet restore
npx ojet serve --server-port=8000
```

## 5. Open and sign into the UI

Open **http://localhost:8000/login**. Use `localhost` consistently for backend and
frontend; do not open index.html directly. Browser and Postman sessions are separate.
The UI automatically sends the session cookie and CSRF header for protected actions.

- Existing user: enter the registered email and password, or switch to mobile
  number/password and enter the registered 10-digit number with the same password.
- New customer: open http://localhost:8000/register; enter name, unique email,
  Indian mobile number, password and confirmation, then sign in.
- New registration creates one SAVINGS account with INR 5,000. Existing migrated
  users retain every account. No default user/admin password was installed.

## 6. Customer workflow

1. **Dashboard**: choose the account and check balance/status.
2. **Beneficiaries**: select that source account and add a recipient. Example:
   name `Demo Recipient`, account `123456789012`, IFSC `HDFC0001234`. Beneficiaries
   are scoped to the selected source account. Details/removal/reactivation remain.
3. **Funds**: available funds must cover a payment plus the INR 5,000 minimum and
   other held payments. A new account with only INR 5,000 cannot send a payment
   until funded. An ADMIN can add an explicit simulated credit in the admin UI.
4. **Send Money**: select source account and its beneficiary, enter amount/purpose,
   click Review payment, check details, then Confirm payment.
5. **Transactions**: inspect status and the saved reason. Pending entries refresh
   every five seconds. Cancel while the server still allows cancellation.
6. **Hard hold**: use Verify payment, enter the current SafePay password and select
   Verify and settle. There is no automatic timed release for HARD_HOLD.
7. **Profile**: edit customer name/email/mobile or optionally change password; leave
   the password blank to retain it.

| Amount | Tier | Behavior |
|---|---|---|
| Above zero through INR 10,000 | LOW | Immediate settlement |
| Above INR 10,000 through INR 50,000 | MEDIUM | 10-second cancellable hold |
| Above INR 50,000 through INR 1,00,000 | HIGH | 60-second cancellable hold |
| Above INR 1,00,000 | VERY_HIGH | HARD_HOLD; password verification |

Try amounts 5,000 / 20,000 / 75,000 / 1,25,000 after funding the account sufficiently.
These operations change the simulated ledger. A cancellation must reach the server
before its deadline. A countdown on screen is only an estimate of remaining time.
For an uncertain payment response, use Retry same payment; after a full browser
reload, check Transactions before creating another payment.

## 7. Administrator workflow

Sign in with an account assigned the ADMIN role; it opens `/admin`. Customer login
cannot access this page. The migration preserved users as customers and did not
promote any user or create a default administrator.

- **Users / details**: inspect user information and all their accounts.
- **User status**: select an allowed status and save; status actions are audited.
- **Create administrator**: provisions another ADMIN login with an initial password
  and no customer bank account. Use public Registration for a customer.
- **Bank interest credit**: choose a customer's account, enter the amount to add,
  check confirmation and submit. An uncertain result must use Retry same credit.
- **Edit balance / type**: balance is the desired TOTAL, not an amount to add; held
  payments and the minimum balance are still enforced.
- **Block/unblock**: controls account availability.
- **Reports**: summary and daily date range (up to 366 days).

### Optional first ADMIN setup (database owner, local simulation only)

Skip this if an ADMIN already exists. No role change has been performed for you.
To avoid changing the migrated customers, register a separate user intended only
for administration, then log out. In SQL Developer, connect to the same schema as
application.properties and inspect the new user and roles:

```sql
SELECT u.user_id, u.email, u.status, r.role_name
FROM users u JOIN roles r ON r.role_id = u.role_id
WHERE u.email = 'YOUR_DEDICATED_ADMIN_EMAIL';
SELECT role_id, role_name FROM roles WHERE role_name = 'ADMIN';
```

Replace both placeholders below with that dedicated user's confirmed values.
This explicitly grants administrator privileges; it does not change the password
or remove the account created at registration.

```sql
UPDATE users
SET role_id = (SELECT role_id FROM roles WHERE role_name = 'ADMIN'),
    updated_at = LOCALTIMESTAMP
WHERE user_id = YOUR_DEDICATED_ADMIN_USER_ID
  AND email = 'YOUR_DEDICATED_ADMIN_EMAIL'
  AND status = 'ACTIVE';
```

Verify exactly one intended row was updated. Use COMMIT if correct, otherwise
ROLLBACK. Log in again in the UI with that user's existing password. Further
administrators can be created from the admin UI without direct SQL.

## 8. Optional Postman check

1. POST `http://localhost:8080/api/auth/login` with JSON
   `{"email":"YOUR_EMAIL","password":"YOUR_PASSWORD"}` (or phone/password).
2. Keep the returned JSESSIONID cookie in Postman.
3. CUSTOMER: GET `/api/accounts`; ADMIN: GET `/api/admin/users`.
4. Copy that GET response's `X-CSRF-TOKEN` header into the same request header on
   protected writes. Set Content-Type to application/json for JSON bodies.
5. Payments, cancellations, verification and credits require their documented
   Idempotency-Key headers. Reuse a key only for retries of the same operation.

Full examples: `Backend/updated-zip-requests.http` and
`Backend/enhanced-requests.http`. Account API URLs use internal accountId values,
not displayed account numbers. `/accounts` without `/api` is not a valid API route.

## 9. Troubleshooting

| Symptom | Action |
|---|---|
| Unknown Maven lifecycle phase containing single quotes | Use the CMD Maven command above without single quotes |
| `Option port not valid` | Use `--server-port=8000` |
| Oracle JET not installed | Check the Shreya folder, then npm ci / npx ojet restore |
| Oracle connection refused / listener error | Check the two Oracle services and connection settings |
| HTTP 401 | Sign in in that browser/Postman session |
| HTTP 403 / CSRF | Confirm the role; Postman needs same cookie and current CSRF header |
| HTTP 404 for `/accounts` | Use `/api/accounts` |
| Insufficient funds / minimum balance | Fund the chosen account through ADMIN; allow for held payments |
| Port already in use | Stop the existing SafePay server with Ctrl+C before starting another |
| Java `SocketException: Invalid argument: connect` during startup | Use Java 17 in a regular external CMD terminal; startup from the agent host previously failed here, so do not treat that attempt as a running backend |

Read-only commands to identify listeners:

```cmd
netstat -ano | findstr :8080
netstat -ano | findstr :8000
```

## 10. Stop, restart and development checks

Press Ctrl+C in each server terminal; if CMD asks to terminate the batch job,
answer Y. Restart the same launchers when needed. Oracle data persists across
backend restarts; sign in again because server sessions are not retained.

Optional developer checks (separate from normal startup):

```cmd
cd /d C:\Shreya\Safe-Pay\Backend
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
.\mvnw.cmd test
```

```cmd
cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
npm test
npm run typecheck
npm run build
```

The ordinary backend test command uses isolated test databases; opt-in Oracle
checks are a separate workflow. A frontend build alone does not start a web server.
