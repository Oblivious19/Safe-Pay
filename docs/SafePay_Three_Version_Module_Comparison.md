# SafePay: Sahu, Ruchi and Shreya module comparison

Date: 15 September 2026

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

## 1. Scope and overall result

This report compares the actual source in these three downloaded archives:

- **Sahu:** Safe-Pay-feature_release_2_sahu.zip
- **Ruchi:** Safe-Pay-feature_release_2_ruchi.zip
- **Shreya:** Safe-Pay-shreya-v1.zip

The referenced task, **Review backend functionality**, was used for context. Its working-copy changes are not assumed to exist in every archive. Controllers, security rules, services, repositories, entities, frontend bindings, tests and SQL were inspected. This is a **static code comparison**: no application, database migration or test suite was executed.

**Overall assessment**

- **Shreya has the broadest application functionality:** session authentication, login lockout, customer screens, admin status controls and reports, and risk scoring using several signals.
- **Sahu has the strongest transaction safeguards of these versions:** persistent cancellation keys, database deadline checks, reservation-aware balance edits, account locking during settlement and separate settlement transactions.
- **Ruchi is a simpler backend variant.** Its root project adds account classification and status management; these additions are absent from its nested Backend project.
- **Shreya is not a complete superset of Sahu or Ruchi.** Several CRUD operations are removed from the usable API, beneficiary reactivation is absent, and some Sahu transaction safeguards are missing.

### Ruchi contains two different backends

| Item | Ruchi root project: pom.xml + src/ | Ruchi nested project: Backend/pom.xml + Backend/src/ |
|---|---|---|
| Account API | /accounts | /api/accounts |
| Account types | SAVINGS, CURRENT, SALARY | No type field |
| Account statuses | ACTIVE, INACTIVE, BLOCKED | No status field |
| Account creation | Defaults to SAVINGS and ACTIVE; supplied type/status accepted | Balance, owner and creation time |
| Account update | Balance, type and status | Balance only |
| Initiation | Rejects a non-ACTIVE source account | No source-account status check |
| Other functional modules | Same baseline functionality | Same baseline functionality |

Both contain 18 controller routes. They are separate launch/build choices; their features must not be counted as two sets of modules. Evidence: [Ruchi account service][R-account], [nested account controller][R-nested-account], [root transfer service][R-transfer].

## 2. Complete module inventory

The following **24 functional areas** group the union of functionality across the archives. They are logical modules and supporting capabilities, not a count of Maven modules or Java packages. “Partial” means code exists but the complete user workflow is missing.

