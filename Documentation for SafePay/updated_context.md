# SafePay Updated Project Context Through Phase 2.3

**Context date:** 14 September 2026  
**Project root:** `C:\Users\Aditya Rao\Downloads\Training\Project\SafePay`  
**Backend root:** `C:\Users\Aditya Rao\Downloads\Training\Project\SafePay\backend`  
**Current verified Maven result:** 105 tests, 0 failures, 0 errors, 0 skipped  
**Current implementation position:** Phase 2.3E code and unit tests complete; the service-level Oracle mutation, rollback, and concurrency integration proof remains open before final Account-module closure.

---

## 1. Purpose and Use of This Context

This file records the work performed and decisions made from the beginning of the current SafePay thread through the Phase 2.3 source audit. It is a continuation aid and status record. It does not replace the approved Decision Register.

When continuing the project, use the following authority order:

1. The user's latest explicit instruction.
2. `AGENTS.md` at the SafePay project root.
3. `SafePay_V1_Decision_Register_Phase_1_9_Reconciled.md`.
4. `SafePay_V1_Implementation_Full_Plan_Phase_1_9_Reconciled.md`.
5. `SafePay_PRD.md`, where it does not conflict with the Decision Register.
6. Applied migrations, verified database evidence, and current source code.
7. Earlier context documents, teammate artifacts, and chat summaries as historical/navigation material.

The Decision Register is the V1 reference bible where older PRD material, teammate designs, or historical notes conflict.

---

## 2. Permanent Working Boundaries and User Preferences

- Application and test code is implemented manually by the user. The assistant provides exact paths, exact paste-ready content, precise replacement blocks, explanations, and verification instructions.
- The assistant must not directly create, edit, delete, or rearrange application code, tests, configuration, migrations, or project structure unless the user gives a specific direct exception.
- No terminal command may be run without the user's approval.
- No dependency, extension, package, or external tool may be installed or downloaded without the user's approval.
- No system-level changes are allowed.
- Applied migrations V1-V11 must never be edited. Any future database change must use an approved forward migration V12 or later.
- Teammate code, SQL, ZIP entries, and supporting material are reference inputs rather than authority. They must not be executed merely for inspection.
- Development proceeds in small units. Each unit is locked only after the user reports the focused and full verification results.
- Error guidance should name the exact file and exact block to change, and should state which nearby code must remain unchanged.
- Enterprise-quality logic and project decisions remain required even while development is being accelerated.
- A cumulative `main` and `test` source-tree reference should be maintained at phase locks.
- Short deferred-Spring-Security comments belong only in code with a real future security integration seam.

The user granted ongoing read permission for these two reconciled Markdown authorities:

- `SafePay_V1_Decision_Register_Phase_1_9_Reconciled.md`
- `SafePay_V1_Implementation_Full_Plan_Phase_1_9_Reconciled.md`

That read permission does not authorize file changes or terminal execution by itself.

---

## 3. Product and V1 Scope

SafePay V1 is a simulated risk-adaptive pre-settlement payment-control layer. It operates before internal rail submission. It must not claim that it can intercept or reverse a payment already settled on live NPCI or RBI rails.

The canonical V1 risk routes are:

- **LOW:** immediate release/settlement route.
- **MEDIUM:** 10-second protection window with customer cancellation.
- **HIGH:** 60-second protection window with warnings and customer cancellation.
- **VERY_HIGH:** OTP verification followed by a hard hold and risk review where required.

The project is an enterprise-oriented prototype using Java 21, Spring Boot 3.5.16, Spring Data JPA, Oracle Database, Flyway, optimistic version fields, explicit row locking where required, deterministic risk decisions, immutable ledger concepts, and an eventual Oracle JET PWA.

---

## 4. Chronological Work Record

### 4.1 Initial project-context reconstruction

The initial request was to obtain the full existing SafePay context from the supplied resources and project directory without executing teammate code or SQL. The authority hierarchy, safe working method, database baseline, phased implementation approach, and teaching style were reconstructed.

Two concrete conflicts were identified:

1. Some materials represented `policy_version` numerically while the intended canonical model required a textual version identity.
2. Older V5 review-status values differed from the final approved V1 vocabulary.

### 4.2 Reconciliation choices approved by the user

The following choices were explicitly approved:

