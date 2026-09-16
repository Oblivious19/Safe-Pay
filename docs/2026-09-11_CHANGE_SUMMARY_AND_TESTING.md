# SafePay changes and testing guide

11 September 2026 · Phase 1 · Team handover

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

Today we completed the User, Role and Account foundation, made registration compatible with it, added basic session login, and standardized the customer account and transaction endpoints. This guide explains what changed and how teammates can verify it. It does not authorize database resets or changes to existing balances.

## What we completed

### Database foundation

- `Database/08_user_account_foundation.sql` adds ROLES, seeds CUSTOMER and ADMIN, and evolves USERS and ACCOUNT while preserving existing IDs, balances and password hashes.
- USERS has a role, lifecycle status, password hash, login tracking fields and timestamps. User statuses are ACTIVE, LOCKED, SUSPENDED and INACTIVE.
- ACCOUNT has an independent account number, type, lifecycle status and timestamps. Account types are SAVINGS and CURRENT; statuses are ACTIVE, BLOCKED and CLOSED.
- User and account IDs still use their original sequences. Role IDs use SEQ_ROLE_ID. Customer-facing account numbers use SEQ_ACCOUNT_NUMBER, independently of ACCOUNT_ID.
- Unique constraints prevent duplicate email, phone and account numbers and more than one account for a user. A unique USER_ID enforces at most one account; registration creates the required account in the same transaction as the user.
- The old minimum-balance database check was removed manually; the nonnegative balance check remains. The transaction service still requires INR 5,000 to remain after a transfer. These are different rules.
- `Database/09_registration_password_transition.sql` makes the legacy PASSWORD column nullable. It preserves the column and its existing values; PASSWORD_HASH stays mandatory. No synchronization trigger or dual-password write was added.

The local migration and PASSWORD nullable / PASSWORD_HASH mandatory result were manually verified. Teammates must check their own database before applying anything. Do not rerun schema 07 or all migration sections blindly against an already migrated database. See `docs/USER_ACCOUNT_DATABASE_SETUP.md` for the earlier setup guide.

### Java domain and registration

- Added Role, UserStatus, AccountStatus and AccountType. User maps to USERS, Account maps to the singular ACCOUNT table, and Role maps to ROLES.
- User has a many-to-one Role relationship. Account has a one-to-one User relationship through unique USER_ID.
- The canonical Java password mapping is PASSWORD_HASH, not PASSWORD. BCrypt hashing remains in the service.
- Registration assigns CUSTOMER, ACTIVE, zero failed attempts, null lock/login dates and current created/updated timestamps.
- Registration creates one linked SAVINGS / ACTIVE account with the existing INR 5,000 demo balance and a sequence-generated account number. The operation is transactional.
- The request field is `password`, containing the raw password. Never send `passwordHash` or a precomputed hash. Passwords and hashes are not returned.
- Missing or blank password returns 400. Duplicate email and phone detected by the service return 409 with clear messages. Database-detected uniqueness races receive a generic 409; raw SQL and Oracle constraint names are not returned.
- BooleanToYNConverter already existed before the User/Account work. Only TransactionDb.authenticationRequired uses it to map Java boolean to Oracle CHAR(1) Y/N. It is not a User or Account converter.

### Basic login

- POST /api/auth/login accepts email and raw password, verifies BCrypt and reads the user's database status.
- Unknown email or wrong password returns 401 with the same message. Valid credentials for LOCKED, SUSPENDED or INACTIVE users return 403.
- Valid ACTIVE users receive a server-side Spring Security session. The session ID is rotated when an existing session is used, and a safe principal is stored without credentials or a JPA User entity.
- LAST_LOGIN_AT is updated. The response contains only userId, name, email, role and status.
- No JWT, logout, /me, failed-login counting, automatic lockout or role authorization was implemented.

### Customer resource endpoints

Account and transaction controllers now obtain identity from the Spring Security session. The transaction controller resolves the current email from the session's stable user ID before calling the existing owner-filtered service. Client-supplied identity cannot override it.

| Previous request | Current request |
| --- | --- |
| GET /api/accounts?userEmail=... | GET /api/accounts/current |
| GET /api/transactions?userEmail=... | GET /api/transactions |
| POST /api/transactions/initiate?... | POST /api/transactions with JSON |
| GET /api/transactions/{id}?userEmail=... | GET /api/transactions/{id} |
| POST /api/transactions/{id}/cancel?userEmail=... | POST /api/transactions/{id}/cancel |

GET requests have no body. The optional `state` transaction filter remains; it is not caller identity. The old unrestricted account list and ID-based account CRUD mappings were retired. Customer registration remains the normal way to create an account.

