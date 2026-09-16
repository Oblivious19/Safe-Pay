# SafePay backend verification and Postman guide

Verified on 13 September 2026. SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

## 1. Result and scope

**Compilation succeeded. Automated tests: 322 discovered, 321 passed, 0 failed, 0 errors, 1 skipped.** The local HTTP server responded to six safe probes. Authenticated live workflows, Oracle validation and live payments were **not verified** because approved credentials/fixtures were unavailable in this task.

Only the three files in this Postman folder were created. No application code, test source, configuration, database schema, migration, user/account status or beneficiary/payment data was changed by this task. Existing uncommitted work was preserved. Maven generated ordinary build/test outputs.

Your statement that all migrations are already executed is acknowledged. Nothing was rerun. We did not independently inspect the live Oracle schema, and HTTP responses alone do not prove database compatibility.

### Artifacts

- [Importable collection](<C:/Users/Shreya Ojha/Desktop/SafePay/docs/postman/SafePay_Workflow.postman_collection.json>) — 8 folders, 35 requests.
- [SafePay Local environment](<C:/Users/Shreya Ojha/Desktop/SafePay/docs/postman/SafePay_Local.postman_environment.json>) — blank credentials/tokens and explicit safety flags.
- [This report](<C:/Users/Shreya Ojha/Desktop/SafePay/docs/postman/SAFEPAY_POSTMAN_TEST_REPORT.md>).

## 2. Actual API inventory

All paths below are relative to `http://localhost:8080`. No request uses a GET body.

Conventions:

- **Public**: no login required. **Customer**: authenticated `LoginPrincipal` with CUSTOMER role. **Admin**: authenticated principal with ADMIN role.
- Customer/admin endpoints return **401** without a session and **403** for the wrong role.
- JSON bodies require `Content-Type: application/json`. `Accept: application/json` is used in the collection.
- Authentication uses Postman's **cookie jar**, never a manually supplied userEmail/userId, JWT or Authorization header.
- **CSRF** means the `X-CSRF-TOKEN` request header for the same current session. It belongs in Headers, not Params.
- “—” means no body, query or special header of that kind is required. Braced path IDs are resource selectors, not caller identity.
- Status lists describe source behavior; not all were exercised against the running server. Infrastructure errors can additionally produce 500.

| Method and exact path | Path/query parameters | Body | Access / CSRF / extra header | Success; important errors | Effect |
|---|---|---|---|---|---|
| POST /api/auth/register | — | R1 | Public; no CSRF | 201; 400 missing/blank password, 409 duplicate email/phone | Creates user and account |
| POST /api/auth/login | — | R2 | Public; no CSRF | 200; 401 invalid/missing credentials, 403 locked/disabled | Creates/changes session; updates login metadata/counters |
| POST /api/auth/logout | — | — | Existing session: CSRF; anonymous: no CSRF | 200; 403 authenticated without valid CSRF | Invalidates current session |
| GET /api/users/current | — | — | Customer; no CSRF | 200; 401/403/404 | Read-only profile |
| GET /api/users/{userId} | userId: own resource ID | — | Customer; no CSRF | 200; 401/403/404 including another user | Read-only profile |
| GET /api/accounts/current | — | — | Customer; no CSRF | 200; 401/403/404 | Read-only own account |
| GET /api/beneficiaries | — | — | Customer; no CSRF | 200; 401/403 | Read-only own ACTIVE beneficiaries |
| POST /api/beneficiaries | — | R3 | Customer; CSRF | 201; 400 invalid/extra fields, 401/403, 409 duplicate | Creates owned beneficiary |
| GET /api/beneficiaries/{beneficiaryId} | beneficiaryId | — | Customer; no CSRF | 200; 401/403/404 missing, foreign or inactive | Read-only |
| DELETE /api/beneficiaries/{beneficiaryId} | beneficiaryId | — | Customer; CSRF | 204; 401/403/404 | Soft-deactivates owned beneficiary; NOT executed/included in runner |
| GET /api/transactions | Optional `state`, e.g. PROTECTED | — | Customer; no CSRF | 200; 400 invalid state, 401/403/404 missing current user | Read-only own list |
| POST /api/transactions | — | R4 | Customer; CSRF; `Idempotency-Key` | 200; 400 validation/reserve/header, 401/403, 404 ownership, 409 eligibility/key conflict | Creates transaction; may debit immediately or later |
| GET /api/transactions/{transactionId} | transactionId | — | Customer; no CSRF | 200; 401/403/404 | Read-only own transaction |
| POST /api/transactions/{transactionId}/cancel | transactionId | — | Customer; CSRF; `Idempotency-Key` | 200; 400 missing header/invalid state/expiry, 401/403/404 | Cancels eligible owned transaction |
| GET /api/admin/users | — | — | Admin; no CSRF | 200; 401/403 | Read-only safe user directory |
| PATCH /api/admin/users/{id}/status | id: target user | R5 | Admin; CSRF | 200; 400 invalid body, 401/403/404, 409 transition conflict | Changes lifecycle status; NOT executed/included in runner |
| PATCH /api/admin/accounts/{id}/status | id: target account | R6 | Admin; CSRF | 200; 400 invalid body, 401/403/404, 409 transition conflict | Changes lifecycle status; NOT executed/included in runner |
| GET /api/admin/reports/transactions/summary | — | — | Admin; no CSRF | 200; 401/403, 503 unavailable reporting | Read-only Oracle view query |
| GET /api/admin/reports/transactions/daily | Required `from`, `to`: YYYY-MM-DD, inclusive, ordered, at most 366 days | — | Admin; no CSRF | 200; 400 date range, 401/403, 503 unavailable reporting | Read-only Oracle view query |

