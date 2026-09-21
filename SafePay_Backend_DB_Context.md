# SafePay Backend + Database Continuation Context

**Context date:** 19 September 2026  
**Project root:** `C:\Users\Aditya Rao\Downloads\Training\Project\SafePay`  
**Backend root:** `C:\Users\Aditya Rao\Downloads\Training\Project\SafePay\backend`  
**Current checkpoint — backend implementation frozen:** User screenshots confirm 31 regression tests passed at 22:32:49 IST, 178 affected tests at 22:33:48 IST, and the complete 1148-test suite at 22:39:13 IST on 19 September 2026; all have zero failures/errors/skips and BUILD SUCCESS. Backend Phases 2.1–2.12 and change-request Phases 0–8 are locked for the current scope. V13 migration/startup and category constraints were previously verified; retain target 13 and both migration locations. No new backend implementation is authorized. Documentation increment 1 (master foundation/index/Phase 2.1 plus its separate API guide) is ready for user review; Phase 2.2 awaits approval. Any discovered flaw must be reported with evidence and proposed correction in chat; do not patch code, migrations, configuration, tests or documentation to resolve it without explicit approval.

---

## 1. Purpose and authority

This is a compact continuation record for the SafePay database and backend. It records the current truth, major decisions, implementation progression, resolved incidents, verification status, and the next work boundary. It is not the detailed master backend explanation or an API-test guide.

Use this authority order when sources differ:

1. The user's latest explicit instruction or approval.
2. Root `AGENTS.md`, subject to any explicit user-authorized exception.
3. `UPDATED_Decision_Register.md` and the reconciled Decision Register.
4. `UPDATED_implementation_plan.md` and the reconciled implementation plan.
5. The operative implementation and guide contracts.
6. Applied Flyway history, current Oracle objects, and current backend source.
7. Phase guides and this continuation context.
8. PRD and older contexts only where they do not conflict with newer decisions.

The Decision Register remains the V1 reference bible. Do not silently import superseded PRD ideas.

---

## 2. Product contract retained

SafePay V1 is a simulated risk-adaptive pre-settlement control layer. It may reserve, delay, cancel, verify, review, release, and internally settle simulated payments. It must never claim to intercept live UPI/IMPS/NEFT/RTGS traffic, reverse a completed external settlement, or move real money.

V1 risk classification is deterministic and amount-only:

| Amount | Tier | Required route |
|---:|---|---|
| ₹1.00-₹5,000.00 | `LOW` | Immediate release and simulated settlement |
| ₹5,000.01-₹25,000.00 | `MEDIUM` | 10-second protection window with Undo |
| ₹25,000.01-₹1,00,000.00 | `HIGH` | 60-second protection window with stronger warning and Undo |
| Above ₹1,00,000.00 | `VERY_HIGH` | Untimed hold, OTP, then Risk Officer review |

The canonical transaction progression is:

```text
CREATED -> AUTHORIZED -> RISK_ASSESSED
  LOW -> RELEASED -> SETTLED
  MEDIUM/HIGH -> PROTECTED -> CANCELLED or RELEASED -> SETTLED
  VERY_HIGH -> VERIFICATION_REQUIRED
              -> CANCELLED on customer action/policy exhaustion
              -> PENDING_RISK_REVIEW on OTP success
                 -> RELEASED on approval
                 -> CANCELLED on rejection
                 -> VERIFICATION_REQUIRED on re-verification request
```

`SETTLED`, `CANCELLED`, and `FAILED` are terminal. Database/server time is authoritative. Client timers and disabled UI buttons are never financial controls.

The exact authorities are `CUSTOMER`, `RISK_OFFICER`, `SYSTEM_ADMIN`, and `AUDITOR`. There is no generic `ADMIN` authority. The UI may later present a combined administrator persona, but the database and Spring Security authorities remain distinct.

---

## 3. Database checkpoint

### 3.1 Ownership model

- `SAFEPAY_OWNER` owns and migrates schema objects.
- `SAFEPAY_APP` is the restricted runtime identity used by JPA.
- Hibernate uses `ddl-auto=validate`; it does not create or update schema objects.
- Flyway is enabled, validates names/checksums, disallows clean, disallows out-of-order migration, and uses the owner credentials.
- The repeatable grants migration gives only the privileges required by the application.