| # | Module | Sahu | Ruchi | Shreya |
|---|---|---|---|---|
| 1 | Registration and onboarding | Register; duplicate email/phone checks; BCrypt password; automatic ₹5,000 account | Register; duplicate email check; BCrypt; automatic ₹5,000 account | Adds CUSTOMER role, ACTIVE user, numbered SAVINGS account and login-status initialization |
| 2 | Login, logout and lockout | Absent | Absent | Session login/logout; five failed attempts cause a 15-minute lock |
| 3 | User management and profile | Create, list, detail, update, guarded delete | Same CRUD operations | Current/self profile reads; legacy create/list/update/delete routes are denied by security |
| 4 | Account management | Multiple-account CRUD; balance edits preserve pending holds and ₹5,000 minimum | CRUD; root adds type/status editing, nested does not | One account per user, separate account number, current-account read; no exposed balance-edit or account CRUD endpoints |
| 5 | Beneficiary management | Add/list; activate/deactivate; source-account ownership | Same baseline operations | Add/list/detail; DELETE soft-deactivates; lists ACTIVE only; no reactivation endpoint |
| 6 | Transfer initiation | JSON request and Idempotency-Key at /api/transactions/initiate | Request parameters and key at /api/transactions/initiate | JSON and key at /api/transactions; caller from session |
| 7 | Pre-transfer validation | Money precision, source ownership, beneficiary/source matching, ACTIVE beneficiary, available funds | Positive amount, email-scoped records, ACTIVE beneficiary, posted balance; root also checks account ACTIVE | Dedicated validator: active CUSTOMER, owned ACTIVE account, ACTIVE beneficiary, money/byte limits, available funds |
| 8 | Risk assessment and explanation | Amount-only LOW/MEDIUM/HIGH/HARD_HOLD, persisted range reason | Same amount-only policy | Integrated scoring using amount, beneficiary age, history and context; LOW/MEDIUM/HIGH/VERY_HIGH; contributing reasons persisted |
| 9 | Protection and state transitions | Initial state validation; immediate settlement or timed protection/hard hold | Same basic lifecycle | Live lifecycle remains similar; also has a separate, tested protection state machine that is not wired into the live transaction flow |
| 10 | Payment cancellation | Unexpired PROTECTED only; database deadline/version/state checks; persisted cancellation key | Unexpired PROTECTED check; version/state update; key ignored | Owner/session-scoped cancellation; otherwise similar limitations to Ruchi |
| 11 | Scheduled settlement | Every five seconds; account lock, balance recheck, separate transaction per payment | Every five seconds; one transaction for whole batch | Every five seconds; one transaction for whole batch |
| 12 | Hard-hold verification and release | Partial: creates HARD_HOLD; no verification/release API | Same gap | Partial: VERY_HIGH creates HARD_HOLD; domain approve/reject hooks exist but no verification/release workflow |
| 13 | Transaction details and history | Rich response, newest-first list, state filter | State filter; response limited to six fields | Rich response and state filter; no explicit backend sorting; dashboard sorts its recent subset |
| 14 | Idempotency and retries | Initiation checks payload; persistent initiation and cancellation keys | Initiation checks key/owner only; cancellation key unused | Initiation checks owner/payload; cancellation key unused |
| 15 | Reserved funds and concurrency | Subtracts held amounts from availability; user/account locks and conditional updates; settlement safeguards | No pending-fund reservation calculation or account write locking | Reserves pending amounts and locks account for initiation; settlement lacks Sahu's account lock and isolation |
| 16 | Audit trail | Initiate/cancel/auto-settle audit rows | Same | Same; no audit retrieval UI/API or admin-change audit |
| 17 | Admin user/account controls | Absent | Absent; account status edits in root are ordinary CRUD | ADMIN user list; user ACTIVE↔SUSPENDED; account ACTIVE↔BLOCKED |
| 18 | Admin transaction reporting | Absent | Absent | Summary and daily reports via Oracle views; no report screen |
| 19 | Customer dashboard | Starter placeholder | Same starter placeholder | Current account, five recent payments, pending payments, account status and error/session states |
| 20 | Frontend screens and integration | Oracle JET starter only | Identical starter frontend | API-connected login, dashboard, send-money, beneficiaries and transactions; logout/profile header |
| 21 | API authentication and ownership | Caller-supplied email filters; no authenticated identity | Same | Session identity, CUSTOMER/ADMIN access rules, CSRF protection and credentialed CORS |
| 22 | Request validation and errors | DTO validation; custom 400/404/409 handling | Basic argument/state/balance errors; less validation | Customer DTOs, safe errors, session/CSRF errors and dedicated duplicate/status/transaction errors |
| 23 | Oracle persistence and schema | Five active JPA entities; explicit monetary mappings; schema scripts need alignment with newer transaction fields | Five entities per tree; Java BigDecimal, but money precision/scale annotations incomplete | Six entities including Role; foundation/risk/state/report migrations; transaction amount mapping still lacks explicit precision/scale |
| 24 | Tests and API documentation | Three backend test classes; flow guide, HTTP requests and Postman package | Two test classes per tree; four real risk tests and one empty placeholder | 22 backend test files, five frontend test files, Postman workflow and setup/reporting docs |

Evidence for the table is expanded below. A route or class name by itself was not treated as proof that a complete feature works.

## 3. Shared functionality

All versions implement these backend foundations:

1. User registration with password hashing and automatic ₹5,000 account creation.
2. Beneficiary creation and listing, with an ACTIVE/INACTIVE concept.
3. Simulated transfer initiation and saved transaction references.
4. Risk decisions with stored explanations.
5. Timed PROTECTED payments, cancellation before expiry and a five-second settlement scheduler.
6. HARD_HOLD creation, transaction detail/history and state filtering.
7. Audit rows for initiation, cancellation and automatic settlement.
8. Java BigDecimal money values, Oracle/JPA sequence IDs, repositories/services/controllers and custom exceptions.

These similarities concern feature intent; behavior and safeguards differ. For example, Ruchi's holds do not reduce spendable funds during subsequent initiation.

All versions settle by **debiting the source account**. None credits a modeled beneficiary account or implements real NEFT/RTGS/IMPS/UPI integration.

Sources: [Sahu transfer service][S-transfer], [Ruchi transfer service][R-transfer], [Shreya transfer service][H-transfer].

## 4. Main behavioral differences