- `policy_version` remains textual: Oracle `VARCHAR2(50 CHAR)` and Java `String`.
- A numeric policy revision is not substituted for the textual version identity.
- Risk-review status vocabulary is exactly:
  - `PENDING`
  - `APPROVED`
  - `REJECTED`
  - `REVERIFICATION_REQUESTED`
  - `CANCELLED`
- The approved spelling is `REVERIFICATION_REQUESTED`, not `VERIFICATION_REQUESTED` for the review decision/status vocabulary.
- Reverification closes the current review round. Successful renewed verification may create a new `PENDING` review round.
- The implementation of these changes was postponed until the teammate design had been fully inspected, avoiding unnecessary intermediate changes.

### 4.3 Phase 1.9 teammate intake and design comparison

The user supplied teammate SQL, seed data, backend source consolidated in Markdown, a Phase 1 specification, a README, and a database setup guide. The work followed the requested Phase 1.9 sequence:

1. **1.9A Intake and inventory:** catalogued SQL objects and backend files; identified missing and duplicated concepts.
2. **1.9B Design reconstruction:** derived teammate entities, relationships, lifecycle, risk logic, roles, and assumptions.
3. **1.9C Side-by-side comparison:** compared tables, columns, datatypes, precision, IDs, constraints, indexes, roles, permissions, transactions, concurrency, risk thresholds, ledger behavior, reservations, authentication, auditing, seed strategy, and JPA mappings.
4. **1.9D Cascading-impact analysis:** traced removal, rename, merge, or replacement effects into APIs, entities, services, security, state transitions, views, and tests.
5. **1.9E Canonical decision matrix:** categorized components as KEEP, ADAPT, MERGE, REPLACE, REJECT, or DEFER.
6. **1.9F Reconciliation strategy:** selected controlled forward migration and canonical schema conventions rather than replacing the stronger V1-V8 design with the simplified teammate schema.

The teammate design was retained as a source of reusable ideas and compatible naming where the decisions were KEEP, ADAPT, or MERGE. It was not adopted wholesale because it omitted or simplified important ownership, state, reservation, ledger, audit, and concurrency rules.

### 4.4 Approved Phase 1.9 reconciliation outcomes

The user approved all presented D1-D8 recommendations and the earlier policy-version and status decisions. The resulting canonical outcomes include:

- Rename the misleading maker-checker physical concept to `RISK_REVIEW`, because full maker-checker separation is future scope while the V1 review workflow is current scope.
- Preserve review rounds, decision actor/reason, timestamps, versioning, foreign keys, and one-open-review protection.
- Use four database authorities: `CUSTOMER`, `RISK_OFFICER`, `SYSTEM_ADMIN`, and `AUDITOR`.
- Present only two broad application personas in V1:
  - Customer has the `CUSTOMER` authority.
  - Combined Admin receives the three separate `RISK_OFFICER`, `SYSTEM_ADMIN`, and `AUDITOR` authorities.
- Do not create a generic `ADMIN` authority.
- Preserve textual policy versions and immutable policy snapshots on transactions.
- Enforce policy tuple consistency between risk policy, policy version, selected band, protection policy, band code, and risk tier.
- Enforce customer ownership consistency across transactions, source accounts, beneficiaries, OTP challenges, and reviews.
- Strengthen risk/state/reservation/deadline consistency.
- Validate risk-policy band completeness so active policy bands have no gaps or overlaps and produce exactly one match for each valid amount.
- Preserve the stronger canonical database design while adapting only useful teammate concepts.

The exact approved decision wording remains in the reconciled Decision Register and should be used instead of reconstructing D-number wording from memory.

### 4.5 Phase 1.9 forward migrations and verification

The user manually created and pasted:

- `V9__phase_1_9_vocabulary_reconciliation.sql`
- `V10__phase_1_9_integrity_hardening.sql`
- `V11__canonical_v1_reference_data.sql`
- `R__safepay_app_grants.sql`
- `phase_1_9_read_only_verification.sql`

The migrations were run through Spring Boot/Flyway. The verification package was executed successfully, with all intended zero-violation checks passing.

A V11 precondition initially raised `ORA-20200` because canonical role codes already existed. This was correctly recognized as the migration guard operating as designed rather than an Oracle failure. The final V9-V11 and repeatable-grant state was subsequently verified.

Two new reconciled Markdown authorities were created without replacing the older documents:

- `SafePay_V1_Decision_Register_Phase_1_9_Reconciled.md`
- `SafePay_V1_Implementation_Full_Plan_Phase_1_9_Reconciled.md`

Phase 1.9 was then officially closed.

### 4.6 Backend Phase 2 intake

The current backend and Maven configuration were mapped against the 19 canonical database tables. The backend implementation started as small manual units with tests at every gate.

The Spring Boot run configuration initially pointed to a JUnit test class and reported that `com.ofss.SafePayApplicationTests` could not be found. The configuration was corrected to use the Spring Boot application configuration with the Oracle environment variables.

The persistent environment-variable contract is:

- `SAFEPAY_DB_URL`
- `SAFEPAY_DB_APP_USERNAME`
- `SAFEPAY_DB_APP_PASSWORD`
- `SAFEPAY_DB_OWNER_USERNAME`
- `SAFEPAY_DB_OWNER_PASSWORD`

`application.properties` now keeps:

- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.jpa.open-in-view=false`
- default Hibernate schema `SAFEPAY_OWNER`
- UTC JDBC timezone handling
- Flyway execution as `SAFEPAY_OWNER`
- runtime datasource as restricted `SAFEPAY_APP`
- disabled Flyway clean and strict migration validation
- hidden detailed database health output

### 4.7 Phase 2.1 foundation implementation

The implemented foundation includes:

- Application UTC `Clock` configuration.
- Strict INR transaction-money validation with `BigDecimal`.
- Correlation-ID request filtering.
- Generic paged API response mapping.
- Field-level validation error representation.
- Stable business-rule, duplicate-resource, and missing-resource exceptions.
- Central `ProblemDetail` exception handling for validation, malformed JSON, duplicates, missing resources, business rules, invalid arguments, and unexpected failures.

The `PagedResponse` generic mapping produced a Java wildcard-capture error where the stream inferred `List<? extends T>` instead of `List<T>`. The mapping was corrected so the mapper result has an explicit `T` boundary while still rejecting null mapped elements.

JUnit execution first failed in the Eclipse JUnit launcher with `NoClassDefFoundError: org/junit/platform/engine/OutputDirectoryCreator`. Maven Surefire was used as the authoritative test runner. A later Maven attempt initially failed while resolving Surefire 3.5.6 artifacts, then succeeded once the dependencies were resolved. Maven full-suite output became the phase gate.

Exception-related test failures, including the duplicate-resource test, were corrected while keeping stable safe API errors.

### 4.8 Phase 2.2 identity and partial security foundation

The implemented identity/security foundation includes:

- Exact read-only `APP_ROLE` mapping through `Role` and `RoleName`.
- Restricted `RoleDao` without create/update/delete methods or runtime ID generation.
- `APP_USER`, `USER_ROLE`, and `AUTH_SESSION` JPA mappings.
- Composite `UserRoleId` mapping.
- User, role, user-role, and session repositories.
- User lookup service.
- BCrypt password hashing abstraction and implementation.
- Customer registration request/response, service, and controller endpoint.
- Customer registration assigns only the `CUSTOMER` authority.
- Login request validation.
- Credential-verification service.
- `SafePayPrincipal` and `SafePayUserDetailsService` authority loading.
- Password encoder configuration.

The database still stores four precise authorities. The product UI and prototype behavior use two broad personas. The combined administrator is represented by three independent role rows rather than a fifth `ADMIN` role.

The security baseline was locked through Phase 2.2L with 68 tests. Full Spring Security orchestration was then deliberately paused so the core SafePay transaction logic could be demonstrated sooner.

### 4.9 Deliberate Spring Security pause

Completed security-related files were retained. Nothing was deleted or rewritten merely because security was paused.

The following work was deliberately deferred:

- User login success/failure state transitions.
- Failed-login counting and timed account lockout orchestration.
- Login-attempt service.
- Login endpoint completion.
- JWT access-token creation and verification.
- Refresh-token/session rotation.
- Refresh-token replay detection and family/session revocation.
- Logout and server-side session revocation.
- Spring Security `SecurityFilterChain`.
- Authentication filter and request-context population.
- Stable security-specific HTTP 401 and 403 handlers.
- Endpoint and method-level RBAC.
- Password-change security-version increment and session revocation.
- Authentication/security audit events.
- WebSocket authentication and authorization.

Existing `spring-security-core` and `spring-security-crypto` support remained. The full security starter, JWT library, and security-test integration were not to be introduced during the pause without the later integration step.

Security must be integrated and tested before the frontend is treated as authenticated or production-like.

### 4.10 Phase 2.3A: ACCOUNT entity and enums

Implemented:

- `AccountType`
- `AccountStatus`
- `CurrencyCode`
- `Account`
- `AccountTest`

The entity maps the canonical `ACCOUNT` table, including:

- `ACCOUNT_ID`
- owner relationship through `OWNER_USER_ID`
- account number, account type, bank name, IFSC, and INR currency
- current and reserved balances using `NUMBER(18,2)` semantics
- read-only Oracle virtual `AVAILABLE_BALANCE`
- status
- optimistic `VERSION_NO`
- UTC-compatible created and updated timestamps

Customer accounts require a persisted owner, customer account type, and valid IFSC. System accounts require a system account type, no owner, and no IFSC.

The initial sequence mapping caused Hibernate validation to expect account-sequence access that the restricted runtime user does not have. The mapping was corrected by removing runtime account ID generation. Account provisioning remains database/migration/admin controlled; the runtime application cannot create or delete accounts.

An account factory test initially used a Mockito owner in a way that did not represent an unpersisted entity reliably. It was corrected to use a real transient `User` object.

Phase 2.3A was locked with 76 passing tests.

### 4.11 Phase 2.3B: Account repository

Implemented:

- `AccountDao`
- `AccountDaoIntegrationTest`

The repository supports:

- lookup by account ID
- lookup by unique account number
- ownership-aware lookup
- ordered listing by owner
- ownership existence check
- `PESSIMISTIC_WRITE` lookup for financial mutation

It intentionally exposes no save or delete API. Managed account updates use JPA dirty checking. Oracle runtime privileges remain `SELECT` and `UPDATE` for `ACCOUNT`, without account creation/deletion or sequence access.

The integration test verifies that ownership, unique-account, and lock queries execute against Oracle and that mutation repository methods are absent.

Phase 2.3B was locked with 80 passing tests.

### 4.12 Phase 2.3C: account reads, masking, and active ownership checks

Implemented:

- `SensitiveDataMasker`
- `AccountSummaryResponse`
- `AccountBalanceResponse`
- `AccountService`
- `AccountServiceImpl`
- `SensitiveDataMaskerTest`
- `AccountServiceImplTest`

The service provides:

- owned-account listing
- owned-account detail
- owned balance detail
- required active owned-account lookup for later transaction flows

Unknown and non-owned accounts both return `ACCOUNT_NOT_FOUND`, preventing ownership enumeration. Inactive owned accounts produce `ACCOUNT_INACTIVE` when an active account is required.

Account numbers are masked while preserving only the final four characters. IDs are represented to API clients as strings. Monetary values are represented as decimal strings.

During the security pause, internal methods temporarily receive a trusted `ownerUserId`. Future controllers must derive it from `SafePayPrincipal`; it must never come from request JSON.

Phase 2.3C was locked with 88 passing tests.

### 4.13 Phase 2.3D: balance invariants and financial state operations

Implemented in `Account` and verified by `AccountFinancialStateTest`:

- reserve funds from available balance
- accumulate reservations exactly
- reject insufficient available balance without mutation
- release reserved funds without reducing current balance
- consume a reservation during settlement
- credit a system settlement account
- reject wrong account categories
- reject amounts with more than two decimal places

The canonical formulas are:

- `availableBalance = currentBalance - reservedAmount`
- reservation increases only `reservedAmount`
- cancellation/release decreases only `reservedAmount`
- settlement consumption decreases both `currentBalance` and `reservedAmount`
- clearing credit increases the clearing account's `currentBalance`

No retained ₹5,000 or hidden minimum balance rule exists.

Phase 2.3D was locked with 96 passing tests.

### 4.14 Phase 2.3E: transactional financial service

Implemented:

- `AccountFundsService`
- `AccountFundsServiceImpl`
- `AccountFundsServiceImplTest`

The service:

- acquires a pessimistic row lock before account financial mutation
- verifies ownership before reservation
- hides wrong ownership as `ACCOUNT_NOT_FOUND`
- permits release of an existing reservation even if the account has since become inactive, preventing trapped funds
- locks two settlement accounts in ascending ID order to reduce deadlock risk
- requires a customer-owned source account
- requires an active `OUTBOUND_CLEARING` destination
- consumes the source reservation and credits clearing inside one writable Spring transaction
- relies on JPA managed-entity dirty checking rather than repository save calls

The unit tests verify ownership, missing accounts, reservation delegation, release, deterministic lock order, pre-mutation loading, clearing-account validation, same-account rejection, and writable transaction metadata.

The full suite reached 105 passing tests.

**Open closure item:** there is no `AccountFundsServiceIntegrationTest` or equivalent service-level Oracle test. The current `AccountDaoIntegrationTest` runs against Oracle but uses unknown IDs; it proves query execution rather than real financial mutation, database rollback, or concurrent reservation behavior. Phase 2.3 is therefore implemented and unit-green, but its planned Oracle integration proof is still pending.

---

## 5. Canonical Database Baseline

The 19 canonical tables are:

1. `APP_ROLE`
2. `APP_USER`
3. `USER_ROLE`
4. `AUTH_SESSION`
5. `ACCOUNT`
6. `BENEFICIARY`
7. `RISK_POLICY`
8. `PROTECTION_POLICY`
9. `RISK_POLICY_BAND`
10. `PAYMENT_TRANSACTION`
11. `TRANSACTION_RISK_FACTOR`
12. `IDEMPOTENCY_RECORD`
13. `PAYMENT_OTP_CHALLENGE`
14. `RISK_REVIEW`
15. `LEDGER_POSTING`
16. `LEDGER_ENTRY`
17. `TRANSACTION_EXCEPTION`
18. `AUDIT_LOG`
19. `APP_NOTIFICATION`

Applied and verified database artifacts:

- V1 security and users
- V2 accounts and beneficiaries
- V3 risk policy
- V4 transactions and idempotency
- V5 OTP and risk approvals/reviews
- V6 ledger and exceptions
- V7 audit and notifications
- V8 analytical/reconciliation views
- V9 Phase 1.9 vocabulary reconciliation
- V10 Phase 1.9 integrity hardening
- V11 canonical V1 reference data
- repeatable `SAFEPAY_APP` grants

`all_sql.txt` also exists under the migration directory. Its `.txt` extension means it is not a Flyway versioned migration. It is currently a reference/combined artifact and should not be treated as a replacement for V1-V11.

---

## 6. Current Source Inventory After the Audit

### 6.1 Main Java source

```text
backend/src/main/java/com/ofss
├── SafePayApplication.java
├── beans
│   ├── Account.java
│   ├── AccountStatus.java
│   ├── AccountType.java
│   ├── AuthSession.java
│   ├── CurrencyCode.java
│   ├── Role.java
│   ├── RoleName.java
│   ├── User.java
│   ├── UserRole.java
│   ├── UserRoleId.java
│   └── UserStatus.java
├── common
│   ├── ClockConfiguration.java
│   ├── CorrelationIdFilter.java
│   ├── MoneyUtility.java
│   ├── PasswordConfiguration.java
│   ├── SensitiveDataMasker.java
│   └── api
│       ├── FieldValidationError.java
│       └── PagedResponse.java
├── controller
│   └── AuthController.java
├── dto
│   ├── account
│   │   ├── AccountBalanceResponse.java
│   │   └── AccountSummaryResponse.java
│   └── auth
│       ├── LoginRequest.java
│       ├── RegisterUserRequest.java
│       └── RegisterUserResponse.java
├── excp
│   ├── BusinessRuleException.java
│   ├── DuplicateResourceExcp.java
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundExcp.java
├── repository
│   ├── AccountDao.java
│   ├── AuthSessionDao.java
│   ├── RoleDao.java
│   ├── UserDao.java
│   └── UserRoleDao.java
├── security
│   ├── SafePayPrincipal.java
│   └── SafePayUserDetailsService.java
└── services
    ├── AccountFundsService.java
    ├── AccountFundsServiceImpl.java
    ├── AccountService.java
    ├── AccountServiceImpl.java
    ├── BCryptPasswordHashingService.java
    ├── CredentialAuthenticationService.java
    ├── CredentialAuthenticationServiceImpl.java
    ├── PasswordHashingService.java
    ├── UserRegistrationService.java
    ├── UserRegistrationServiceImpl.java
    ├── UserService.java
    └── UserServiceImpl.java