Transaction creation validates the request DTO and verifies that fromAccountId belongs to the session user before entering the existing service. Detail and cancellation use the existing owner-filtered transaction lookup. Another customer's resource returns 404 rather than exposing whether it exists.

## Verification completed so far

| Verification | Recorded result |
| --- | --- |
| Domain and Oracle validation stage | 11 tests passed; Oracle application context started with ddl-auto=validate and read foundation entities |
| Registration service stage | 16 tests passed, including the then-existing Oracle validation test |
| Registration validation stage | 17 selected registration/domain tests passed |
| Basic login stage | 8 focused tests passed |
| Endpoint standardization and latest rerun | Compilation and 15 selected tests passed: 7 ownership tests and 8 login tests |
| Manual registration | Reported working; new users appeared in Oracle |
| Manual login | Screenshot showed 200, safe five-field response and one cookie for Manual Test User |
| Manual current-account and standardized transaction flow | Still to be exercised using the instructions below |

These counts describe separate runs, not one combined total. The endpoint tests use mocked repositories and the real transaction service; they do not prove a live Oracle payment succeeded. Earlier Oracle validation did not start an HTTP listener because of a local Java loopback issue. The later manual login did reach the running backend.

## Prepare your test environment

1. Use a local demo database. Transaction POST requests can change simulated balances and transaction state; the scheduler can also settle protected transactions.
2. Confirm migrations 08 and 09 have been applied to your database. Do not modify schema or balances just to make a test pass.
3. Start the updated backend with Java 17 and Oracle schema validation enabled. Confirm the console says SafePayApplication started and the server is listening on port 8080.
4. Check that Eclipse is running the updated project, not an older copy in a different workspace. Earlier work used both Desktop/SafePay and an Eclipse workspace copy.
5. In Postman, use `http://localhost:8080` consistently. Switching between localhost and 127.0.0.1 may use a different cookie scope.
6. Keep passwords and session/CSRF token values out of screenshots, Git and shared reports. Example credentials below are for a new local demo user, not real credentials.

## Test registration

POST http://localhost:8080/api/auth/register

Select Body → raw → JSON. Use an email and phone not already registered:

```json
{
  "name": "Team Test User",
  "email": "teamcheck01@safepay.test",
  "phone": "9876501201",
  "password": "DemoOnly@12345"
}
```

Expected: 201 Created with userId, email and a success message. Save the returned userId and the original test password locally. A new account should have SAVINGS type, ACTIVE status and balance 5000.00.

Negative checks:

| Change to request | Expected result |
| --- | --- |
| Remove password, use null, or use spaces only | 400, message Password is required |
| Send passwordHash instead of password | 400; this is not the API password field |
| Repeat the successful email | 409, message Email is already registered |
| Use a fresh email but repeat the successful phone | 409, message Phone number is already registered |

A duplicate detected only by Oracle can return 409 with `A record with these details already exists`. A non-uniqueness database failure returns a safe 500 message; do not assume every database error is a duplicate.

## Test login

POST http://localhost:8080/api/auth/login

Body → raw → JSON:

```json
{
  "email": "teamcheck01@safepay.test",
  "password": "DemoOnly@12345"
}
```

Use the exact credentials from a successful registration. Example emails in instructions are not automatically created users.

Expected: 200 OK. Example response, with an illustrative ID:

```json
{
  "userId": 123,
  "name": "Team Test User",
  "email": "teamcheck01@safepay.test",
  "role": "CUSTOMER",
  "status": "ACTIVE"
}
```

Open the Cookies tab and confirm JSESSIONID exists. Keep the cookie in Postman's cookie jar. It identifies the server-side session; it is not a JWT and must not be shared.

Try a wrong password and an unregistered email: each must return 401 with `Invalid email or password`. For status testing, use prepared local LOCKED, SUSPENDED and INACTIVE users with known valid passwords; each must return 403. Do not change shared user statuses for this guide. Automated tests already cover those cases.

LAST_LOGIN_AT should change only for successful login. Failed-login attempts are deliberately not incremented yet. A rejected login is not a logout operation; use an isolated cookie jar when testing an anonymous client.

## Get the current account and CSRF token

GET http://localhost:8080/api/accounts/current

Use Body → none, no identity parameters, and the same login session. Expected: 200 with one account object, not an array. Check accountId, userId, accountNumber, accountType, balance and status against the logged-in user. Created and updated timestamps are also returned; the full User and password are excluded.

From the response Headers tab, copy `X-CSRF-TOKEN` into a private Postman variable or the next request header. This token protects session-based writes from cross-site request forgery. A fresh GET can retrieve it again. Never put it in the URL.

If no account exists for the logged-in user, expect 404. If not logged in, expect 401 for this GET.

## List and view transactions

GET http://localhost:8080/api/transactions