### 4.1 Authentication and customer/account management

Sahu and Ruchi use request-supplied email for ownership filtering. BCrypt protects stored passwords but does not authenticate API requests.

Shreya implements session authentication, session logout, login attempt counting and timed lockout. Identity is resolved from the server-side session. Customer resource routes and admin routes have separate access rules; authenticated writes require CSRF tokens.

Shreya also changes the account model to one account per user with a generated account number. Its types are SAVINGS/CURRENT and statuses ACTIVE/BLOCKED/CLOSED; CLOSED is a defined state, but the admin endpoint only permits ACTIVE↔BLOCKED.

**Usable operations change:** Sahu/Ruchi expose user and account CRUD. Shreya denies legacy user create/list/update/delete routes and exposes only current/self profile reads; its account controller only exposes current-account retrieval. Account CRUD methods remain in a service, but that does not make them usable HTTP features.

Shreya has no deposit/top-up API. A new account starts at the ₹5,000 minimum, so it needs a funded/seeded balance before a transfer can succeed.

Sources: [Sahu registration][S-user], [Sahu account management][S-account], [Ruchi account management][R-account], [Shreya registration][H-user], [login][H-login], [security rules][H-security], [logout][H-logout], [account model][H-account].

### 4.2 Beneficiaries

- **Sahu/Ruchi:** add, list, activate and deactivate. Neither exposes beneficiary detail editing or DELETE.
- **Shreya:** add, ACTIVE-only list/detail and soft DELETE to INACTIVE. Ownership is assigned from the current session's account. Account-number/IFSC validation and normalization are explicit.
- Shreya does not expose reactivation. Its duplicate check includes inactive rows, so soft-deleting a beneficiary does not allow adding the same bank details again.
- No version implements beneficiary cooling, blacklisting or verification. Shreya's “under 24 hours” rule changes payment risk; it is not a beneficiary activation delay.

Sources: [Sahu beneficiary service][S-beneficiary], [Shreya beneficiary service][H-beneficiary].

### 4.3 Risk behavior is materially different

**Sahu and Ruchi:**

| Amount | Risk tier | Outcome |
|---|---|---|
| Positive amount up to ₹10,000 | LOW | Immediate settlement |
| Above ₹10,000 to ₹50,000 | MEDIUM | 10-second protection |
| Above ₹50,000 to ₹1,00,000 | HIGH | 60-second protection |
| Above ₹1,00,000 | HARD_HOLD | Held pending an unimplemented authentication flow |

**Shreya's integrated engine:**

| Signal | Score contribution |
|---|---:|
| Amount up to ₹10,000 / up to ₹50,000 / up to ₹1,00,000 / above ₹1,00,000 | 0 / 2 / 4 / 6 |
| Beneficiary under 24 hours old | +2 |
| No previous settled payment to this beneficiary | +1 |
| No settled-payment baseline in the last 30 days | +1 |
| Amount exceeds three times recent average, when a baseline exists | +2 |
| Device evidence elevated or unknown | +2 |
| Transaction context elevated or unknown | +1 |

| Total score | Tier | Outcome |
|---|---|---|
| 0 | LOW | Immediate-settlement branch |
| 1–3 | MEDIUM | 10-second protection |
| 4–5 | HIGH | 60-second protection |
| 6+ | VERY_HIGH | HARD_HOLD; authentication required |

**Actual integration caveat:** Shreya's input builder always supplies UNKNOWN for both device and context, adding at least three points. The running path therefore cannot currently produce LOW.

For example, assuming sufficient funds and valid records, a ₹5,000 first payment to a new beneficiary by a customer with no recent history produces:

- Sahu/Ruchi: LOW and immediate settlement.
- Shreya: 0 + 2 + 1 + 1 + 2 + 1 = **7**, VERY_HIGH and HARD_HOLD.

This is an outcome derived from source, not a live test. No device-tracking system is implemented. Missing/invalid risk evidence aborts processing rather than silently selecting LOW.

Shreya's AGENTS compatibility note still describes the old engine as live. The actual TransactionServiceImpl constructs and invokes RiskAssessmentEngine and TransactionRiskInputBuilder, so this comparison follows the implementation.

Sources: [Sahu risk engine][S-risk], [Shreya engine][H-risk], [input builder][H-inputs], [live service integration][H-transfer].

### 4.4 State machine and hard-hold completion

All three live services still use CREATED→AUTHORIZED→RISK_ASSESSED and then SETTLED, PROTECTED or HARD_HOLD.