Legacy controller mappings still exist but are **denied by the security configuration**, not usable alternatives:

| Mapping | Controller input / nominal status if it were reachable | Actual configured behavior |
|---|---|---|
| GET /api/users | No body/query; list | Anonymous 401; authenticated denied 403 |
| POST /api/users | User entity body, nominal 201 | Anonymous 401; authenticated denied 403; do not use for registration |
| PUT /api/users/{userId} | userId path + User entity body, nominal 200 | Anonymous 401; authenticated denied 403 |
| DELETE /api/users/{userId} | userId path, no body, nominal 204 | Anonymous 401; authenticated denied 403; do not test deletion |

The blocked writes would also encounter normal authenticated CSRF checks. They are intentionally absent from the collection.

### Exact request bodies

Placeholders below are not credentials. Fill only local approved demo inputs inside Postman. Body scripts serialize them using JSON.stringify, so quotes in a password cannot break JSON.

**R1 — registration**
```json
{
  "name": "{{testName}}",
  "email": "{{testEmail}}",
  "phone": "{{testPhone}}",
  "password": "{{testPassword}}"
}
```

**R2 — login**
```json
{
  "email": "{{testEmail}}",
  "password": "{{testPassword}}"
}
```

**R3 — beneficiary**
```json
{
  "beneficiaryName": "{{beneficiaryName}}",
  "bankAccountNumber": "{{beneficiaryBankAccountNumber}}",
  "ifsc": "{{beneficiaryIfsc}}"
}
```

Name: nonblank, at most 100 characters. Account number: 1–30 digits. IFSC: 11 characters matching four letters, zero, six alphanumeric characters. Extra fields are rejected. Duplicate detection is per customer and includes an existing inactive registration.

**R4 — transaction**
```json
{
  "fromAccountId": {{accountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": {{testAmount}},
  "purpose": "{{testPurpose}}"
}
```

The collection generates the final valid JSON from these inputs. It never sends caller identity, risk tier, history, device signals, protection duration or transaction state.

**R5 — admin user status (catalog only; not executed)**
```json
{"status": "SUSPENDED"}
```
The supported transition pair is ACTIVE ↔ SUSPENDED. Use ACTIVE to reactivate a suspended user. LOCKED/INACTIVE are not an admin reactivation shortcut.

**R6 — admin account status (catalog only; not executed)**
```json
{"status": "BLOCKED"}
```
The supported transition pair is ACTIVE ↔ BLOCKED. CLOSED is not reopened. Both admin DTOs reject unrelated fields.

### Actual response contracts