No body or identity parameters are needed. Expected: 200 and an array containing only this customer's transactions; `[]` is valid when none exist.

To view one returned item:

GET http://localhost:8080/api/transactions/17

Replace 17 with an actual transactionId. Expected: 200 for an owned transaction, 404 for a nonexistent or another customer's transaction. Responses include transaction ID/reference, amount, purpose, source account, beneficiary details, state, risk information and timestamps.

## Create a transaction

First check the test data. You need an existing ACTIVE beneficiary owned by this same customer and a source account with sufficient funds. Beneficiary APIs were not standardized in this task; do not use their current identity parameters as proof of security.

IMPORTANT: A new account starts with INR 5,000, and the current service requires INR 5,000 to remain. It cannot make any positive transfer from that starting balance. To transfer INR 5,000, use an approved pre-funded demo account with at least INR 10,000 and a known password. Otherwise expect 400 for insufficient remaining balance. This behavior was preserved, not changed today.

POST http://localhost:8080/api/transactions

Headers:

```text
Content-Type: application/json
Idempotency-Key: team-low-001
X-CSRF-TOKEN: <token from the current-account GET>
```

Retain the session cookie. Replace both example IDs with actual owned resources:

```json
{
  "fromAccountId": 1000001,
  "beneficiaryId": 2001,
  "amount": 5000,
  "purpose": "Low risk demo"
}
```

Expected for valid data: 200 with the transaction details. This amount is LOW risk and should be SETTLED. Read the current account again; for a genuinely new settled payment the balance should decrease by the amount.

An Idempotency-Key distinguishes a logical payment. Retry the same request with the same key to check that it returns the existing transaction without a second debit. Use a new key for a genuinely new payment. Do not reuse a key when changing the amount, account or beneficiary; changed-payload conflict detection was not added today.

Do not send userEmail or userId. They are not DTO fields and do not determine identity. The server checks fromAccountId against the session user.

## Test cancellation

Use an existing owned transaction that is currently PROTECTED and whose protection window has not expired. LOW payments settle immediately and cannot be cancelled. Current MEDIUM protection is 10 seconds and HIGH is 60 seconds, so have the request ready; account funding and beneficiary requirements still apply.

POST http://localhost:8080/api/transactions/17/cancel

Replace 17. Body → none. Send the session cookie, X-CSRF-TOKEN and an Idempotency-Key such as `team-cancel-001`.

Expected for an eligible transaction: 200 with CANCELLED state. An owned settled or expired payment returns 400. Another customer's transaction returns 404. The existing cancellation logic was preserved, not redesigned.

## Verify ownership and request protection

Use two approved demo users with separate Postman cookie jars, or clear the test cookies and log in as the other user. A normal new Postman tab can still share cookies, so a new tab alone does not isolate identity.

| Check | Expected |
| --- | --- |
| GET current account without cookies | 401 |
| GET transactions without cookies | 401 |
| Add ?userEmail=another@example.com&userId=999 to current-account GET while logged in | Still only your account |
| Add another email to transaction-list GET | Still only your transactions |
| Create using another user's source account with valid session, CSRF and request body | 404, no payment created |
| Read or cancel another user's transaction with otherwise valid request | 404 |
| Create or cancel without a valid CSRF token | 403 before business logic |
| Use the retired GET /api/accounts list route while logged in | 404 |

These checks apply to the account and transaction routes changed today. They do not establish whole-application authorization. Unrelated user and beneficiary endpoints remain outside this security scope.

## Read only Oracle checks

Use the same database/schema as the backend. Replace the example email or ID with your test user's values. Do not select password hashes for screenshots.

```sql
SELECT u.user_id, u.email, r.role_name, u.status,
       u.failed_login_attempts, u.locked_until, u.last_login_at,
       u.created_at, u.updated_at,
       CASE WHEN u.password_hash IS NULL THEN 'MISSING'
            ELSE 'PRESENT' END AS hash_status
FROM users u JOIN roles r ON r.role_id = u.role_id
WHERE u.email = 'teamcheck01@safepay.test';

SELECT account_id, user_id, account_number, account_type,
       balance, status, created_at, updated_at
FROM account
WHERE user_id = 123;

SELECT column_name, nullable
FROM user_tab_columns
WHERE table_name = 'USERS'
  AND column_name IN ('PASSWORD', 'PASSWORD_HASH');

SELECT table_name, constraint_name, constraint_type,
       status, validated
FROM user_constraints
WHERE table_name IN ('ROLES', 'USERS', 'ACCOUNT')
ORDER BY table_name, constraint_type, constraint_name;
```

Expected password-column result: PASSWORD = Y (nullable), PASSWORD_HASH = N (required). PRESENT only confirms a stored value, not whether a supplied password matches BCrypt. Constraint types: P = primary key, R = foreign key, U = unique, C = check or NOT NULL. Review definitions when verifying what each constraint enforces; names alone do not prove correctness.