Shreya additionally contains TransactionProtectionStateMachine and ProtectionState with RELEASED and REJECTED. This pure Java component is separately tested but is not called by the live transaction service. Its package-private approve/reject methods do not authenticate anybody.

**None of the archives provides an end-to-end hard-hold verification/release operation.** The cancellation and scheduler paths exclude HARD_HOLD. Shreya's customer login does not serve as transaction-specific step-up verification.

Shreya's optional state migration 11 permits the newer states and excludes RISK_ASSESSED; the live history filter still uses the legacy enum. These pieces require deliberate integration and schema coordination. Their presence should not be counted as completed release/rejection functionality.

Sources: [Shreya pure state machine][H-state], [live transfer service][H-transfer], [state migration][H-migration].

### 4.5 Idempotency, reservation and settlement safeguards

| Control | Sahu | Ruchi | Shreya |
|---|---|---|---|
| Same initiate key with changed payload | Rejects conflict | Returns old transaction for same email without comparing changed payload | Rejects conflict |
| Separate persistent cancellation key | Yes | No; argument ignored | No; argument ignored |
| Subtract PROTECTED + HARD_HOLD amounts from availability | Yes | No | Yes |
| Account lock during initiation | Yes | No | Yes |
| Account lock and minimum-balance recheck during scheduler debit | Yes | No | No |
| Atomic database deadline check when cancelling/settling | Yes | No; application-time check before update | No; application-time check before update |
| Settlement failure isolated to one payment | Yes | No; whole loop is one transaction | No; whole loop is one transaction |
| Clear persistence context after bulk state update | Yes | No | No |

**Static consistency concern:** Ruchi/Shreya load a transaction, bulk-update it, and query it again in the same persistence context without clearing or refreshing it. The cancellation response may therefore retain the already-loaded PROTECTED state even after the database changed. This was not reproduced against a live database.

Sahu expresses monetary precision/scale and unique idempotency keys in its entity mappings. Ruchi lacks explicit monetary precision/scale annotations for account balance and transaction amount. Shreya maps account balance explicitly but not transaction amount; initiation-key uniqueness also depends on installed SQL. BigDecimal alone does not establish every database constraint.

Sources: [Sahu transfer handling][S-transfer], [Sahu conditional database operations][S-txdao], [Ruchi transfer handling][R-transfer], [Shreya validation/reservation][H-validator], [Shreya transfer handling][H-transfer], [Shreya bulk updates][H-txdao].

### 4.6 History, administration and reporting

Ruchi returns only transaction ID, reference, state, risk tier, expiry and risk reason. Sahu and Shreya also return money, beneficiary and lifecycle information. Sahu explicitly orders newest first; Shreya's backend list has no explicit ordering, although the dashboard sorts the latest five.

Shreya alone exposes:

- ADMIN user list.
- User ACTIVE↔SUSPENDED status changes.
- Account ACTIVE↔BLOCKED status changes.
- Transaction summary and daily reports: status/risk counts, total amount and settled amount.

Admin operations do not provide role editing, locked-user unlocking, account closing, balance changes or payment approval.

Reports read Oracle views installed from migration 12. A daily report groups by transaction creation date and reflects current state; it is not a historical record of transitions on that day. The inclusive range is limited to 366 days. REJECTED is included in reports even though the live rejection workflow is absent.

All versions write payment audit rows; none exposes an audit browser/API. Shreya admin status changes do not create audit events.

Sources: [Ruchi response contract][R-api], [Sahu response contract][S-api], [Shreya response contract][H-api], [admin service][H-admin], [report repository][H-report], [report views][H-report-sql].

## 5. Frontend comparison

A file-by-file SHA-256 comparison found **all 48 frontend files in Sahu and Ruchi identical**.

| Screen/capability | Sahu/Ruchi | Shreya |
|---|---|---|
| Login | Absent | Connected to session login |
| Registration screen | Absent | “Create an account” disabled; registration API/client service exists |
| Dashboard | Oracle JET placeholder | Current balance/account, pending payments, recent five, errors and session redirect |
| Send money | Absent | Account/beneficiary selection, amount/purpose submission, result |
| Beneficiaries | Absent | Add and list screen; backend detail/DELETE are not exposed by this screen |
| Transactions | Absent | History and cancellation |
| Signed-in identity/logout | Sample identity only | Profile read and session logout |
| Admin controls/reports screen | Absent | Absent; report client service exists without a routed screen |
| Incidents/customers/about | Starter placeholders | Old starter files remain, but are not in active navigation |
| Automatic polling/countdown | Absent | No polling loop/countdown implemented; uses load/refresh |
| API transport | No SafePay integration | Cookies, CSRF header handling, typed services and request-error mapping |