```

### 6.2 Test Java source

```text
backend/src/test/java/com/ofss
├── SafePayApplicationTests.java
├── beans
│   ├── AccountFinancialStateTest.java
│   └── AccountTest.java
├── common
│   ├── CorrelationIdFilterTest.java
│   ├── MoneyUtilityTest.java
│   ├── SensitiveDataMaskerTest.java
│   └── api
│       └── PagedResponseTest.java
├── controller
│   └── AuthControllerTest.java
├── dto/auth
│   ├── LoginRequestTest.java
│   └── RegisterUserRequestTest.java
├── excp
│   ├── DuplicateResourceErrorTest.java
│   ├── GlobalExceptionHandlerTest.java
│   ├── RequestValidationErrorTest.java
│   └── ResourceNotFoundErrorTest.java
├── repository
│   ├── AccountDaoIntegrationTest.java
│   ├── AuthSessionDaoIntegrationTest.java
│   ├── RoleDaoIntegrationTest.java
│   ├── UserDaoIntegrationTest.java
│   └── UserRoleDaoIntegrationTest.java
├── security
│   └── SafePayUserDetailsServiceTest.java
└── services
    ├── AccountFundsServiceImplTest.java
    ├── AccountServiceImplTest.java
    ├── BCryptPasswordHashingServiceTest.java
    ├── CredentialAuthenticationServiceImplTest.java
    ├── UserRegistrationServiceImplTest.java
    └── UserServiceImplTest.java