## Automated checks for teammates

From the Backend folder, use Java 17 and Maven. The latest focused verification was:

```text
mvn -Dtest=CustomerResourceTest,LoginControllerTest compile test
```

Expected: 15 tests, no failures/errors. Registration validation can be checked separately:

```text
mvn -Dtest=RegistrationValidationTest,UserRegistrationTest,UserAccountDomainTest compile test
```

The recorded registration run passed 17 tests. Use your installed Maven or functioning wrapper; this laptop required a cached Maven installation because the wrapper failed. These selected tests do not require Oracle. OracleDomainValidationTest is opt-in using `safepay.oracle.validation=true`; it requires your own Oracle configuration and is not part of the commands above. Keep ddl-auto=validate and never share database credentials.

## Common errors and what they mean

| Symptom | Check next |
| --- | --- |
| ECONNREFUSED 127.0.0.1:8080 | Backend is not listening; inspect startup console and confirm the updated project and port |
| 400 rawPassword cannot be null | Older validation code may be running, or password was sent as passwordHash; use password and restart the updated backend |
| 401 Invalid email or password | Verify the email exists in the backend's database and use the original registered password |
| 401 on current account or transaction list | Session cookie absent, expired or for a different hostname; log in again |
| 403 User is not active | Valid credentials but status is not ACTIVE |
| 403 on transaction POST | Check session and X-CSRF-TOKEN from the same session |
| 404 Account or Transaction not found | Check the ID and ownership; do not bypass this by supplying another email |
| 400 minimum balance message | Source balance minus amount must currently remain at least INR 5,000 |
| 409 duplicate details | Use a fresh email/phone or review existing registration; do not weaken unique constraints |
| 500 Unable to save the record | Read the backend console privately; it is not necessarily a duplicate |

## Main files involved

Paths below are relative to the SafePay repository root.

- Database: `Database/08_user_account_foundation.sql`, `Database/09_registration_password_transition.sql`.
- Domain: `Backend/src/main/java/com/ofss/beans/User.java`, `Account.java`, `Role.java`, `UserStatus.java`, `AccountStatus.java`, `AccountType.java`.
- Registration: `Backend/src/main/java/com/ofss/services/UserServiceImpl.java`, `AccountServiceImpl.java`; repositories `UserDao.java`, `RoleDao.java`, `AccountDao.java` under `com/ofss/repository`.
- Errors: `GlobalExceptionHandler.java`, `DuplicateEmailException.java`, `DuplicatePhoneException.java` under `Backend/src/main/java/com/ofss/excp`.
- Login: `LoginRequest.java`, `LoginPrincipal.java` under `com/ofss/beans`; `LoginService.java` under `com/ofss/services`; `LoginController.java` under `com/ofss/controller`; `LoginSecurityConfig.java` under `com/ofss/config`.
- Endpoints: `AccountController.java`, `TransactionController.java`; `TransactionRequest.java`; `CustomerResourceSecurityConfig.java` in their corresponding controller, beans and config packages.
- Project setup: `AGENTS.md`, `Backend/pom.xml`.
- Tests: `UserAccountDomainTest`, `OracleDomainValidationTest`, `UserRegistrationTest`, `RegistrationValidationTest`, `LoginControllerTest`, `CustomerResourceTest` under `Backend/src/test/java/com/ofss`.

## What remains outside this work

No logout, /me, lockout implementation, role/admin authorization, frontend changes, Beneficiary redesign, risk redesign or settlement redesign was added. The existing frontend still needs new URLs, a single-account response handler, session credentials and CSRF handling. Cross-origin browser use also needs credential-enabled CORS and exposure of the CSRF header; Postman does not enforce browser CORS.

Do not describe the entire app as secured yet. Complete the manual account/transaction checks on approved test data before proceeding to the next module.

## Test evidence checklist

- [ ] Confirm correct backend checkout and Oracle connection.
- [ ] Registration returns 201 and creates the expected user/account.
- [ ] Missing password returns 400; duplicate email and phone return 409.
- [ ] Login returns 200 with only five safe fields; JSESSIONID exists.
- [ ] LAST_LOGIN_AT is updated in Oracle after successful login.
- [ ] Current account belongs to the session user.
- [ ] Transaction list and details contain only owned resources.
- [ ] JSON creation succeeds on a funded approved fixture.
- [ ] Same-key retry does not debit twice.
- [ ] Foreign account/detail/cancel requests are rejected.
- [ ] Missing CSRF is rejected; eligible cancellation works.
- [ ] Record date, tester, fixture IDs, expected result, actual result and pass/fail without secrets.