### 3.2 Applied migration map

| Migration | Current responsibility |
|---|---|
| V1 | Roles, users, assignments, authentication sessions and identity sequences |
| V2 | Customer accounts, beneficiaries, ownership and monetary foundations |
| V3 | Versioned risk policy, amount bands and protection policies |
| V4 | Payment transaction, risk factors and idempotency records |
| V5 | OTP challenges and the original approval/review structure |
| V6 | Append-only ledger posting/entry model and transaction exceptions |
| V7 | Immutable audit evidence and durable application notifications |
| V8 | Dashboard, risk-summary and pending-review analytical views |
| V9 | Phase 1.9 vocabulary reconciliation; review table/sequence renamed to `RISK_REVIEW` and `SEQ_RISK_REVIEW_ID` |
| V10 | Ownership, policy tuple, state, reservation, immutability and integrity hardening |
| V11 | Canonical four roles, four protection actions and active `AMOUNT_ONLY_V1` reference data |
| R | Restricted `SAFEPAY_APP` grants, safely repeatable when its checksum changes |
| V12 | Local showcase seed: 30 customers, 3 staff, accounts, beneficiaries, 25 transaction scenarios, historical OTP/review/audit/notification evidence and balanced ledger data |

V12 is stored separately under `db/showcase` and is included only by the showcase run configuration. It adds data, not tables, constraints, triggers, sequences, or grants.

V1-V12 have now been applied successfully. They are immutable in this database. Any approved forward database correction must use V13 or later. A reset of V12 data requires an expressly approved disposable-schema rebuild; never remove a successful Flyway row or manually imitate rollback by deleting selected seed rows.

### 3.3 Canonical objects

The 19 canonical tables are:

`APP_ROLE`, `APP_USER`, `USER_ROLE`, `AUTH_SESSION`, `ACCOUNT`, `BENEFICIARY`, `RISK_POLICY`, `PROTECTION_POLICY`, `RISK_POLICY_BAND`, `PAYMENT_TRANSACTION`, `TRANSACTION_RISK_FACTOR`, `IDEMPOTENCY_RECORD`, `PAYMENT_OTP_CHALLENGE`, `RISK_REVIEW`, `LEDGER_POSTING`, `LEDGER_ENTRY`, `TRANSACTION_EXCEPTION`, `AUDIT_LOG`, and `APP_NOTIFICATION`.

The principal analytical views are `VW_TRANSACTION_DASHBOARD`, `VW_RISK_SUMMARY`, and `VW_PENDING_APPROVALS`.

Key invariants include ownership-composite foreign keys; contiguous, non-overlapping policy bands; state/risk/deadline/reservation consistency; optimistic version columns; one pending OTP per transaction; terminal OTP/review protection; append-only audit/ledger behavior; and balanced debit/credit settlement postings.

---

## 4. Backend implementation progression

The 18 September pre-change snapshot contained 253 production Java files and 145 test Java files. The Phase 1–4 compilation checkpoint covered 262 production files and 152 test files; use the change-request guide for its exact affected-file manifest.

### Phase 2.1 — shared foundations

Added server/database clock handling, exact money validation, correlation IDs, safe RFC-style problem responses, pagination, masking and common request/response infrastructure. This established consistent errors and validation before business APIs were added.

### Phase 2.2 — identity foundation, later completed as deferred security work

Initially mapped roles, users, user-role assignments and authentication sessions, plus registration and password foundations. Full Spring Security was intentionally paused so later modules could be built against stable identity contracts without temporary insecure identity parameters.

### Phase 2.3 — accounts and funds

Mapped account types/status/currency and Oracle sequences; added safe account projections and ownership reads; enforced current/reserved/available balance invariants; implemented pessimistically locked reserve, release and atomic source/clearing settlement operations. Owner credentials are used only for controlled fixtures/migrations, never runtime financial operations.

### Phase 2.4 — beneficiaries

Implemented owned beneficiary creation/list/detail/status APIs, bank-account versus UPI validation, sensitive destination masking, active-status enforcement, ownership isolation, and Oracle-backed repository/service verification.

### Phase 2.5 — deterministic risk engine