Sources: [starter navigation][S-starter], [Shreya routes][H-route], [dashboard][H-dashboard], [browser transport][H-browser-api], [disabled registration control][H-registration-ui].

## 6. API changes that affect compatibility

| Operation | Sahu | Ruchi | Shreya |
|---|---|---|---|
| Initiate | POST /api/transactions/initiate, JSON including userEmail | Same path, request parameters including userEmail | POST /api/transactions, JSON; session supplies identity |
| Cancel | POST /api/transactions/{id}/cancel, JSON userEmail | Same path, userEmail parameter | Same path, session identity; no email body needed |
| Add beneficiary | JSON with source account/email/details | Request parameters | JSON beneficiary details only; account derived from session |
| Beneficiary status/removal | PATCH /api/beneficiaries/{id}/status | Same | DELETE /api/beneficiaries/{id}; no status PATCH |
| Accounts | CRUD at /accounts | Root /accounts; nested /api/accounts | GET /api/accounts/current |
| Current user | Get an explicitly selected user | Same | GET /api/users/current or self ID |
| Login/logout | Absent | Absent | POST /api/auth/login and /api/auth/logout |

### Route counts

- Sahu: **18** mapped controller routes.
- Ruchi: **18 per backend tree**.
- Shreya: **22** controller mappings, of which four legacy user CRUD mappings are denied; **18 intended reachable controller routes + one logout filter endpoint = 19 usable operations**, subject to role/session requirements.

### Full API route inventory

**Sahu and Ruchi** (18 each):

- POST /api/auth/register
- POST /api/users; GET /api/users
- GET /api/users/{id}; PUT /api/users/{id}; DELETE /api/users/{id}
- GET {accounts}; POST {accounts}
- GET {accounts}/{id}; PUT {accounts}/{id}; DELETE {accounts}/{id}
- POST /api/beneficiaries; GET /api/beneficiaries
- PATCH /api/beneficiaries/{id}/status
- POST /api/transactions/initiate
- GET /api/transactions; GET /api/transactions/{id}
- POST /api/transactions/{id}/cancel

Here {accounts} is /accounts for Sahu/Ruchi root and /api/accounts for Ruchi nested.

**Shreya** (19 intended usable operations):

- POST /api/auth/register; POST /api/auth/login; POST /api/auth/logout
- GET /api/users/current; GET /api/users/{id} (self)
- GET /api/accounts/current
- POST /api/beneficiaries; GET /api/beneficiaries
- GET /api/beneficiaries/{id}; DELETE /api/beneficiaries/{id}
- POST /api/transactions; GET /api/transactions
- GET /api/transactions/{id}; POST /api/transactions/{id}/cancel
- GET /api/admin/users
- PATCH /api/admin/users/{id}/status
- PATCH /api/admin/accounts/{id}/status
- GET /api/admin/reports/transactions/summary
- GET /api/admin/reports/transactions/daily

## 7. Schema, tests and completeness

### Active persistence models

| Version | Active JPA entities |
|---|---|
| Sahu | User, Account, Beneficiary, TransactionDb, AuditLog |
| Ruchi, either backend | User, Account, Beneficiary, TransactionDb, AuditLog |
| Shreya | User, Role, Account, Beneficiary, TransactionDb, AuditLog |

SQL-only approvals and Shreya's older tables for banks, risk factors, configurable protection rules, safety settings, transaction context and disputes are schema material. They do not establish active application modules. No such controllers/services were found.

### Test source inventory

| Version | Backend test source | Frontend test source |
|---|---|---|
| Sahu | 3 classes; 22 test methods; 39 cases after expanding supplied parameter inputs | No supplied frontend tests |
| Ruchi | 2 classes per tree; 5 methods, comprising 4 risk tests and 1 empty placeholder | No supplied frontend tests |
| Shreya | 22 Java test files; 146 test/parameterized-test declarations | 5 test files |

These are source counts, **not passing-test results**. Shreya covers more modules; Sahu includes focused risk, idempotency, reservation, deadline and scheduler-isolation tests. Ruchi does not have comparable transaction/service/controller coverage.

### Functionality absent as a complete workflow in every archive