- Registration: `userId, email, message`; message is “User registered successfully”.
- Login: **only** `userId, name, email, role, status`.
- Logout: HTTP **200**, `{"message":"Logged out successfully"}`. Historical screenshots showing 204 are not the current contract.
- Profile: `userId, name, email, phone, status, createdAt, updatedAt`.
- Account: `accountId, accountNumber, accountType, balance, status, createdAt, updatedAt, userId`.
- Beneficiary: `beneficiaryId, beneficiaryName, bankAccountNumber, ifsc, status, createdAt`; list returns an array; DELETE has no response body.
- Transaction: `transactionId, transactionRef, amount, purpose, fromAccountId, beneficiaryId, beneficiaryName, beneficiaryBankAccountNumber, beneficiaryIfsc, state, riskTier, riskReason, protectionSeconds, protectionExpiresAt, createdAt, settledAt, cancelledAt`. Absent transaction timestamps are currently empty strings. There is no `authenticationRequired` response field.
- Admin user DTO: `userId, name, email, phone, role, status, createdAt, updatedAt`.
- Admin account status DTO: `accountId, userId, accountNumber, accountType, status, updatedAt`.
- Report summary: `totalTransactions, settledTransactions, protectedTransactions, cancelledTransactions, rejectedTransactions, hardHolds, highRiskTransactions, totalAmount, settledAmount`.
- Daily report: array of `{"date": "...", "summary": { ...summary fields... }}`. It groups by transaction creation date and current state, not a timeline of state-change events.
- Errors generally use `{"message":"..."}`; default framework error envelopes may differ. The collection checks for sensitive keys and raw Oracle diagnostic patterns without printing values.

Inventory sources: [controllers](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/controller>), [request/response types](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/beans>), [security configuration](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config>), [services](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services>).

## 3. Automated verification