Mapped policy, policy bands and protection actions. Implemented database-driven amount-only classification, exact boundary behavior, policy/version snapshots, safe explanations and immutable published-policy assumptions. V1 does not invent device, location, velocity, beneficiary-age, anomaly or AI scores.

### Phase 2.6 — transaction and state engine

Implemented transaction creation, explicit authorization, state transitions, risk routing, fund reservation, protected deadlines, cancellation, detail/list/risk-explanation APIs, optimistic locking and safe conflict handling. Customer identity is derived from Spring Security rather than request data.

### Phase 2.7 — idempotency and request safety

Implemented 12-hour idempotency records, canonical request fingerprints, exact user/operation/key scoping, stored response replay, conflicting-payload rejection and Oracle uniqueness as the duplicate-race arbiter. Mutating transaction, OTP and review APIs require exactly one `Idempotency-Key`.

### Phase 2.8 — protection scheduler and cancellation races

Implemented server-authoritative MEDIUM/HIGH expiry release, bounded polling, transaction locking, fresh deadline checks, restart/backlog recovery and single-winner cancellation-versus-release behavior. VERY_HIGH holds are excluded from timer release.

### Phase 2.9 — ledger settlement and exceptions

Implemented atomic RELEASED-to-SETTLED processing, account locking, balanced immutable postings, internal outbound-clearing credit, settlement idempotency, retry delays of 5 seconds/30 seconds/1 minute, and durable exception/manual-review evidence after three retries. No partial balance or ledger mutation may survive a failed settlement.

### Phase 2.10 — OTP verification and development email delivery

Implemented cryptographically generated six-digit challenges, a fresh salt plus SHA-256 digest only, constant-time comparison, five-minute validity, three attempts, 30-second resend cooldown and three issues per verification cycle. Successful OTP moves a VERY_HIGH transaction to `PENDING_RISK_REVIEW`; exhaustion cancels and releases reservation safely.

Development Gmail delivery is opt-in. `FIXED_OVERRIDE` routing sends every showcase OTP to the configured local mailbox while retaining realistic stored customer emails. The current intended showcase recipient is `shawnlaisetti@gmail.com`; it is environment configuration, not hardcoded business data. Automated tests forcibly disable real email delivery.

### Phase 2.11 — Risk Officer review

Implemented review mapping/repositories, transaction-first locking, pending queue/detail, approval, rejection, re-verification and append-only notes. Review statuses are exactly `PENDING`, `APPROVED`, `REJECTED`, `REVERIFICATION_REQUESTED`, and `CANCELLED`. Approval retains reservation and releases; rejection releases reservation and cancels; re-verification retains reservation and opens a new review round only after a fresh successful OTP.

### Phase 2.12 — audit, notifications and real-time updates

Implemented append-only lifecycle evidence, role-shaped audit search/timelines, customer notification list/read APIs, durable notification dispatch/retry behavior and user-scoped STOMP event publication. Sensitive audit details are sanitized; customer timelines expose only safe events. Notification retries are bounded and durable.

### Deferred Phase 2.2 completion — SEC-A through SEC-K

Completed the original Spring Security contract rather than creating a new phase:

- BCrypt credential authentication and registration safety.
- Five failed logins followed by a 15-minute temporary lock.
- HS256 JWT access tokens with issuer/audience validation and 15-minute validity.
- Seven-day opaque refresh sessions; only hashes stored; rotation and replay-family revocation.
- Live account/status/security-version revalidation.
- Exact RBAC using the four canonical authorities.
- Strict HttpOnly refresh cookie, exact-origin credentialed CORS, and Origin plus CSRF protection for refresh/logout.
- Security headers and stable safe `401`/`403` problem responses.
- JWT-authenticated WebSocket/STOMP CONNECT, exact subscription authorization and denied client SEND frames.
- `SYSTEM_ADMIN` status/role/session-revocation controls with locks, target security-version invalidation, refresh revocation and audit evidence.

---

## 5. Current REST surface

### Public authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Refresh/logout use the refresh cookie plus the approved Origin/CSRF contract. Normal bearer-token business POST requests do not use refresh-cookie CSRF protection.

### Customer