- Transaction-specific OTP/step-up verification and hard-hold settlement.
- Real payment rails, recipient-account credit or a double-entry ledger.
- Maker-checker/corporate approvals.
- Beneficiary cooling/blacklist, risk preview and device tracking.
- Incident/dispute management and customer safety-settings workflows.
- Notifications, audit viewing and transaction statement/export.
- Password reset/recovery.
- WebSocket push, AI/ML or administrator-configurable risk rules.

Some names appear in SQL, sample screens or future domain hooks. They were intentionally not counted as completed modules.

## 8. Conclusion and how to check this comparison

**Shreya contributes the broadest customer/admin feature set. Sahu contributes transaction safeguards that should be retained in any future consolidation. Ruchi root contributes account type/status CRUD, but Shreya uses a different account model and authorization contract.**

A future consolidation would need explicit decisions on risk policy, one versus multiple accounts, beneficiary reactivation, API contracts and scheduler/idempotency safeguards. No merge or implementation is part of this comparison.

Only this report was added to the working project. To manually verify the conclusions:

1. Open the three source archives and distinguish Ruchi's root and nested Maven projects.
2. Follow the linked implementation files below; compare controller routes and security rules before counting usable operations.
3. Compare the live risk-engine calls, not just AGENTS/spec text.
4. Inspect cancellation and settlement repository predicates alongside their calling service methods.
5. Check active frontend routes and API calls; sample pages and client-only service methods are not complete screens.

Source links point to temporary extracted copies used for review. If those copies are cleaned up, use the same internal paths in the original ZIP archives in Downloads.

### Source archive fingerprints (SHA-256)

- Sahu: 7A963AE58DC0B09FFC9D0DF32E2DF2AAF09B835C2C5153BD4C1F6D65884695E4
- Ruchi: 2539770646D4F049D9F2D12CD60242EA9AFAF1B5CC6D5ECD44029ABA6550B505
- Shreya: AD865EB51FD5598E05A32109524BFF1D33FD20F169C07A2D86B00C00A8519CCA

[S-user]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/services/UserServiceImpl.java:34
[S-account]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/services/AccountServiceImpl.java:55
[S-beneficiary]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/services/BeneficiaryServiceImpl.java:26
[S-transfer]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java:64
[S-txdao]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/repository/TransactionDao.java:41
[S-api]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/controller/TransactionController.java:33
[S-risk]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Backend/src/main/java/com/ofss/services/AmountRiskEngine.java:10
[R-account]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_ruchi/src/main/java/com/ofss/services/AccountServiceImpl.java:51
[R-nested-account]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_ruchi/Backend/src/main/java/com/ofss/controller/AccountController.java:19
[R-transfer]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_ruchi/src/main/java/com/ofss/services/TransactionServiceImpl.java:48
[R-api]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_ruchi/src/main/java/com/ofss/controller/TransactionController.java:28
[R-user]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_ruchi/src/main/java/com/ofss/services/UserServiceImpl.java:36
[H-user]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/UserServiceImpl.java:41
[H-login]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/LoginService.java:19
[H-security]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/config/CustomerResourceSecurityConfig.java:29
[H-logout]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/config/LogoutSecurityConfig.java:17
[H-account]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/beans/Account.java:33
[H-beneficiary]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/BeneficiaryServiceImpl.java:31
[H-transfer]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java:56
[H-validator]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/TransactionPreRiskValidator.java:32
[H-risk]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/RiskAssessmentEngine.java:21
[H-inputs]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/TransactionRiskInputBuilder.java:31
[H-state]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/TransactionProtectionStateMachine.java:11
[H-api]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/controller/TransactionController.java:43
[H-txdao]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/repository/TransactionDao.java:39
[H-admin]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/services/AdminService.java:26
[H-report]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Backend/src/main/java/com/ofss/repository/AdminReportRepository.java:13
[H-report-sql]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Database/12_admin_reporting_views.sql:10
[H-migration]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Database/11_transaction_state_machine.sql:62
[H-route]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Frontend/SafePayJet/src/ts/appController.ts:46
[H-dashboard]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Frontend/SafePayJet/src/ts/viewModels/dashboard.ts:10
[H-browser-api]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Frontend/SafePayJet/src/ts/services/apiClient.ts:51
[H-registration-ui]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-shreya-v1/Frontend/SafePayJet/src/ts/views/login.html:27
[S-starter]: C:/Users/GAURAV~1/AppData/Local/Temp/safepay-module-comparison-20260915/Safe-Pay-feature_release_2_sahu/Frontend/SafePayJet/src/ts/appController.ts:62