Ran the existing suite without modifying test source. Actual working directory was the Backend folder, Java 17 and Maven 3.9.16:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& 'C:/Users/Shreya Ojha/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/Shreya Ojha/.m2/repository' '-Dsafepay.oracle.validation=false' -B compile test
```

On a teammate's Java 17 machine with Maven installed, the equivalent command from Backend is:

```text
mvn -Dsafepay.oracle.validation=false -B compile test
```

BUILD SUCCESS; exit code 0; Maven duration 23.022 seconds; completion 2026-09-13 22:29:10 +05:30.

| Existing suite | Passed |
|---|---:|
| BooleanToYNConverterTest | 2 |
| UserAccountDomainTest | 2 |
| AdminAuthorizationTest | 35 |
| AdminReportTest | 14 |
| BeneficiaryBackendTest | 25 |
| CustomerResourceTest | 47 |
| LoginControllerTest | 18 |
| LogoutTest | 5 |
| RegistrationValidationTest | 8 |
| AdminReportRepositoryTest | 1 |
| SafePayApplicationTests | 1 |
| AccountServiceImplTest | 1 |
| AmountRiskEngineTest | 4 |
| LoginAttemptTransactionTest | 2 |
| LoginLockoutTest | 9 |
| RiskAssessmentEngineTest | 38 |
| TransactionPreRiskStageTest | 31 |
| TransactionProtectionStateMachineTest | 66 |
| TransactionRiskInputBuilderTest | 5 |
| UserRegistrationTest | 7 |
| **Total passed** | **321** |

**OracleDomainValidationTest: 1 skipped**, explicitly disabled for this credential-free isolated run. Total discovered 322. No failures/errors hidden or tests altered to force a pass.

Evidence: current Surefire XML summaries in [test reports](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/target/surefire-reports>). Counts include parameterized cases. The one passing `SafePayApplicationTests` method is a no-op check, **not** proof of application startup. Isolated tests/mock repositories are not live Oracle verification.

## 4. Live local smoke checks

The existing server at **http://localhost:8080** was reachable. It was not restarted. No implemented health/actuator endpoint was found, so none was invented.

| Live probe actually sent | Observed result | Interpretation |
|---|---|---|
| GET /api/accounts/current without login | 401 | Anonymous account access blocked |
| GET /api/transactions without login | 401 | Anonymous transaction listing blocked |
| GET /api/admin/reports/transactions/summary without login | 401 | Anonymous reporting blocked |
| POST /api/auth/login with empty JSON object | 401 | Missing credentials rejected; no user lookup/counter change |
| POST /api/auth/logout without authentication | 200; expected logout message matched | Anonymous logout safe |
| OPTIONS /api/auth/login with Origin http://localhost:8000 and POST/content-type preflight | 200 | Allowed origin answered; credential/header-exposure limitations below |

No password, cookie, token or database credential values were logged. No real or guessed credentials were submitted.

### Live tests skipped — precise reasons

| Requested verification | Status / missing prerequisite |
|---|---|
| Successful customer login; exact safe response; JSESSIONID creation | SKIPPED: no currently approved existing demo customer's credentials supplied |
| Authenticated account and transaction reads | SKIPPED: no established approved customer session |
| Cross-customer ownership checks | SKIPPED: no approved second fixture with independently confirmed user/transaction/beneficiary IDs |
| Admin success and live customer denial on reports | SKIPPED: no approved ADMIN/customer sessions; automated authorization tests passed |
| Authenticated logout with/without CSRF, then protected 401 | SKIPPED: no approved authenticated session; anonymous logout alone does not prove session invalidation |
| Live transaction creation, same-key replay, balances and resulting tier/state | SKIPPED: no approved existing funded demo account + ACTIVE owned beneficiary + agreed amount/budget |
| Cancellation/expiry/auto-settlement/hold behavior in Oracle | SKIPPED: no eligible approved transaction and no authorized live payment fixture |
| Oracle schema validation and independent persisted transaction/balance checks | SKIPPED: no securely supplied Oracle verification configuration for this task; no SQL was executed |
| Newman collection execution | SKIPPED: Newman is not installed on PATH or in local node_modules; no installation performed |

Historical chat screenshots are not current fixture approval and were not used to guess credentials. No user, beneficiary, funds or payment was created merely to fill these gaps. **There is no verified live payment success to report.**

### Startup/configuration boundary

The server was already running; we did not launch another instance. Its exact deployed build/profile and database connectivity were not independently proven by anonymous routes.

For a later approved startup, the source's Oracle profile expects `SAFEPAY_DB_URL`, `SAFEPAY_DB_USERNAME`, `SAFEPAY_DB_PASSWORD` supplied privately. With those configured securely and Java 17/Maven installed, the source-compatible command from Backend is:

```text
mvn -Dspring-boot.run.profiles=oracle "-Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never" spring-boot:run
```

This command was **not run**. Starting the application also starts the existing scheduled release job and can process already-expired protected transactions. Do not start/restart it simply to obtain a green test without considering that side effect. Never substitute `ddl-auto=update` to hide validation failures.

## 5. CSRF and session workflow

Source: [CustomerResourceSecurityConfig](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config/CustomerResourceSecurityConfig.java>), [LoginSecurityConfig](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config/LoginSecurityConfig.java>), [LogoutSecurityConfig](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config/LogoutSecurityConfig.java>), [AdminSecurityConfig](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config/AdminSecurityConfig.java>).

1. Login using raw demo email/password. Postman keeps JSESSIONID in its cookie jar automatically.
2. GET /api/accounts/current using that same cookie jar.
3. The **response header** `X-CSRF-TOKEN` contains the current token. The collection saves it locally as `csrfToken`.
4. Authenticated POSTs send `X-CSRF-TOKEN: {{csrfToken}}` in Headers, never in the URL.
5. Customer/admin GET security chains expose the token. After ADMIN login, GET the admin summary/directory to obtain its current session token.
6. Authenticated logout without the header should return 403 and leave the session usable. Logout with the header returns 200 and invalidates the session. Subsequent protected reads should return 401.
7. Anonymous logout is exempt from CSRF and returns 200. It is safe but does not test an authenticated session.

The collection has these logout checks instead of creating a payment merely to test CSRF. No manual Cookie header, session ID environment variable or JWT is used. Clear stale token values by rerunning the appropriate login followed by a GET.

## 6. Current transaction behavior and safety constraints

Source: [TransactionPreRiskValidator](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services/TransactionPreRiskValidator.java>), [TransactionServiceImpl](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>), [TransactionRiskInputBuilder](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services/TransactionRiskInputBuilder.java>), [RiskAssessmentEngine](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services/RiskAssessmentEngine.java>).

- Caller must resolve to an existing ACTIVE CUSTOMER. Source account must belong to that customer and be ACTIVE.
- Beneficiary must belong to the same customer and be ACTIVE.
- Amount must be positive, at most 16 integer digits and two fractional digits. Purpose is limited to 255 UTF-8 bytes by service validation (also 255 characters at DTO validation).
- Available balance subtracts outstanding PROTECTED and HARD_HOLD amounts. At least **INR 5000 must remain** after the new payment.
- Creation needs a nonblank Idempotency-Key up to 100 UTF-8 bytes. Same owner + same key + same payload returns the original transaction; changed payload with that key returns 409.
- The risk input is assembled on the server: beneficiary creation time, prior settled payments, and the customer's last 30 days of settled amounts. Unknown device/context evidence is explicitly UNKNOWN.
- LOW: immediate SETTLED, zero delay. MEDIUM: PROTECTED, 10 seconds. HIGH: PROTECTED, 60 seconds. VERY_HIGH: HARD_HOLD, authentication required, no expiry/automatic release.
- Currently the input builder always supplies UNKNOWN device/context, contributing at least 3 points. Therefore **LOW cannot currently be produced by this HTTP integration**, even for a small amount. LOW is covered in isolated engine tests; do not fabricate normal-device evidence to force it.
- The current persisted Java enum remains CREATED, AUTHORIZED, RISK_ASSESSED, PROTECTED, HARD_HOLD, CANCELLED, SETTLED. The separate eight-state protection model is not connected to this service.
- Cancellation is owner-only and requires an unexpired PROTECTED transaction. SETTLED/HARD_HOLD cannot be cancelled. A repeated cancellation of an already CANCELLED transaction returns it.
- Auto-release runs on a scheduler; a balance can change between HTTP snapshots. VERY_HIGH has no implemented approval API and can remain held indefinitely.

### Optional live payment in Postman — explicit operator approval only

Leave all payment flags false for ordinary API checks. To test later:

1. Use an existing, approved, isolated demo customer. Do not run concurrent activity on its account.
2. Supply an existing owned ACTIVE beneficiaryId; the collection fetches it explicitly. It never chooses the first list result automatically.
3. Set `approvedDemoFixture=true`, `enableDemoPayments=true`, `acceptPossibleHardHold=true`.
4. Set an agreed positive decimal `testAmount` and `maximumDemoAmount` budget. Both are blank by default. Choose a small amount; the collection additionally caps a request at INR 1,000,000 as a safety guard, not an API rule.
5. Do not top up accounts or create payment recipients to force this test. The collection conservatively skips payments if **any** PROTECTED/HARD_HOLD transaction is present, if the snapshot is older than 30 seconds, or if the INR 5000 reserve would fail.
6. Run the verified account/beneficiary/list requests, then the pre-payment account snapshot, create, exact replay and post-replay balance requests in order.
7. A new Idempotency-Key is generated only when the local placeholder is empty; the exact body/key is frozen for the replay. Retain it for retries. Clear it only when intentionally approving a different payment.
8. Check the same transactionId is returned. Ending balance may be unchanged while held or reduced once. The assertion assumes no other account activity and does **not** establish database correctness by itself.
9. Independently verify persisted state and balances through an approved read-only Oracle check before claiming full live-payment verification. This task did not do that check.

For cancellation, set `enableCancellation=true` only for an approved selected transaction and configure the actual backend `serverUtcOffset`. Poll eligibility first, then cancel immediately. A safety margin is used, but the server may still return 400 if the window expires. Do not hide that result or extend a timer. No cancellation was executed during this task.

## 7. Import and manual run instructions

1. In Postman, choose **Import** and select both JSON files linked above.
2. Select **SafePay Local** from the environment dropdown. Keep baseUrl exactly **http://localhost:8080**.
3. Use a current Postman release supporting `pm.execution.skipRequest()`. Keep the cookie jar enabled. Do not add manual Cookie/Authorization headers. Do not mix localhost and 127.0.0.1.
4. Store only approved test/demo credentials in local, unshared environment values. Password/token variables are marked secret, but **masking is not an export guarantee**: do not export/sync populated environments, cookie jars or console/network logs.
5. For existing-customer checks, fill testEmail/testPassword and set approvedDemoFixture=true. Keep enableRegistration=false. A wrong login can increment failures and lock at five attempts; stop after an unexpected 401/403 instead of retrying blindly.
6. Run folders in numerical order, **one iteration, not in parallel**:
   - **01 Registration:** skipped unless explicitly enabled with a demo name/email/phone/password. It creates a user/account; duplicate 409 is a real outcome, not a successful new registration.
   - **02 Authentication:** empty-body 401 check, then approved customer login.
   - **03 Customer Account:** current account captures accountId/accountNumber and CSRF; profile checks follow.
   - **04 Beneficiaries:** list and inspect an explicitly selected existing beneficiary. Optional creation is off; createdBeneficiaryId is deliberately separate from the payment fixture.
   - **05 Transactions:** own list, then optional guarded create/replay/balance workflow; detail only if an ID exists.
   - **06 Risk and Protection:** GET/poll existing own transaction; optional guarded cancellation; read final state.
   - **07 Security and Ownership:** customer admin-denial check; foreign-resource checks only with approvedSecondFixture=true and independently known foreign IDs/email; CSRF logout tests; anonymous 401 checks.
   - **08 Admin Reporting:** approvedAdminFixture=true plus existing adminEmail/adminPassword; explicit ADMIN login, summary, daily report, safe user list, logout. No role/status changes.
7. If doing a shorter manual flow: login → current account → transactions → beneficiaries → optional approved transaction/replay → detail/poll → optional cancel → logout → admin login/reports/logout.
8. Adjust reportFrom/reportTo for the desired period (YYYY-MM-DD). Empty daily arrays and zero totals are valid; no test transaction is created to populate a chart.
9. Requests without prerequisites are skipped, not credited as successes. The safe reason for the last skip is stored in collection variable `_lastSkip`; inspect it locally. Re-run login and prerequisite reads when running a dependent request on its own.
10. After testing, log out and remove local credentials/tokens. Export only a fresh blank environment. Do not share populated runtime collection variables containing resolved request bodies either.

The collection deliberately excludes beneficiary DELETE and admin status PATCH requests from its runnable workflow to avoid deletion/status mutation. They remain documented in the inventory.

## 8. Collection verification and Newman

Newman was absent from PATH and project-local installation locations. It was **not installed** and the collection was **not run through Newman**.

Offline Node checks performed without network access:

- Both files parsed as JSON.
- All 71 embedded scripts compiled syntactically.
- Eight folders and 35 requests were found.
- A simulated default environment permitted only five safe requests: empty-body login, three anonymous protected GETs, anonymous logout. Thirty dependent/opt-in requests were skipped.
- Blank exported password/token values were confirmed.
- Fourteen additional offline checks passed: unsafe/missing approvals, missing CSRF, invalid/over-budget amounts, insufficient reserve, pending holds, missing beneficiary verification, stale snapshots and a non-local base URL were blocked; an approved in-memory example serialized only the four DTO fields and replayed the identical body/key. These examples were never sent to the backend.
- Offline guard checks are not Postman runtime or backend integration tests.

If Newman becomes available later, use it only after reviewing fixtures and guards; do not export an environment containing secrets merely to make a command runnable. The Postman manual import is sufficient for this handover.

## 9. Not implemented as HTTP APIs

No collection requests were invented for:

- health/actuator health;
- /api/auth/me (use the implemented /api/users/current);
- arbitrary /api/accounts/{id} lookup or customer balance updates;
- old /api/transactions/initiate query-parameter creation;
- standalone risk assessment or client-selected risk tier;
- transaction approve/reject/verify/release endpoints;
- the isolated protection state-machine transitions as separate APIs;
- an admin dashboard UI.

Unsupported paths may encounter security filters before a normal 404. Do not treat historical URLs as current APIs.

## 10. Known findings and limits — no fixes made

1. **Protection integration remains incomplete.** [TransactionServiceImpl.initiate/transition](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>) still uses RISK_ASSESSED and its own transition helper. [TransactionState](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/beans/TransactionState.java>) does not contain RELEASED or REJECTED; the isolated protection class does. Running migration 11 alone does not connect those code paths. If such new states are stored, the current JPA enum cannot read them. This is source evidence, not a claim that live rows contain them.

2. **Cancellation idempotency/consistency gaps.** In TransactionServiceImpl.cancel, the required key is not otherwise consumed; replay behavior relies on current state. [TransactionDao.cancelProtected](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/repository/TransactionDao.java>) uses a bulk update without clearing the persistence context, so the immediate response may be stale. Its update predicate lacks the expiry comparison checked earlier in the service, leaving a deadline race. These are source-level risks, not reproduced live failures.

3. **Concurrent settlement deserves a database-backed test.** TransactionServiceImpl.releaseExpiredTransactions subtracts and saves an account balance without the same account-locking path used by transaction initiation. The version check protects an individual transaction, not necessarily concurrent writes to one account. Lost-update exposure has not been reproduced or excluded by these isolated tests.

4. **Browser session CORS is incomplete.** [WebConfig](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/config/WebConfig.java>) allows localhost:8000 but does not enable credentialed CORS or expose X-CSRF-TOKEN. The live preflight did not return Access-Control-Allow-Credentials=true or expose that response header. Postman is not subject to browser CORS, so successful Postman checks would not prove frontend session integration.

5. **Configuration needs a separate cleanup task.** [application.properties](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/resources/application.properties>) contains hardcoded database credentials and legacy provider properties; values are intentionally not reproduced here. The default local profile uses H2/ddl-auto=update while the pom does not provide H2. Use the reviewed Oracle profile with validation for Oracle-backed work. No configuration was changed and no schema problem was “fixed” by automatic DDL.

6. **Some API boundaries still use entities.** [AuthController.register](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/controller/AuthController.java>) accepts User directly, though sensitive identity/status values are overwritten in registration and the response is a safe map. Name/email/phone request validation is less comprehensive than the password check. [AccountController](<C:/Users/Shreya Ojha/Desktop/SafePay/Backend/src/main/java/com/ofss/controller/AccountController.java>) returns Account directly with ignored user relationship rather than a dedicated DTO. Current safe serialization is tested, but these are future maintenance boundaries.

7. **Security coverage is explicit rather than catch-all.** Custom chains match auth/customer/admin paths; there is no general fallback chain covering future controllers. Existing registration is intentionally public. New APIs need explicit security review; this report does not claim an exploit on an unimplemented route.

8. **Documentation drift exists.** The AGENTS compatibility paragraph still describes AmountRiskEngine as live, while the actual service invokes RiskAssessmentEngine. Parts of PHASE1_SPEC/historical setup notes retain older API/state assumptions. For this read-only verification, the inventory follows actual source and flags the discrepancy instead of changing either code or policy.

9. **Passing tests are not full live acceptance.** Authenticated sessions, independent ownership fixtures, actual Oracle view contents, schema validation, concurrent balance behavior and live payments remain unverified here. The approved next action is to supply local demo fixtures and run the guarded read-only session workflow first—not to assume all live scenarios passed.

## 11. Completion checklist

- [x] Inspected actual endpoints, DTOs, security, relevant services/repositories and project documentation.
- [x] Compilation and all available isolated tests passed.
- [x] Existing local HTTP listener checked without restarting it.
- [x] Six safe live probes recorded; no guessed credentials.
- [x] Eight-folder collection and blank environment created.
- [x] JSON/script/default-guard checks completed offline.
- [x] No application/test-source/schema changes and no SQL execution.
- [ ] Approved authenticated customer/admin live acceptance.
- [ ] Approved second-user live ownership check.
- [ ] Oracle-backed validation and persisted balance/state verification.
- [ ] Optional suitable-fixture payment/replay/cancellation checks.
- [ ] Newman run (tool not installed).

Stop point: documentation and verification artifacts only. No backend fixes or further features were implemented.