- Beneficiaries: create, list, detail and status update under `/api/v1/beneficiaries`.
- Transactions: create, authorize, list, detail, risk explanation and cancel under `/api/v1/transactions`.
- OTP: issue, resend and verify under `/api/v1/transactions/{transactionId}/otp...`.
- Notifications: list and mark read under `/api/v1/notifications`.
- Safe owned transaction audit timeline: `GET /api/v1/transactions/{transactionId}/audit`.

### Risk Officer

- Queue, detail, approve, reject, request verification and add note under `/api/v1/admin/risk-reviews`.

### Auditor

- Global audit search at `GET /api/v1/audit-logs`.
- Authorized transaction timeline access.

### System Administrator

- User status changes, role grant/removal and session revocation under `/api/v1/admin/users`.

All protected routes derive the user and authorities from the validated SafePay principal. Ownership failures must not leak another customer's records.

---

## 6. Runtime configuration and operating state

Required external values include owner/app database credentials, the Oracle URL, a Base64 JWT secret of at least 256 bits, browser origin, local refresh-cookie security selection, outbound clearing account, and—when email is enabled—Gmail username/App Password/from address/routing mode/recipient override.

Two local Flyway modes are intentionally separated:

- Clean baseline: `classpath:db/migration`, target 11.
- Showcase: `classpath:db/migration,classpath:db/showcase`, target 12.

The current database is the V12 showcase database. Start it with the V12 showcase configuration. Flyway validates every start but does not reapply successful versioned migrations. Do not start the V1-V11-only location against the V12 history and misinterpret V12 as an unresolved migration.

For controlled API demonstrations, protection, notification and settlement schedulers may be disabled through environment variables to prevent background state changes. Verify the active run configuration instead of assuming defaults. Enable only the worker required by the scenario being demonstrated.

On 18 September 2026, the V12 run was verified to show successful Flyway application, Hikari startup, JPA initialization, WebSocket broker startup, Tomcat on port 8080 and `Started SafePayApplication`.

---

## 7. Verification status

**Current confirmed baseline — 19 September 2026:** user-run **1148 full-suite tests** passed at **22:39:13 IST**, with zero failures/errors/skips. The 31-test regression group and 178-test affected group also passed. The last fixes replaced the operational statistics outer-join/subquery with equivalent EXISTS predicates and added safe 404 handling for missing routes/resources; one regression test raised the previous 1147 expectation to 1148. All current backend/change-request implementation phases are locked. Earlier 866/918/1002/1054 results remain historical evidence, not the current baseline. Codex ran compilation only; all runtime results were supplied by the user.

- The user previously confirmed the full pre-email-routing backend/security suite at 866 tests with zero failures, errors or skips.
- The later OTP routing implementation added four tests; focused OTP/configuration/controller/exception tests passed at 49 tests and offline production/test compilation passed.
- Historical snapshot: Surefire reports for 145 classes aggregated to 870 tests, without a separately confirmed cumulative console run at that point. The later user-confirmed 918-test and current 1002-test full-suite results supersede that earlier verification gap.
- V12 initially failed safely before inserting showcase data because local PL/SQL functions were used inside SQL `VALUES` expressions and the old pre-V9 review sequence name remained. The three lookups were changed to typed PL/SQL variables, the sequence was corrected to `SEQ_RISK_REVIEW_ID`, the failed Flyway history marker was repaired, and V12 then applied successfully.
- Backend startup against the V12 schema is confirmed.
- Initial Postman OTP success/invalid-code workflows were exercised by the user. Their individual result is not being used as the final API acceptance gate; systematic phase-wise and end-to-end API testing remains next.

---

## 8. Major resolved incidents and lessons

- Oracle owner/runtime responsibility was separated so migrations and fixtures do not broaden `SAFEPAY_APP` privileges.
- Flyway checksum/history, empty migration, repeatable grant and view-access issues were diagnosed from the first real Oracle error rather than bypassed.
- Teammate schema differences were reconciled through V9-V11 instead of rewriting applied migrations.
- Repository/service Oracle tests use owner-created fixtures and restricted runtime operations with cleanup and rollback/concurrency checks.
- Full-suite-only failures caused by shared Spring contexts, mocks, fixture uniqueness and ordering were patched only in their affected tests/configuration.
- Security was completed only after business ownership/state contracts stabilized; temporary identity-taking endpoints were not introduced.
- Automated tests cannot accidentally send real OTP email.
- Fixed-recipient development routing lets customer identities remain realistic while directing all actual showcase OTPs to one controlled mailbox.
- A failed V12 attempt was not treated as success merely because Maven printed `BUILD SUCCESS`; the underlying Oracle compilation error and failed Flyway history row were repaired before rerun.
- Maven dependency-resolution failures were treated as environment/cache/network problems unless source evidence proved otherwise.