```

### 6.3 Resources

```text
backend/src/main/resources
├── application.properties
└── db/migration
    ├── all_sql.txt
    ├── R__safepay_app_grants.sql
    ├── V1__security_and_users.sql
    ├── V2__accounts_and_beneficiaries.sql
    ├── V3__risk_policy.sql
    ├── V4__transactions_and_idempotency.sql
    ├── V5__otp_and_risk_approvals.sql
    ├── V6__ledger_and_exceptions.sql
    ├── V7__audit_and_notifications.sql
    ├── V8__analytical_and_reconciliation_views.sql
    ├── V9__phase_1_9_vocabulary_reconciliation.sql
    ├── V10__phase_1_9_integrity_hardening.sql
    └── V11__canonical_v1_reference_data.sql

backend/src/test/resources
└── db/verification
    └── phase_1_9_read_only_verification.sql
```

---

## 7. Audit Result at the End of Phase 2.3

### 7.1 Confirmed on track

- All expected Phase 2.3 production classes through the financial orchestration service are present.
- Account persistence matches the canonical Oracle object and restricted runtime privilege model.
- Account IDs are not runtime-generated.
- Available balance is treated as an Oracle virtual/read-only column and is also derived safely in-memory after mutation.
- Money uses `BigDecimal` with strict two-decimal and precision validation.
- Customer/system account-type invariants are enforced.
- Ownership-aware account reads prevent cross-customer disclosure.
- Account numbers are masked in response DTOs.
- Account mutations use a writable transaction and pessimistic row locks.
- Settlement uses deterministic two-account lock ordering.
- Clearing destination is restricted to `OUTBOUND_CLEARING`.
- JPA dirty checking is used without exposing account save/delete methods.
- Optimistic `@Version` remains mapped as an additional stale-write defense.
- Security integration seams are marked only in the account files that need future principal-derived ownership.
- The user reported 105 tests passing after Phase 2.3E.

### 7.2 Immediate gap before final Phase 2.3 closure

The planned **service and Oracle integration tests** are only partly satisfied:

- Present: Oracle-backed `AccountDaoIntegrationTest` for repository queries and lock-query execution.
- Missing: Oracle-backed `AccountFundsServiceIntegrationTest` for real managed-entity mutation and transaction behavior.

The missing integration test should prove at least:

1. A valid reservation commits `RESERVED_AMOUNT` and reduces derived available balance.
2. Reservation cannot exceed available balance.
3. Releasing a reservation restores available balance without changing current balance.
4. Settlement decreases source current/reserved balances and increases outbound-clearing current balance atomically.
5. A forced failure after the source debit causes the complete transaction to roll back.
6. Competing reservations cannot both spend the same available balance.
7. Lock acquisition works against real existing rows rather than only an unknown ID.

The test must use controlled test fixtures and transaction cleanup that do not alter V1-V11 or leave permanent financial data. If the current restricted runtime grants cannot safely create fixtures, fixture setup must use an approved owner-side test mechanism without adding runtime account-creation privileges.

### 7.3 Deliberately deferred account/API items

- Account REST controller.
- Authenticated current-user account listing/detail/balance endpoints.
- Extraction of `ownerUserId` from `SafePayPrincipal`.
- Endpoint authorization and customer/admin access rules.
- Public account provisioning; this is intentionally excluded from runtime V1 APIs.
- End-to-end transaction-driven account reservation, cancellation, and settlement; these will be connected in Phases 2.6, 2.8, and 2.9.
- High-contention transaction race tests beyond the service-level Oracle proof; broader state/cancellation races belong to Phase 2.8.

---

## 8. Complete Pending and Deferred Work List

### 8.1 Account-module closure

- Add and pass the missing service-level Oracle mutation/rollback/concurrency integration test.
- Decide the controlled fixture strategy before writing that test; do not broaden `SAFEPAY_APP` privileges merely to make tests convenient.
- After this passes, mark Phase 2.3 fully closed and begin Phase 2.4.

### 8.2 Paused security suite

- User login state/lockout transitions.
- Login-attempt orchestration.
- Login endpoint.
- JWT issue/validate/filter pipeline.
- Refresh rotation and replay response.
- Logout/session revocation.
- Security filter chain.
- 401/403 response handlers.
- Method and endpoint RBAC.
- Password-change session invalidation.
- Authentication/security auditing.
- WebSocket security.
- Account and later controllers must derive actor/owner from the authenticated principal.

### 8.3 Core backend phases still to implement

1. **Phase 2.4 — Beneficiary module**
2. **Phase 2.5 — Versioned risk policy and deterministic risk engine**
3. **Phase 2.6 — Transaction creation and state engine**
4. **Phase 2.7 — Idempotency and request safety**
5. **Phase 2.8 — Protection scheduler and cancellation races**
6. **Phase 2.9 — Ledger settlement and exception recording**
7. **Phase 2.10 — Payment OTP verification**
8. **Phase 2.11 — Risk review workflow**
9. **Phase 2.12 — Audit, notifications, and core event production**

### 8.4 Later integration before frontend

- Complete the paused Spring Security suite.
- Connect all owner and actor IDs to `SafePayPrincipal`.
- Add protected controllers and stable authorization behavior.
- Add security and domain audit integration.
- Add authenticated WebSocket/STOMP behavior where required.
- Run the complete unit, JPA/Oracle integration, concurrency, security, and application-context suites.
- Only then treat the backend contract as ready for the Oracle JET PWA integration.

### 8.5 Database change control

- Do not edit V1-V11.
- Any new constraint, index, grant, seed adjustment, or other schema change requires explicit approval and a V12+ forward migration.
- Keep `R__safepay_app_grants.sql` as the repeatable runtime-grant source.
- Continue using `SAFEPAY_OWNER` for migrations and restricted `SAFEPAY_APP` at runtime.

---

## 9. Next Safe Implementation Step

The next step is a narrow **Phase 2.3E Oracle integration completion unit**. It should add the missing real-row reservation, atomic settlement, rollback, and concurrency proof without changing production behavior or runtime privileges.

After that focused integration test and the full Maven suite pass, Phase 2.3 can be formally locked. Phase 2.4 should then begin with a read-only reconstruction of the canonical `BENEFICIARY` table and constraints, followed by entity/enums, repository, ownership-aware service, and tests in small verified units.

---

## 10. Current Continuation Checkpoint

- **Database:** V1-V11 and repeatable grants applied and verified.
- **Documentation:** reconciled Decision Register and full plan exist.
- **Backend foundations:** implemented and verified.
- **Identity foundation:** implemented through the pre-JWT credential/principal layer.
- **Security:** intentionally paused after Phase 2.2L; all completed files retained.
- **Account production code:** implemented through transactional funds orchestration.
- **Account unit/repository verification:** passing.
- **Full Maven suite:** 105 passing tests as reported by the user.
- **Outstanding account proof:** real service-level Oracle mutation/rollback/concurrency integration test.
- **Next domain:** Beneficiary, after the account integration gap is closed.