---

## 9. Current open and deferred work

Immediate next work is user review of documentation increment 1 (master foundation/index/Phase 2.1 and its paired API guide). The full 1148-test pass closes change-request Phases 7A/7B/7C/8. Keep target 13 and both migration locations; no migration rerun is needed. Backend implementation is frozen pending an explicit new change/fix approval.

At the final documentation stage, under `dbsetup_cum_backend_master_guide_contract.md`:

1. Build the master backend understanding guide incrementally from Phase 2.1 through Phase 2.12, including exact Java-to-SQL/constraint/sequence/trigger/view mappings and later-phase dependencies.
2. Create separate phase-wise API-test guides covering positive, negative, boundary, ownership, authorization, idempotency, concurrency, retry, recovery and audit/notification evidence cases.
3. Provide user-run customer/Risk Officer/SYSTEM_ADMIN/AUDITOR end-to-end scenarios against the V13 schema containing V12 showcase data. Do not claim API walkthroughs passed based only on Maven tests.
4. Preserve the confirmed 1148-test baseline and separately record later manual API evidence before frontend integration. OpenAPI remains pending before frontend integration; dependency/configuration additions require separate approval.

Documentation delivery rules:

- One master guide, incrementally appended in conceptual Phase 2.1–2.12 order; stop after each phase, state exactly what was added, and await approval.
- Every production class (current inventory 277 Java source files) must have explicit coverage: fields/types, constructors/factories/methods, validation, mutations, SQL mapping or explicit no-direct-mapping status, constraints/keys/sequences/indexes/views/triggers/procedures, consumers, downstream dependencies, chronology and limitations.
- Cover V1–V13 and repeatable grants, distinguishing V12 synthetic data from V13 category schema. Applied V1–V13 are immutable; any future approved migration must use the next verified free version (currently V14).
- Keep all original/deferred SEC-A–SEC-K security in logical Phase 2.2. Fold newer profile/user/account/auditor/operations/category capabilities into their logical domain chapters and cross-reference shared ownership.
- Produce each separate phase API-test guide alongside its matching incremental master chapter, then stop for review. Do not postpone all twelve API guides until the end. No invented controller or endpoint for infrastructure-only phases; use valid later APIs that exercise the phase indirectly.
- Keep test-class explanations brief: which classes cover which functionality, with detail only for a genuinely confusing case. Production classes still require the full member/mapping explanation.
- Explain new Java/JPA/Spring/SQL concepts in context with a topic label, plain-language example and primary-source study references where useful.
- Review current code/SQL first; reconcile decisions, operative contracts, old phase guides and only authorized historical chats. Flag uncertainty and do not invent rationale or schema behavior.
- A possible implementation flaw is an approval gate: report in chat, stop dependent documentation, and do not silently fix code or describe intended behavior as implemented behavior.
- GOALS_FOR_TODAY.txt also requires later database understanding, teammate backend comparison before frontend integration, and eventual presentation ownership/flow explanations. These are context only in the current roadmap; no comparison/merge/frontend/assignment work is authorized now.

Still deferred unless separately approved:

- Customer address during registration.
- Simulated PAN-format KYC validation; PAN must never be stored.
- Any role collapse or rename; the four canonical authorities remain unchanged.
- Multi-signal/device/location/velocity/anomaly/AI risk scoring.
- Real payment-rail integration or claims of external settlement/reversal.
- Oracle JET PWA implementation and final deployment hardening.

Operational cautions:

- OTP API tests mutate persistent V12 fixtures. `SPV1-SHOWCASE-105` and `SPV1-SHOWCASE-116` may no longer be in their original no-challenge state after manual testing; inspect before reuse.
- The V1 salted SHA-256 six-digit OTP design is acceptable only for this prototype and should be strengthened before any production/customer deployment.
- Local HTTP may use a non-secure refresh cookie only by explicit local configuration; production requires HTTPS and secure cookies.
- Fixed-recipient email routing is showcase-only. Production must use an approved stored-user delivery design and real operational controls.
- Oracle 23.26 currently produces a Flyway support-range warning; startup and migrations succeeded, but dependency/database compatibility should be revisited during deployment hardening.

---

## 10. Continuation references

Use these for exact detail rather than expanding this context file:

- `UPDATED_Decision_Register.md`
- `UPDATED_implementation_plan.md`
- `Contract for Codex Agent to Adhere to for Safepay.txt`
- `dbsetup_cum_backend_master_guide_contract.md`
- `Documentation for SafePay/SafePay_V1_Data_Seed_Complete_Guide.md`
- `Documentation for SafePay/SafePay_Backend_Launch_and_Server_Guide.md`
- `Documentation for SafePay/SafePay_Backend_Phase_2_4_Beneficiary_Module.md`
- Phase 2.5 through Phase 2.12 backend guides in `Documentation for SafePay`
- `Documentation for SafePay/SafePay_Backend_Deferred_Phase_2_2_Spring_Security.md`
- Current migrations under `backend/src/main/resources/db`
- Current production and test source under `backend/src/main/java` and `backend/src/test/java`
- Sidebar tasks `SafePay - DB Design`, `Safepay - DB to Backend shift`, and `Safepay - Backend Design Part 2` as historical progress sources, never as higher authority than current files and verified state.

## 11. Historical guide resume point (superseded by section 12)

Read this checkpoint and `dbsetup_cum_backend_master_guide_contract.md`, with the latest user instructions overriding its historical 866-test and V1–V11 references. Current inventory: 277 production / 159 test Java files; full suite 1148 confirmed green. Backend and change-request Phases 0–8 are locked. The user approved incremental guide creation with brief test-class summaries and paired chapter/API-guide delivery. Increment 1 now contains the master foundation, all-production-source coverage index and complete Phase 2.1 chapter in `Documentation for SafePay/SafePay_Master_Backend_Understanding_Guide.md`, plus `Documentation for SafePay/API Test Guides/SafePay_Phase_2_1_API_Test_Guide.md`. Both are ready for user review; their manual API cases have not been executed by Codex. STOP before Phase 2.2 until the user approves the next increment. No backend code/configuration/migration/test changes or builds were made for this documentation increment. OpenAPI remains pending before frontend integration. Vector search is discarded, the new administrative health feature remains excluded, and broader goals await a later roadmap.

## 12. Current continuation decisions — 20 September 2026

These explicit user decisions supersede the historical guide resume point above:

- Keep A (`SafePay`) as the common unchanged backend. The team repository `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend` remains read-only; no team users, accounts, payments or other data are to be imported into A.
- Keep A's existing prepared-account onboarding model. No automatic registration account creation or funding is approved.
- Master/backend/API guide development is paused while a working frontend prototype is prioritized. Detailed frontend file-by-file inspection is for a subsequent user-requested session; do not start it during the current planning task.
- Customer profile editing is deferred until after frontend completion/integration. Future scope is non-password profile editing; contact verification and authentication/session consequences must be explicitly resolved before implementation. Password-change/reset remains deferred.
- Daily reporting from B is selected for future adaptation, but implementation is deferred. At the post-frontend-completion/integration review, remind the user to revisit it. Adapt against A's schema and exact roles, rather than copying B's SQL. Reporting authority, creation-day versus settlement-day meaning, timezone and declined/rejected counting remain decisions to finalize then.
- Countdown convenience is planning-only: propose minimal customer transaction-detail GET enrichment using database time; preserve existing write/idempotency responses, financial logic and schema. No countdown implementation is approved by this request.
- No application code, configuration, migration or tests were changed or executed for these decisions. This context checkpoint is the only authorized project-file update in the planning turn.
- Preserve existing exclusions: no direct administrative balance editing or account deletion; account disabling remains deferred; Oracle vector search stays discarded, not pending. Documentation/OpenAPI work remains outstanding; no contract-completion claim is made by pausing it.
