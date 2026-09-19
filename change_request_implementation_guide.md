# SafePay Change Request — Implementation Plan and Guide

**Document:** change_request_implementation_guide.md  
**Consolidated:** 19 September 2026  
**Project root:** C:/Users/Aditya Rao/Downloads/Training/Project/SafePay  
**Status:** Change-request Phases 0–8 are implemented and user-verified; backend implementation is frozen for the current scope. Phase 9 documentation increment 1 (master foundation/index/Phase 2.1 and its paired API guide) is ready for review; Phase 2.2 awaits approval. Strict category validation, including old category-free high-value POST rejection, remains approved. Codex did not execute migrations, Maven tests or application startup.  
**Verified runtime baseline:** User screenshots show **31 regression tests** passing at **22:32:49 IST**, **178 affected tests** at **22:33:48 IST**, and **1148 full-suite tests** at **22:39:13 IST**, 19 September 2026; zero failures/errors/skips and BUILD SUCCESS throughout. V13 deployment and startup were previously confirmed. Later checkpoint notes below supersede earlier pending/expected statuses.

## Contents

1. Authority, provenance, and working boundaries
2. Final scope and corrections to earlier proposals
3. Existing implementation and preservation matrix
4. Common API, security, query, and representation contract
5. Sequential implementation phases
6. Phase 1 — SYSTEM_ADMIN account directory and details
7. Phase 2 — SYSTEM_ADMIN user/customer directory
8. Phase 3 — CUSTOMER own profile
9. Phase 4 — CUSTOMER transaction-history filters
10. Phase 5 — Above-INR-1-lakh payment categories and officer priority
11. Phase 6 — AUDITOR review and transaction evidence
12. Phase 7 — AUDITOR ledger, reconciliation, exceptions, and policies
13. Phase 8 — SYSTEM_ADMIN statistics and failure views
14. Phase 9 — Final documentation and API-testing guides
15. Consolidated endpoint and permission matrix
16. Production file manifest
17. Test strategy, execution gates, and expected-count discipline
18. Critical compatibility decisions and approval checklist
19. Completion and continuation checklist

## 1. Authority, provenance, and working boundaries

### 1.1 Source of this contract

This guide consolidates only the change-request discussion in this same task, beginning with the user's request to plan the capabilities in the two text files, through the latest reversal of the proposed legacy backfill.

Relevant project references:

| Reference | Use |
|---|---|
| [Selected capabilities](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/to_be_implemented.txt) | Initial positive scope; later user instructions override stale entries |
| [Deferred capabilities](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/currently_deferred_enpoints_capabilities.txt) | Explicit exclusions from the current implementation |
| [Pending/history notes](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/pending_gaps.txt) | Historical navigation; not blanket authorization for additional features |
| [Updated Decision Register](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/UPDATED_Decision_Register.md) | Current baseline contracts except where explicitly amended by this change request |
| [Original Decision Register](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/SafePay_V1_Decision_Register.md) | Original role/API intent, subordinate to later decisions |
| [Original PRD](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/SafePay_PRD.md) | Product context; does not reinstate superseded features |
| [Backend/database context](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/SafePay_Backend_DB_Context.md) | Earlier implementation context |
| [Master guide contract](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/dbsetup_cum_backend_master_guide_contract.md) | Final understanding/testing deliverables |
| [Project instructions](C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/AGENTS.md) | Working rules, subject to direct user instructions |

Later user instructions in this task control conflicts. This guide records the latest selections; older proposals to backfill categories, expose balances in account lists, or add administrative health reporting must not be revived.

The original guide-creation instruction authorized documentation and a memory note only. Subsequent approvals covered Phases 1–4, then 5A–6, including strict old high-value POST replay rejection and user-controlled V13 deployment. Both batches are verified. The latest instruction authorizes scoped direct implementation of Phases 7A/7B/7C/8 together. Database execution, Maven tests and configuration changes are not implied by source implementation approval.

### 1.2 Working boundaries

- Read-only file listing/search/inspection is permitted.
- Scoped direct source writes are permitted only after the user approves the immediate implementation plan and affected files. Paste-ready manual implementation is not required unless requested.
- Do not run Maven tests, start the application, execute SQL/Flyway, or change database state autonomously.
- Offline compile/test-compile checks are permitted within an approved implementation phase; they are not runtime verification.
- No downloads, installations, extensions, packages, system changes, broad refactors, or unrelated edits without explicit approval.
- Applied V1–V12 migrations remain immutable. A schema change requires a separately approved forward-only migration.
- Do not treat static SQL-file inspection as proof of the live schema or grants.
- No new tests, migrations, application configuration, or source files are created merely because their names appear here.
- SafePay remains a simulated pre-settlement control layer. No live payment-rail interception or reversal is implied.

### 1.3 Current baseline and stale notes

The customer account APIs are implemented and their verification gate passed. The user confirmed the individual tests, the combined **60-test** account group, and the clean **918-test** full suite.

That historical baseline is superseded by the 19 September change-request verification: **142 focused tests** passed at **18:38:18 IST**, followed by **1002 full-suite tests** at **18:41:02 IST**. Both console screenshots show BUILD SUCCESS and zero failures, errors or skips. The two empty-page fixtures in TransactionQueryServiceTest were corrected to preserve PageRequest.of(0,20); no production pagination validation was weakened.

The earlier full-suite ORA-12514 failure was resolved by the user's database/service restart. It is not an open code defect. Historical “manual verification pending” text in pending_gaps.txt is superseded by the supplied passing result.

No future test total is known until the additional tests are written and counted.

## 2. Final scope and corrections to earlier proposals

### 2.1 Selected implementation scope

| Role | Selected capability |
|---|---|
| SYSTEM_ADMIN | Paginated customer-account directory; customer/minimum-current-balance/account-type filters; detail and balance reads |
| SYSTEM_ADMIN | Paginated user/customer search and individual user detail |
| SYSTEM_ADMIN | Operational statistics and detailed failure reads |
| CUSTOMER | Own-profile GET endpoint |
| CUSTOMER | Transaction-history date, state, and source-account filters |
| CUSTOMER | Category selection only for newly created payments strictly above INR 100000.00; conditional Others reason |
| RISK_OFFICER | Category filtering and category-priority sorting on the existing paginated pending-review endpoint |
| AUDITOR | Read-only review queue/detail and transaction/risk evidence |
| AUDITOR | Ledger postings/entries, two existing reconciliation views, exceptions, and policy-version reads |
| Final stage | OpenAPI; master backend understanding guide; comprehensive API-testing guides |

“Admin” means the combined persona holding SYSTEM_ADMIN, RISK_OFFICER, and AUDITOR. Each endpoint still checks its exact authority. A combined persona receives the union of those permissions, not a new ADMIN authority or CUSTOMER ownership.

### 2.2 Explicit exclusions — no implementation steps in this contract

| Item | Disposition |
|---|---|
| Account disabling/reactivation | Excluded from this contract |
| Account deletion or direct balance-edit APIs | Prohibited |
| Branch/location filtering | Excluded; current account model has no location field |
| CUSTOMER password change/reset | Excluded |
| SYSTEM_ADMIN force-password-reset | Excluded |
| RISK_OFFICER general account directory | Excluded |
| New SYSTEM_ADMIN health endpoint/capability | Removed entirely from this change request; no health DTO, service, probe, scheduler-property inspection, or health-specific tests |
| Oracle vector/embedding/semantic-search integration | Discarded, **not deferred and not a pending item**; no prototype, model, dependency, index, configuration, or future implementation slot |
| Unrelated suggestions appearing elsewhere in pending_gaps.txt | Not automatically included; for example, the support/contact-email form is not part of the selected change request |

Existing basic application health functionality is not removed: the exclusion concerns the newly proposed SYSTEM_ADMIN health feature.

Previously excluded or future product ideas—Maker-Checker, editable risk policies, safety preferences, disputes, device/location risk, general AI assistance—are not expanded or planned here.

### 2.3 Final decisions that replace earlier drafts

| Topic | Final decision |
|---|---|
| Account-list fields | Exactly accountId, ownerId, ownerName, maskedAccountNumber, accountType, bankName, ifscCode, status |
| Account detail | Formatted account columns, full account number, all three balances; owner name as an additional descriptive field |
| Balance filtering | minCurrentBalance, inclusive currentBalance >= value; never reserved or available balance filtering |
| Full account number | Individual SYSTEM_ADMIN account detail only among these new staff account responses |
| Auditor reservation figures | Stored reservation, calculated reservation, and difference permitted; current/available balance omitted |
| Categories | One enum containing five values |
| New-payment applicability | Strictly amount > 100000.00; exactly 100000.00 remains uncategorized |
| Others note | Reuse purpose; required trimmed 1–140 characters for new above-threshold OTHERS payments |
| Existing rows | Category remains NULL; original purpose remains exactly as stored |
| Legacy presentation | Above-threshold category NULL displays “Not specified”; not a sixth enum |
| Legacy queue position | After the five real categories |
| Legacy lower-value responses | Category property omitted, like new lower-value payments |
| Seed handling | No category/purpose backfill, no reseed, no default OTHERS, no replacement with “not specified” |
| Review priority | MEDICAL, LOAN, FRIENDS_FAMILY, INVESTMENTS, OTHERS, NULL; oldest first within each group |
| Documentation | Final stage after implementation and verification; OpenAPI before frontend integration |

## 3. Existing implementation and preservation matrix

| Existing component | Current behavior | Preservation rule |
|---|---|---|
| AccountController / AccountService | Customer-owned list/detail/balance | Keep existing routes, owner checks, and customer DTOs |
| AccountDao / Account | Ownership reads, financial fields, locks | Add staff read queries only; no account mutation |
| AdminUserSecurityController / service | User status, roles, session revocation | Add GET operations; preserve mutation and audit semantics |
| TransactionController / service | Create, authorize, list, detail, cancel, risk explanation | Extend only selected query/create metadata paths |
| RiskReviewController / service | Pending queue, detail, decisions, notes | Extend queue filters/order; preserve financial decisions |
| AuditController / AuditQueryService | Global audit and role-scoped timelines | Keep intact; new evidence reads use separate routes |
| Ledger/exception/policy entities and repositories | Financial evidence and internal reads | Add non-locking read queries only |
| PagedResponse | items/page/size/totalElements/totalPages/first/last | Reuse unchanged |
| GlobalExceptionHandler | Existing problem responses | Reuse existing validation/access/not-found handling |
| SecurityConfig / JWT converter | Exact authorities, live user/role/security-version checks | Add explicit routes; preserve authentication mechanics |
| Schedulers, OTP, idempotency engine, settlement/state/funds services | Canonical processing | No algorithm change; only create-request fingerprint input gains category where applicable |
| Existing SQL views and grants | Reporting foundations | Read existing structures; do not rewrite them |

Payment category must not become a risk signal. Amount-only classification, OTP, review eligibility, protection periods, reservation, cancellation, settlement, ledger entries, and audit immutability retain their contracts.

## 4. Common API, security, query, and representation contract

### 4.1 Types and validation

| Concern | Contract |
|---|---|
| Prefix | /api/v1 |
| IDs | Long internally; string identifiers in DTOs following current conventions |
| Money | BigDecimal internally, never binary floating point; new financial fields use exact two-decimal strings |
| Existing DTO money | Preserve current types/serialization; do not globally refactor existing BigDecimal responses |
| Dates | OffsetDateTime; normalize query timestamps to UTC |
| New date ranges | from inclusive, to exclusive; reject from >= to when both supplied |
| List defaults | page=0, size=20; validate page >= 0 and 1 <= size <= 100 |
| Counts | Database count query with identical filter predicates |
| Ordering | Fixed order plus unique ID tie-breaker; no unrestricted client property sorting |
| No results | Empty page, not an error |
| Invalid input | Existing 400 response conventions |
| Missing detail | Existing resource-not-found convention |
| Authorization | Existing 401/403 conventions; no entity/data query before necessary authorization |

New date filtering uses **from <= timestamp < to**. Do not change existing audit endpoint date semantics as collateral work. Full-day UI selections should be converted to start-of-day and next-day-start in an explicit timezone.

Do not reuse MoneyUtility.requireValidTransactionAmount() for minCurrentBalance: it enforces a minimum INR 1 transaction amount. A minimum-balance filter of zero is valid.

### 4.2 Query methods and transactional boundaries

- New staff read methods use read-only transactions and exact-role checks.
- Add methods to existing restricted Repository interfaces rather than replacing them with broad CRUD repositories.
- Queries remain parameterized. Escape wildcard search input deliberately.
- Fetch to-one associations needed for DTOs; do not paginate a to-many fetch join.
- For paginated user lists, fetch role assignments for the selected page in one batch.
- All filtering/ordering is applied before database pagination. Do not retrieve everything and filter in Java.
- Do not take write locks for reporting.
- Explicit JDBC projections over views avoid additional JPA entities.
- Reuse existing DTO mapping and SensitiveDataMasker where compatible.
- Use bound parameters and fixed allowlisted identifiers for native SQL.
- Existing grants are checked before implementation execution; missing live grants require an explicit forward-only correction plan, not broad privilege escalation.
- Financial/report data changes concurrently. Deterministic pagination is not a frozen snapshot across separate requests.

### 4.3 Shared staff access helper

New file: **N01** in the manifest, StaffReadAccess.java.

Method: requireActiveRole(actorUserId, requiredRole).

Use UserDao and UserRoleDao to confirm an active user with the required authority. New account, auditor-evidence, and operations services use it. Existing administrator/audit services retain their existing equivalent checks; no wholesale refactor.

### 4.4 Route protection

Add explicit root and descendant matching as appropriate:

| Route | Authority |
|---|---|
| /api/v1/admin/accounts and children | SYSTEM_ADMIN |
| /api/v1/admin/users and children | SYSTEM_ADMIN |
| /api/v1/admin/operations and children | SYSTEM_ADMIN |
| /api/v1/users/me | CUSTOMER |
| /api/v1/audit and children | AUDITOR |
| Existing /api/v1/admin/risk-reviews routes | RISK_OFFICER, unchanged |

Apply controller method security as well. Auditor routes do not broaden the existing officer controller annotation. SYSTEM_ADMIN alone does not gain approval or general auditor-evidence rights.

## 5. Sequential implementation phases

The initial recommended sequence is preserved, with categories inserted before the new auditor DTO consumers to reduce repeated integration changes.

| Phase | Deliverable | Gate |
|---|---|---|
| 0 | Approve this contract and resolve compatibility choices | No implementation until approved |
| 1 | SYSTEM_ADMIN account reads | Focused/security/query verification |
| 2 | SYSTEM_ADMIN user/customer directory | Query counts and security verification |
| 3 | CUSTOMER own profile | Identity and privacy verification |
| 4 | CUSTOMER transaction filters | Ownership and date-boundary verification |
| 5A | Approve category migration/deployment and replay compatibility | Separate database execution authorization |
| 5B | Category persistence/create validation/response mapping | Legacy and amount-boundary verification |
| 5C | Officer category filtering/priority | Global pagination and decision-regression verification |
| 6 | AUDITOR review and transaction evidence | Read/action separation verification |
| 7A | Ledger reads | Financial evidence mapping verification |
| 7B | Reconciliation reads | Oracle projection and privacy verification |
| 7C | Exception and policy reads | Safe fields and historical evidence verification |
| 8 | SYSTEM_ADMIN stats and failures | Counter/query/role verification |
| 9 | OpenAPI and final guides | Document only verified final behavior |

At every gate: explain the exact affected files, implement only the approved increment, run permitted compilation, provide user-run focused test instructions/counts, inspect results, and obtain confirmation before advancing. No retrospective claim that the entire contract was implemented merely because one phase passed.

**Approved batch exception, 19 September 2026:** the user authorized Phases 1–4 together in this order. Implement and compile the batch before handing over runtime tests; separate runtime confirmation between these four phases is waived for this batch only. This does not authorize Phase 5 or database operations.

## 6. Phase 1 — SYSTEM_ADMIN account directory and details

### 6.1 Endpoints

| Method | Endpoint | Parameters/return |
|---|---|---|
| GET | /api/v1/admin/accounts | customerId, minCurrentBalance, accountType, page, size; PagedResponse<AdminAccountSummaryResponse> |
| GET | /api/v1/admin/accounts/{accountId} | AdminAccountDetailResponse |
| GET | /api/v1/admin/accounts/{accountId}/balance | Existing AccountBalanceResponse |

Only customer-owned SAVINGS/CURRENT accounts are included. Internal clearing/control accounts are excluded from these routes. Inactive customer accounts remain inspectable.

### 6.2 Exact fields

| List item — only these fields | Detail — formatted account columns |
|---|---|
| accountId | accountId |
| ownerId | ownerId |
| ownerName | ownerName, additional descriptive field |
| maskedAccountNumber | accountNumber, full |
| accountType | accountType |
| bankName | bankName |
| ifscCode | ifscCode |
| status | status |
| — | currency |
| — | currentBalance |
| — | reservedAmount |
| — | availableBalance |
| — | versionNo |
| — | createdAt |
| — | updatedAt |

List items contain **no balances, currency, timestamps, or full account number**. The normal PagedResponse envelope is retained.

The balance endpoint reuses AccountBalanceResponse and keeps its maskedAccountNumber. No raw User entity, password data, or nested entity graph is exposed.

Illustrative list item:

~~~json
{
  "accountId": "501",
  "ownerId": "101",
  "ownerName": "Example Customer",
  "maskedAccountNumber": "********9012",
  "accountType": "SAVINGS",
  "bankName": "Example Bank",
  "ifscCode": "ABCD0123456",
  "status": "ACTIVE"
}
~~~

### 6.3 Methodology and methods

1. Validate SYSTEM_ADMIN through the shared helper.
2. Validate positive customerId and permitted accountType.
3. Validate minCurrentBalance >= 0, at most two decimal places, NUMBER(18,2) range.
4. Combine supplied filters using AND.
5. Apply currentBalance >= minCurrentBalance.
6. Sort accountId ASC.
7. Map list/detail independently so full numbers cannot enter list serialization.

Service methods:

- searchAccounts(administratorId, customerId, minCurrentBalance, accountType, page, size)
- getAccount(administratorId, accountId)
- getBalance(administratorId, accountId)

AccountDao additions:

- searchCustomerAccounts(customerId, minCurrentBalance, accountType, pageable)
- findCustomerAccountById(accountId)

Both queries enforce account type/ownership eligibility. Fetch owner through a to-one association where needed.

### 6.4 Files

Create N02–N06; extend E01/E02; reuse AccountBalanceResponse, SensitiveDataMasker, PagedResponse and the existing entity. Do not change AccountService's customer methods or any account mutation method.

### 6.5 Acceptance checks

Verify exact list-field allowlist; full number only in administrator detail; balances only in permitted responses; type/customer/minimum filters in combination; zero and precision limits; excluded internal accounts; inactive accounts; stable pagination/counts; all single-role denials and combined-admin success.

## 7. Phase 2 — SYSTEM_ADMIN user/customer directory

### 7.1 Endpoints

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/admin/users | q, role, status, page, size |
| GET | /api/v1/admin/users/{userId} | Individual user security/identity read |

Reuse AdminUserSecurityResponse and PagedResponse. role=CUSTOMER supplies a customer picker for account filtering.

### 7.2 Methodology

1. Add read-only service methods alongside existing administration methods.
2. Use existing active-administrator checking without lockParticipants().
3. Search name/email case-insensitively and mobile text using a bounded q.
4. Apply RoleName and UserStatus filters with AND semantics.
5. Use EXISTS for the role predicate, preserving one row per user.
6. Sort userId ASC.
7. Batch-load role assignments for the selected user IDs, including role entities needed for mapping.
8. Return existing safe user DTO fields; never serialize User.

Methods:

- searchUsers(administratorId, q, role, status, page, size)
- getUserDetails(administratorId, targetUserId)
- UserDao.searchUsers(q, role, status, pageable)
- UserRoleDao.findAllForUsers(userIds)

### 7.3 Files and tests

Extend E03–E07; no new production DTO/service/controller needed.

Extend existing AdminUserSecurityControllerTest, AdminUserSecurityServiceImplTest and repository integration coverage. Verify multi-role users are not duplicated, page totals are accurate, CUSTOMER filters work, and all existing status/role/session mutation tests still pass.

## 8. Phase 3 — CUSTOMER own profile

### 8.1 Endpoint and fields

GET /api/v1/users/me

Response: userId, fullName, email, mobileNumber, status.

The user ID comes solely from AuthenticatedUser. No target ID/role parameter is accepted. Existing authentication already supplies role information.

### 8.2 Methodology

1. Require CUSTOMER at the route/controller.
2. Call UserService.getOwnProfile(customerUserId).
3. Reuse UserDao.findById().
4. Map to CustomerProfileResponse.
5. Omit password hashes, security version, failure counters, secrets and tokens.

### 8.3 Files and tests

Create N07/N08; extend E01/E08/E09. Reuse existing User entity and DAO.

Test principal-derived identity, denial for staff-only identities, allowed profile fields, missing-resource handling and absence of sensitive fields. Do not add a general public GET /users/{id} as a side effect.

## 9. Phase 4 — CUSTOMER transaction-history filters

### 9.1 Existing endpoint extension

GET /api/v1/transactions

| Parameter | Meaning |
|---|---|
| from | Inclusive creation timestamp |
| to | Exclusive creation timestamp |
| state | Existing TransactionState |
| sourceAccountId | Source account filter within the customer's owned payments |
| page / size | Existing pagination |

Keep current TransactionSummaryResponse and ordering: createdAt DESC, transactionId DESC.

### 9.2 Methodology

1. Preserve the original no-filter behavior.
2. Derive customer identity from the authenticated principal.
3. Validate range, enums, ID and pagination.
4. Add a filtered listTransactions overload.
5. Use ownership AND every supplied filter in the database.
6. A foreign sourceAccountId yields no owned matches, not cross-owner data.
7. Keep count-query predicates aligned.

Methods:

- Existing listTransactions(customerUserId, page, size) remains.
- New listTransactions(customerUserId, from, to, state, sourceAccountId, page, size).
- TransactionDao.searchOwned(customerUserId, from, to, state, sourceAccountId, pageable).
- Keep findAllOwned() for the original no-filter path.

### 9.3 Files and tests

Extend E10–E13. No new production file required for filtering itself.

Extend TransactionControllerTest, TransactionQueryServiceTest, TransactionDaoIntegrationTest and relevant Oracle service coverage. Verify offsets, exact time boundaries, combined filters, empty pages, ownership and unchanged unfiltered calls.

## 10. Phase 5 — Above-INR-1-lakh categories and officer priority

### 10.1 Applicability and invariant

One new enum: PaymentCategory.

| Value | Display label | Priority |
|---|---|---:|
| MEDICAL | Medical | 1 |
| LOAN | Loan | 2 |
| FRIENDS_FAMILY | Friends & Family | 3 |
| INVESTMENTS | Investments | 4 |
| OTHERS | Others | 5 |
| NULL, not an enum | Not specified, historical above-threshold rows only | Last |

The threshold is **amount > 100000.00**, using BigDecimal. Exactly INR 100000.00 is HIGH and requires no category; INR 100000.01 is above threshold.

The category threshold is metadata validation tied to the current approved band boundary, not a second risk engine. Centralize the comparison and verify it against current policy boundaries in tests. Never use float/double.

MEDICAL is a customer-declared category, not verified medical urgency. No additional emergency flag or evidence workflow is introduced.

### 10.2 Final legacy table — no backfill

| Payment group | Database category | API/UI behavior | Purpose |
|---|---|---|---|
| Existing <= INR 100000.00 | NULL | Category property omitted; no category UI | Preserve original value, including null |
| Existing > INR 100000.00 | NULL | category may be null; UI shows “Not specified” where applicable | Preserve original value, including null |
| New <= INR 100000.00 | NULL | No category selection/property | Existing optional purpose, max 280 |
| New > INR 100000.00, non-OTHERS | Selected category | Required at creation and displayed | Optional purpose, max 280 |
| New > INR 100000.00, OTHERS | OTHERS | Required at creation and displayed | Required trimmed reason, 1–140 |

All existing records, including all V12 seeds and runtime-created records predating this change, remain uncategorized. Do not infer MEDICAL from “Hospital advance” or infer any other category from free text.

Do not UPDATE old rows to OTHERS, rewrite purpose, use a default category, add a sixth enum, or reseed. Old above-threshold payments remain authorizable/reviewable/settleable under existing lifecycle rules.

### 10.3 Schema plan and execution gate

Proposed migration name: V13__payment_category.sql **only if V13 remains the next available version at implementation time**.

Location is listed as M01 in the manifest. V12 is in the showcase location; review all configured migration locations before selecting the next version.

Proposed column: PAYMENT_TRANSACTION.PAYMENT_CATEGORY VARCHAR2(20 CHAR), nullable, no non-null default.

Constraint intent:

1. Non-null values must belong to the five-value set.
2. Payments at or below INR 100000.00 cannot store a non-null category.
3. OTHERS requires a nonblank trimmed purpose within 140 characters.
4. Above-threshold NULL must remain permitted for historical records.

Because historical and new above-threshold rows share the table, a universal NOT NULL requirement is invalid. The create request/service/factory enforces mandatory category for **new** above-threshold payments. The database cannot distinguish creation era without adding further metadata, which is not planned.

Approval and deployment sequence:

1. Review migration DDL and exact new constraints.
2. User approves and performs or explicitly authorizes execution.
3. Verify existing row counts, balances, states and purpose values remain unchanged.
4. Verify new column is null on all existing rows and Hibernate mapping matches.
5. Coordinate application version and migration target before startup/integration tests.
6. If test/run configurations still pin SPRING_FLYWAY_TARGET=12, obtain approval to advance to the actual new version. Keeping target 12 would leave the new entity mapping without its column.
7. Keep both approved migration locations where the existing showcase database requires them.
8. Do not rebuild the schema, run Flyway clean/repair, edit old migrations, or automatically change deployment configuration.

Adding one field is the sole planned schema change for this contract. Any additional index or privilege change requires evidence and separate review.

### 10.4 Creation request rules

POST /api/v1/transactions retains existing fields and gains conditional category.

~~~json
{
  "sourceAccountId": 501,
  "beneficiaryId": 701,
  "amount": 5000.00,
  "purpose": "Monthly expenses",
  "customerReference": "PAY-001"
}
~~~

~~~json
{
  "sourceAccountId": 501,
  "beneficiaryId": 701,
  "amount": 150000.00,
  "category": "MEDICAL",
  "purpose": "Hospital advance",
  "customerReference": "PAY-002"
}
~~~

~~~json
{
  "sourceAccountId": 501,
  "beneficiaryId": 701,
  "amount": 150000.00,
  "category": "OTHERS",
  "purpose": "Equipment purchase",
  "customerReference": "PAY-003"
}
~~~

- At/below threshold: omit category or send null; reject any non-null category.
- Above threshold: category is mandatory and must be one of the enum values.
- OTHERS above threshold: trim purpose and require 1–140 characters.
- Other categories: preserve optional purpose and its existing 280-character bound.
- No new otherReason property/column.
- Blank input normalization must happen before conditional validation.
- Validate amount precision/minimum using existing rules before category applicability.
- Do not add unconditional @NotNull to category.
- The UI clears category when the amount moves below the threshold; normal purpose text remains available.
- Authorization, cancel, OTP and review-decision request bodies do not gain category.

### 10.5 Persistence, DTOs, and idempotency

- Add EnumType.STRING mapping and getter in TransactionDb; do not store enum ordinals.
- Set category at instruction creation; do not add an edit-category endpoint.
- Extend creation factory/service narrowly. Retain old Java signatures where safe, but do not use overloads to bypass mandatory new-payment validation or fabricate OTHERS.
- Update affected test constructors/fixtures deliberately.
- Add category to TransactionResponse, TransactionSummaryResponse and RiskReviewSummaryResponse; the existing review-detail wrapper inherits it.
- Include purpose in review summary/detail so the officer can inspect the Others reason.
- Legacy null is valid in response mapping. Do not make DTO construction reject it.
- Lower-value responses omit category entirely; above-threshold legacy responses can carry category:null.
- Use property-specific serialization/mapping, not a global Jackson null-omission change. Test mixed pages and avoid inadvertently hiding existing unrelated fields.
- Verify cached old JSON responses still deserialize after DTO evolution.
- Extend TransactionController.canonicalCreateRequest() to include category when supplied, preserving the old canonical map for lower-value requests.
- Same key plus changed category is an idempotency conflict, as with other changed semantic inputs.
- Do not rewrite stored idempotency fingerprints or response bodies.

**Compatibility decision — explicitly approved:** an old above-threshold create request without category fails the new request validation before replay lookup, including when its Idempotency-Key already exists. No compatibility bypass is implemented. Existing stored payments still function without a retroactive category; this is different from replaying an old POST. Adding a category to a previously accepted category-free request changes its fingerprint and is not a way to replay that old request.

### 10.6 Officer queue

Extend existing GET /api/v1/admin/risk-reviews:

| Parameter | Proposed behavior |
|---|---|
| category | Optional enum filter; omitted includes legacy NULL |
| sort | PRIORITY default; OLDEST retains FIFO option |
| page / size | Existing defaults/bounds |

Sort PRIORITY globally in SQL:

MEDICAL -> LOAN -> FRIENDS_FAMILY -> INVESTMENTS -> OTHERS -> NULL, then requestedAt ASC, approvalId ASC.

Only eligible pending reviews are returned. Existing workflows create those reviews after the required verification; category does not create a review for a small payment or skip OTP.

Methods:

- Keep listPending(officerId, page, size).
- Add listPending(officerId, category, sort, page, size).
- Add a filtered pending-review DAO query with matching count.
- Use allowlisted sort values; no client-controlled SQL/property names.
- If priority CASE ranks repeat enum order in SQL, test their consistency.
- Keep review decisions, state guards, note mutation, reservation release and settlement processing unchanged.

No separate API or frontend page is necessary. Existing category-null high-value reviews remain usable and appear after categorized rows in PRIORITY mode. OLDEST avoids imposing category priority when the officer deliberately selects FIFO; no new aging algorithm is introduced.

### 10.7 Files

Create N09/M01. Extend E10/E12/E14–E21 and relevant tests. No payment-category table, category management controller, separate note field, new role, or weighted-risk configuration is required.

### 10.8 Tests

| Input/condition | Expected |
|---|---|
| 100000.00, category absent | Accepted subject to existing validation |
| 100000.00, category MEDICAL | 400 |
| 100000.01, category absent | 400 for new creation |
| 100000.01, category MEDICAL | Accepted subject to existing validation |
| 100000.01, OTHERS + blank reason | 400 |
| OTHERS reason length 140 | Accepted |
| OTHERS reason length 141 | 400 |
| Existing high-value null category | Existing lifecycle operations continue |
| Existing purpose text | Preserved exactly |
| Mixed response page | Category absent on lower rows; correct high-row representation |
| Priority spans multiple pages | Global order/count correct; stable tie-breakers |
| Same create key, different category | Existing conflict behavior |
| Old persisted response JSON | Deserializes without invented category |
| Existing financial/OTP/state tests | Remain correct |

### 10.9 Frontend boundary

No active frontend package was found during the planning inspection. Do not invent an existing view-model path or start frontend scaffolding.

Record only the integration requirements for the later frontend work: conditional selector, conditional Others reason, “Not specified” for historical high-value records, queue category filter and sort control. The API-testing guide remains a final-stage deliverable.

## 11. Phase 6 — AUDITOR review and transaction evidence

### 11.1 Shared auditor architecture

Create N10–N12: AuditEvidenceController, AuditEvidenceService, AuditEvidenceServiceImpl.

Use /api/v1/audit routes with AUDITOR checks and the shared access helper. Preserve existing AuditController/AuditQueryService timeline and global-audit methods.

### 11.2 Read-only review routes

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/audit/risk-reviews | status=PENDING, page, size; select another valid status for historical reviews |
| GET | /api/v1/audit/risk-reviews/{reviewId} | Existing detail response |

Methods: listReviews(auditorId, status, page, size), getReview(auditorId, reviewId).

Reuse RiskReviewDao.findAllByStatusOrderByRequestedAtAscApprovalIdAsc() and findByApprovalId(). Reuse review summary/detail DTOs, including their final category fields. This resolves the earlier permission discrepancy without granting auditors officer actions.

Do not automatically impose officer-specific category priority on historical auditor lists. Default auditor ordering remains the documented existing query order; additional auditor filters require scoped agreement.

### 11.3 Transaction and risk evidence routes

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/audit/transactions | customerId, state, from, to, page, size |
| GET | /api/v1/audit/transactions/{transactionId} | Payment detail plus stored risk evidence |

Methods: searchTransactions(...), getTransactionEvidence(auditorId, transactionId).

- Add dedicated non-owner-scoped auditor read queries after authority checking.
- Never call customer services with a substituted customer identity.
- Reuse TransactionSummaryResponse for the list.
- New N13 composes customerId, existing TransactionResponse, and a nested typed risk-evidence list.
- Use stored factor/band/tier/explanation/evaluation information.
- Reuse existing persisted-evidence consistency validation where applicable.
- Unassessed CREATED payments have an empty risk-evidence list, not a fabricated assessment.
- Existing transaction audit endpoint remains the timeline source; no duplicate timeline route is needed.

Extend E13 and E22: searchForAudit(...), findForAuditById(...), findAllForAuditByTransactionId(...).

### 11.4 Verification

Test single-role access, combined persona, denied officer mutations for auditor-only users, review histories, historical policies, unassessed transactions, missing/inconsistent evidence, and no current-account balance leakage from nested entities.

## 12. Phase 7 — AUDITOR ledger, reconciliation, exceptions, and policies

### 12.1 Ledger posting/entry reads

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/audit/ledger-postings | transactionId, posting status, creation from/to, page, size |
| GET | /api/v1/audit/ledger-postings/{postingId} | Header and ordered entries |

Create N14, AuditLedgerPostingResponse, with nested header/entry records. List returns headers; detail contains entries. Add searchForAudit(...) and findByPostingId(...) to E23.

Reuse LedgerEntryDao.findAllByPosting_PostingIdOrderByLineNumberAsc(). Include opening-balance postings with null transactionId. Never assume every posting is a payment settlement.

Expose permitted posting identifiers, type/status, amount/currency, entry counts/timestamps, account IDs/masked identifiers, line numbers and debit/credit amounts. Do not expose replay keys merely because entities contain them. No edit/repair operations.

### 12.2 Reconciliation

| Method | Endpoint | Parameters |
|---|---|---|
| GET | /api/v1/audit/reconciliation/ledger | postingId, reconciliationStatus, page, size |
| GET | /api/v1/audit/reconciliation/reservations | accountId, reconciliationStatus, page, size |

Read existing SAFEPAY_OWNER.VW_LEDGER_RECONCILIATION and SAFEPAY_OWNER.VW_RESERVATION_RECONCILIATION. Existing grants source includes SELECT for SAFEPAY_APP.

Create N15 ReportingReadRepository using existing Spring JDBC infrastructure and explicit column projections. Create N16 AuditReconciliationResponse with separate nested ledger/reservation records.

| View | Permitted fields |
|---|---|
| Ledger | Posting identifiers/status/amount/currency, expected/actual entries, debit/credit counts/totals, reconciliation status, appropriate timestamps |
| Reservations | Account ID, masked number, currency, stored reservation, calculated reservation, difference, reconciliation status, updated time |

**Omit current_balance and available_balance from auditor reservation SQL projection and DTO**, despite their presence in the view.

Use view-derived statuses: ledger INCOMPLETE/BALANCED/MISMATCH; reservations MATCH/MISMATCH. Validate each endpoint's own allowed statuses.

These views verify balanced postings and reservation consistency. They do not provide complete ledger-to-current-account-balance reconstruction. Do not label them as such.

Native-query implementation must verify Oracle NUMBER and timestamp mappings, identical count filters, deterministic pagination, and no modification of underlying views.

### 12.3 Exception reads

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/audit/exceptions | transactionId, processingStage, status, occurrence from/to, page, size |
| GET | /api/v1/audit/exceptions/{exceptionId} | Safe exception evidence |

Use lastOccurredAt for the proposed occurrence range and sort lastOccurredAt DESC, transactionExceptionId DESC; confirm these names in the phase contract.

Create N17 AuditExceptionResponse. Extend E24 with searchForAudit(...) and findByTransactionExceptionId(...).

Fields include IDs/reference, transaction/posting IDs, stage, errorCode, retryability/count, next retry time, occurrence/resolution timestamps, permitted resolving-user ID and correlation ID. A controlled display explanation may be derived from known error codes.

Do not return raw errorMessage, arbitrary stack traces, or unrestricted resolution text by default. Unknown codes receive a generic display explanation. Do not misuse the existing audit-JSON sanitizer as a general-purpose free-text sanitizer.

### 12.4 Policy-version reads

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/audit/risk-policies | Optional existing policy status, page, size |
| GET | /api/v1/audit/risk-policies/{policyVersion} | Stored header and bands |

Create N18 AuditRiskPolicyResponse with nested band records. Extend E25 for paginated policy listing; reuse findByPolicyVersion() and RiskPolicyBandDao.findAllForPolicy().

Include stored version/name/status/algorithm/currency/effective dates and ordered bands with min/max amount, tier, protection code/release mode/duration and approved OTP/review/cancellation flags.

Read inactive/historical versions as stored. Do not route historical inspection through current eligible-policy selection or recalculate old payment decisions.

### 12.5 Verification

Cover zero-result pages, opening balances, null transaction/posting references, exact money, allowed reconciliation statuses, omission of current/available balance, historical policies/open-ended top bands, safe exception fields and authorization. No mutation of ledger, exception resolution, risk policy, or account balance is added.

## 13. Phase 8 — SYSTEM_ADMIN statistics and failures

### 13.1 Endpoints

| Method | Endpoint | Contract |
|---|---|---|
| GET | /api/v1/admin/operations/stats | Current operational counters |
| GET | /api/v1/admin/operations/failures | Source, transactionId, occurrence from/to, page, size |

There is **no proposed /health route**. No Health nested DTO, probe, liveness field, scheduler-properties dependency or new health configuration is to be created.

Create N19–N23: controller, service interface/implementation, OperationalDashboardResponse for statistics only, and OperationalFailureResponse. Reuse N15 reporting repository.

### 13.2 Statistics methodology

Recommended initial counters:

- Current payment counts by state.
- Current pending-review count.
- Exception counts by OPEN, RETRY_PENDING, MANUAL_REVIEW.
- Notification counts by PENDING, RETRY_PENDING, FAILED.
- Expired protected-payment backlog.
- Settlement work currently due under the existing latest-exception/retry rules.

Use grouped/count queries, not entity loading or the existing batch-limited worker selection methods as total counts.

Where consistency across counters matters, use one Oracle SELECT with aggregate subqueries and database observation time. Document that these are current-state/workload counters, not all-time event totals or scheduler-health conclusions.

Due-work predicates must match current worker eligibility, including latest relevant settlement exception and retry time. Reporting does not invoke workers.

Method: getStatistics(administratorId).

### 13.3 Failure methodology

One paginated view covers transaction-processing exceptions and failed/retrying notification records.

- source is an allowlisted TRANSACTION or NOTIFICATION value; omission can include both.
- Each result includes source type and record ID to disambiguate identical numeric IDs.
- Use a defined occurrence timestamp: exception lastOccurredAt, notification failedAt where populated, otherwise updatedAt for retry records.
- Sort event time DESC with source and ID tie-breakers.
- Project code, safe display explanation, stage/status where applicable, retry information, relevant timestamps, transaction ID and correlation ID.
- Do not include notification body, personal recipient data, raw technical exception text, credentials or mail configuration.
- Database pagination/count must cover the same combined sources.
- No extra detail route is necessary if all permitted failure details fit this response.

Method: searchFailures(administratorId, source, transactionId, from, to, page, size).

### 13.4 Verification

Validate counters against known fixtures; no overcount from joins; correct latest-exception eligibility; notification retries/terminal failures; combined-source pagination/counts; empty state; authority denial; no SQL writes or worker invocation.

## 14. Phase 9 — Final documentation and API-testing guides

Only after implemented phases and their required runtime gates pass:

1. Reconcile the final endpoint/field/error/security contract.
2. Produce OpenAPI documentation, with any dependency or configuration addition separately scoped and approved.
3. Produce the master backend understanding guide.
4. Produce comprehensive role-wise API-testing guides with environment setup, request examples, expected responses, sequencing and cleanup boundaries.
5. Record what was actually verified, using user-supplied runtime evidence.
6. Complete OpenAPI before frontend integration.

Focused verification instructions during implementation are still necessary; deferring the comprehensive guides does not defer all testing.

## 15. Consolidated endpoint and permission matrix

All additions below are GET/read-only except the existing payment-create request gaining category metadata.

| Endpoint/change | CUSTOMER | SYSTEM_ADMIN | RISK_OFFICER | AUDITOR |
|---|:---:|:---:|:---:|:---:|
| GET /api/v1/admin/accounts | — | Yes | — | — |
| GET /api/v1/admin/accounts/{accountId} | — | Yes | — | — |
| GET /api/v1/admin/accounts/{accountId}/balance | — | Yes | — | — |
| GET /api/v1/admin/users | — | Yes | — | — |
| GET /api/v1/admin/users/{userId} | — | Yes | — | — |
| GET /api/v1/users/me | Own | — | — | — |
| GET /api/v1/transactions filters | Own | — | — | — |
| POST /api/v1/transactions conditional category | Own | — | — | — |
| GET /api/v1/admin/risk-reviews category/priority | — | — | Yes | — |
| GET /api/v1/audit/risk-reviews | — | — | — | Yes |
| GET /api/v1/audit/risk-reviews/{reviewId} | — | — | — | Yes |
| GET /api/v1/audit/transactions | — | — | — | Yes |
| GET /api/v1/audit/transactions/{transactionId} | — | — | — | Yes |
| GET /api/v1/audit/ledger-postings | — | — | — | Yes |
| GET /api/v1/audit/ledger-postings/{postingId} | — | — | — | Yes |
| GET /api/v1/audit/reconciliation/ledger | — | — | — | Yes |
| GET /api/v1/audit/reconciliation/reservations | — | — | — | Yes |
| GET /api/v1/audit/exceptions | — | — | — | Yes |
| GET /api/v1/audit/exceptions/{exceptionId} | — | — | — | Yes |
| GET /api/v1/audit/risk-policies | — | — | — | Yes |
| GET /api/v1/audit/risk-policies/{policyVersion} | — | — | — | Yes |
| GET /api/v1/admin/operations/stats | — | Yes | — | — |
| GET /api/v1/admin/operations/failures | — | Yes | — | — |

A combined three-role administrator gets the union of the three staff columns. It does not gain CUSTOMER routes without CUSTOMER itself and applicable ownership.

Existing audit timelines, auth, notifications, beneficiary, review decisions and administration mutations retain their established permissions.

## 16. Production file manifest

Names below are planning targets, not files already created. Existing file edits are additive/scoped unless explicitly described otherwise. Refer to phase acceptance checks before editing.

### 16.1 Proposed new source files

| ID | Absolute path | Responsibility |
|---|---|---|
| N01 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/security/StaffReadAccess.java | Active exact-role helper for new staff reads |
| N02 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/AdminAccountController.java | Three account GET routes |
| N03 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminAccountService.java | Account read contract |
| N04 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminAccountServiceImpl.java | Validation, authority, account reads |
| N05 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/admin/AdminAccountSummaryResponse.java | Exact eight-field list item |
| N06 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/admin/AdminAccountDetailResponse.java | Full permitted account detail |
| N07 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/CustomerProfileController.java | Own-profile route |
| N08 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/user/CustomerProfileResponse.java | Safe profile projection |
| N09 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/beans/PaymentCategory.java | Five values and shared applicability semantics |
| N10 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/AuditEvidenceController.java | Auditor evidence routes |
| N11 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AuditEvidenceService.java | Evidence read contract |
| N12 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AuditEvidenceServiceImpl.java | Authorized evidence composition |
| N13 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/audit/AuditTransactionDetailResponse.java | Existing transaction DTO plus typed risk evidence |
| N14 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/audit/AuditLedgerPostingResponse.java | Header/detail/entry records |
| N15 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/ReportingReadRepository.java | Parameterized JDBC projections/aggregates |
| N16 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/audit/AuditReconciliationResponse.java | Distinct ledger/reservation nested records |
| N17 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/audit/AuditExceptionResponse.java | Safe exception evidence |
| N18 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/audit/AuditRiskPolicyResponse.java | Stored policy and nested bands |
| N19 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/AdminOperationsController.java | Statistics/failures only |
| N20 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminOperationsService.java | Statistics/failure read contract |
| N21 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminOperationsServiceImpl.java | Authorized operational reads |
| N22 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/admin/OperationalDashboardResponse.java | Statistics only; no health record |
| N23 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/admin/OperationalFailureResponse.java | Typed combined failure source |

Small response-only child types should be nested rather than split into many tiny files. No generic framework or mapper dependency is planned. If implementation reveals a genuinely necessary extra file, explain its purpose and update the scoped plan before creating it.

### 16.2 Existing source files to extend

| ID | Absolute path | Permitted change |
|---|---|---|
| E01 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/security/SecurityConfig.java | Exact new route matchers |
| E02 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/AccountDao.java | Staff customer-account reads |
| E03 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/AdminUserSecurityController.java | Two user-directory GET routes |
| E04 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminUserSecurityService.java | Read method declarations |
| E05 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/AdminUserSecurityServiceImpl.java | Read-only user-directory logic |
| E06 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/UserDao.java | Paginated filtered users |
| E07 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/UserRoleDao.java | Page-scoped bulk role read |
| E08 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/UserService.java | getOwnProfile declaration |
| E09 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/UserServiceImpl.java | Own-profile mapping |
| E10 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/TransactionController.java | History parameters and category fingerprint |
| E11 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/TransactionService.java | Filtered history overload |
| E12 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/TransactionServiceImpl.java | Filtered reads and creation metadata |
| E13 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/TransactionDao.java | Owned filters and auditor read queries |
| E14 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/beans/TransactionDb.java | Nullable category mapping/factory/getter |
| E15 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/transaction/CreateTransactionRequest.java | Conditional category/purpose validation |
| E16 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/transaction/TransactionResponse.java | Conditional category response |
| E17 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/transaction/TransactionSummaryResponse.java | Conditional category response |
| E18 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/riskreview/RiskReviewSummaryResponse.java | Category and purpose |
| E19 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller/RiskReviewController.java | Queue parameters |
| E20 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/RiskReviewService.java | Queue overload |
| E21 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/RiskReviewServiceImpl.java and C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/RiskReviewDao.java | Queue validation/filter/ordering |
| E22 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/TransactionRiskFactorDao.java | Auditor evidence read |
| E23 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/LedgerPostingDao.java | Non-locking detail/search |
| E24 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/TransactionExceptionDao.java | Non-locking detail/search |
| E25 | C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/RiskPolicyDao.java | Paginated historical listing |

M01: C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/db/migration/V13__payment_category.sql — proposed next version only, subject to inventory and explicit migration approval.

Tests and approved final documentation are additional phase-specific files. This manifest does not authorize arbitrary editing of neighbouring files.

### 16.3 Reuse without planned alteration

Account, AccountStatus, AccountType, AccountService/implementation, AccountBalanceResponse, AccountSummaryResponse, AdminUserSecurityResponse, PagedResponse, SensitiveDataMasker, MoneyUtility transaction validation, LedgerEntryDao, RiskPolicyBandDao, RiskReviewDetailResponse, TransactionRiskExplanationResponse, existing AuditController/AuditQueryService, and existing financial/OTP/state/settlement services.

Retain existing response-field names even where new staff DTOs use a different explicit contract. Do not rename existing currencyCode fields to currency throughout the codebase.

## 17. Test strategy, execution gates, and expected-count discipline

### 17.1 Existing baseline

| Evidence | Status |
|---|---|
| AccountControllerTest | 18 passed in user rerun |
| AccountEndpointSecurityTest | User reported individual success; planned count 25 |
| AccountServiceImplTest | 10 in the focused group |
| SecurityAuthorizationContractTest | 7 in the focused group |
| Combined account group | User screenshot confirms 60 passed |
| Full suite | User screenshot confirms 918 passed, 0 failures/errors/skips |

These are historical results from this task, not a fresh execution during documentation creation. The old 870 report aggregate is not the current verification baseline.

### 17.2 Test placement

Test root: C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss

Proposed focused new tests:

| Absolute path | Coverage |
|---|---|
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/security/StaffReadAccessTest.java | Live status and exact-role helper |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/security/StaffReadEndpointSecurityTest.java | Filter chain/method security for new routes |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/controller/AdminAccountControllerTest.java | Input/output field contract |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/services/AdminAccountServiceImplTest.java | Filters, detail eligibility, DTO privacy |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/controller/CustomerProfileControllerTest.java | Own identity and safe profile |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/beans/PaymentCategoryTest.java | Enum/boundary semantics |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/controller/AuditEvidenceControllerTest.java | Auditor routes and request validation |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/services/AuditEvidenceServiceImplTest.java | Evidence and privacy rules |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/repository/ReportingReadRepositoryOracleIntegrationTest.java | Real Oracle view/aggregate mapping |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/controller/AdminOperationsControllerTest.java | Stats/failure contracts only |
| C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/test/java/com/ofss/services/AdminOperationsServiceImplTest.java | Operational query and mapping behavior |

Extend existing related tests rather than duplicating them, especially:

- SecurityAuthorizationContractTest.
- AccountDaoIntegrationTest.
- AdminUserSecurityControllerTest / AdminUserSecurityServiceImplTest.
- UserDaoIntegrationTest / UserRoleDaoIntegrationTest / UserServiceImplTest.
- TransactionControllerTest / TransactionQueryServiceTest / TransactionDaoIntegrationTest.
- CreateTransactionRequestTest / TransactionEntityTest / TransactionResponseTest.
- TransactionCreationServiceTest and existing idempotency tests.
- RiskReviewControllerTest / RiskReviewServiceImplTest / RiskReviewResponseTest / RiskReviewRepositoryContractTest / RiskReviewServiceOracleIntegrationTest.
- LedgerDaoOracleIntegrationTest / RiskPolicyDaoIntegrationTest.
- Existing financial/authorization/cancellation/state/OTP/settlement regressions when category integration touches their fixtures.

Each existing test name is under the test root in its matching package. Confirm filenames and dependencies before editing; tests must prove behavior, not mirror implementation line by line.

### 17.3 Execution discipline

1. Perform static review and the permitted offline compile/test-compile.
2. Report compilation separately from runtime success.
3. Provide exact focused Maven goals and expected counts after tests are implemented.
4. User executes tests with their approved environment.
5. Inspect the first underlying error, not only repeated Spring context failures.
6. Use controlled transactional fixtures/approved cleanup patterns; do not repurpose or destroy V12 seed data.
7. Run the appropriately scoped regression group; final clean full-suite run closes the change request.
8. Count tests from the final selected test definitions/current run. Do not invent a number or sum stale Surefire reports from mixed executions.
9. Do not automatically run tests because compile permission exists.

Existing test configuration disables real mail connection checks and settlement processing; Maven already disables relevant scheduled production work and OTP delivery. Preserve these protections. Do not expose credentials in commands/logs or ask the user to paste secrets.

No new code/tests/database commands are executed as part of creating this guide.

## 18. Critical compatibility decisions and approval checklist

| Topic | Current disposition / required gate |
|---|---|
| Legacy category/purpose | Settled: null category, preserve purpose, no backfill |
| Threshold | Settled: strictly above INR 100000.00 |
| Others note | Settled planning rule: reuse purpose, 1–140 trimmed characters |
| Account list/detail privacy | Settled: eight list fields including accountId; full number only individual staff detail |
| Auditor reservation figures | Settled: reservations/difference permitted, current/available balance omitted |
| New health feature | Removed |
| New category database objects | One enum and one nullable payment column; migration execution still requires approval |
| Strict new high-value request requirement | Explicitly approved, including rejection of old category-free high-value POST retries before replay lookup |
| Existing legacy payment lifecycle | Must continue without requiring retroactive category |
| Date/search/stat/failure semantics | Concrete recommendations in this guide; included in phase approval, not silent defaults |
| Actual live migration/grant state | Verify through approved inspection before database-dependent execution |
| Backend call-site/DTO changes | Scoped additions; confirm constructor/replay compatibility in tests |
| Frontend selector/queue controls | Integration requirements only until actual frontend work is approved |
| Additional reporting index | Not preapproved; explain necessity based on evidence if encountered |
| Source-file remarks outside selected scope | Do not implement merely because present in an older note |

A requirement of literal zero defects cannot be established by planning alone. Acceptance is based on the explicit contracts, compilation, negative-security tests, Oracle query/migration verification and user-run runtime gates.

## 19. Completion and continuation checklist

### Before starting

- [x] User approves Phase 0 and scoped implementation of Phases 1–4 as one batch; later phases remain gated.
- [x] Refresh the relevant source for the Phase 1–4 batch.
- [x] Keep this batch's edits confined to its production files, tests, and guide checkpoint.
- [x] Record phase-specific test expectations after test creation; Phases 1–4 runtime results confirmed, Phases 5A–6 runtime results pending.

### Before category deployment

- [x] Explicit V13 migration approval, user execution and successful startup confirmed.
- [ ] Existing rows retain null category and untouched purpose.
- [ ] New lower-value records cannot accept category.
- [ ] Existing high-value records remain processable.
- [x] High-value POST/replay compatibility choice acknowledged: strict rejection explicitly approved.
- [x] Flyway target 13 and mapping deployment coordinated by the user.
- [ ] No financial/ledger/state mutation introduced.

### Before final closure

- [ ] Every selected route has exact-role protection and bounded reads.
- [ ] No full account number in list/balance staff responses.
- [ ] No current/available balances in auditor reservation reconciliation.
- [ ] No new SYSTEM_ADMIN health functionality.
- [ ] Removed features have not re-entered work or pending lists.
- [ ] User-run focused and full-suite results recorded accurately.
- [ ] OpenAPI and final understanding/API-testing guides completed at the final stage.
- [ ] Implementation status distinguishes proposed, compiled, and runtime-verified work.

**Continuation instruction:** start with the next explicitly approved phase. Reading this guide does not authorize code, SQL, environment changes, application startup, tests, or later phases.

### Phase 0–4 implementation checkpoint — 19 September 2026

| Phase | Current state | Remaining gate |
|---|---|---|
| 0 | User approval recorded; no critical unresolved choice for Phases 1–4 | Phase 5 choices remain separate |
| 1 | Account directory/detail/balance reads implemented and runtime-verified | Closed |
| 2 | User directory/detail reads implemented and runtime-verified | Closed |
| 3 | Own-profile read implemented and runtime-verified | Closed |
| 4 | Transaction-history filters implemented and runtime-verified | Closed |
| 5A | V13 deployed by user; startup and constraints verified | Closed |
| 5B, 5C, 6 | User-run 224 affected and 1054 full-suite results passed | Closed |
| 7A, 7B, 7C, 8 | Implemented and offline test-compiled under latest batch approval | User-run verification |
| 9 | Final documentation not included in this implementation batch | Separate approval after verification |

Implementation details now fixed within the approved recommendations:

**Compilation evidence:** offline Maven `-o -Dmaven.repo.local=... -DskipTests test-compile` finished with BUILD SUCCESS at 18:19:12 IST on 19 September 2026. Production compilation had succeeded for 262 files; the final test compilation succeeded for 152 files. No Maven tests, database operations, Flyway execution, or application startup were run by Codex. The only compiler message was the existing annotation-processing advisory; it was not a compilation failure.

- User search trims `q`, accepts at most 120 characters, searches name/email/mobile with literal substring matching, and escapes `!`, `%`, `_` using the SQL escape character `!`. Blank search behaves as an absent filter.
- Account queries join only the to-one owner; user filtering uses `EXISTS` for roles and bulk-fetches assignments for the selected page. Count predicates match their data queries.
- All new service reads use read-only transactions. Administrator directory/detail reads retain active SYSTEM_ADMIN checks and do not acquire the existing mutation locks.
- Transaction filters normalize supplied offsets to UTC, use `[from,to)`, and retain the existing unfiltered service method and DAO query. A foreign source-account filter yields an empty customer-owned result.
- No schema, migration, entity, balance mutation, payment creation, risk/approval, scheduler, dependency, or application-property change is part of this batch.

#### Actual affected-file manifest for this batch

Paths below are relative to `backend/src/main/java/com/ofss/` unless noted.

| Change | Files |
|---|---|
| 8 new production files | `security/StaffReadAccess.java`; `controller/AdminAccountController.java`; `services/AdminAccountService.java`; `services/AdminAccountServiceImpl.java`; `dto/admin/AdminAccountSummaryResponse.java`; `dto/admin/AdminAccountDetailResponse.java`; `controller/CustomerProfileController.java`; `dto/user/CustomerProfileResponse.java` |
| 13 existing production files extended | `security/SecurityConfig.java`; `repository/AccountDao.java`; `controller/AdminUserSecurityController.java`; `services/AdminUserSecurityService.java`; `services/AdminUserSecurityServiceImpl.java`; `repository/UserDao.java`; `repository/UserRoleDao.java`; `services/UserService.java`; `services/UserServiceImpl.java`; `controller/TransactionController.java`; `services/TransactionService.java`; `services/TransactionServiceImpl.java`; `repository/TransactionDao.java` |
| 5 new test files, under `backend/src/test/java/com/ofss/` | `security/StaffReadAccessTest.java`; `security/StaffReadEndpointSecurityTest.java`; `controller/AdminAccountControllerTest.java`; `services/AdminAccountServiceImplTest.java`; `controller/CustomerProfileControllerTest.java` |
| 9 existing test files extended | `security/SecurityAuthorizationContractTest.java`; `services/AdminUserSecurityServiceImplTest.java`; `controller/AdminUserSecurityControllerTest.java`; `controller/TransactionControllerTest.java`; `services/TransactionQueryServiceTest.java`; `repository/AccountDaoIntegrationTest.java`; `repository/UserDaoIntegrationTest.java`; `repository/UserRoleDaoIntegrationTest.java`; `repository/TransactionDaoIntegrationTest.java` |
| Documentation | This guide: approval, implementation checkpoint, test handoff |

#### User-run verification sequence

Use the same Eclipse Maven Build configuration that produced the confirmed 918-test result, with base directory `SafePay/backend`. Refresh the project after source changes. Change only the Goals field for the runs below. Preserve the working test environment and credentials locally; no new environment variable is required by Phases 1–4. Keep Flyway locations and target 12 unchanged, real email disabled, mail connection checks disabled, and settlement processing disabled. The existing Maven configuration disables protection/notification schedulers and OTP delivery during tests.

The table below retains the verification sequence and counts derived from test definitions. The user has now supplied successful combined 142-test and full-suite 1002-test console results; all show zero failures, errors and skipped tests. Keep those distinct from any future phase's unexecuted tests.

Run each individual class using `-Dtest=ClassName test`, in this order:

| Order | ClassName | Expected tests | Purpose |
|---|---|---:|---|
| 1 | StaffReadAccessTest | 5 | Active/exact live role check |
| 2 | AdminAccountServiceImplTest | 8 | Validation, balances, masking, authorization |
| 3 | AdminAccountControllerTest | 10 | Binding, exact JSON fields, 400/404 |
| 4 | AdminUserSecurityServiceImplTest | 13 | Directory reads plus existing administrative mutation regressions |
| 5 | AdminUserSecurityControllerTest | 6 | Directory/detail plus existing controls |
| 6 | CustomerProfileControllerTest | 2 | Principal identity and safe profile mapping through real service |
| 7 | TransactionQueryServiceTest | 10 | Existing queries plus filters/ranges/UTC |
| 8 | TransactionControllerTest | 20 | Existing endpoints plus filter binding |
| 9 | StaffReadEndpointSecurityTest | 40 | Real security filter chain, six routes, four roles, combined staff, live status/version |
| 10 | SecurityAuthorizationContractTest | 7 | Existing exact-role contracts including new controllers |
| 11 | AccountDaoIntegrationTest | 6 | Oracle query execution and internal-account exclusion |
| 12 | UserDaoIntegrationTest | 3 | Oracle literal search and matching pagination count |
| 13 | UserRoleDaoIntegrationTest | 3 | Multi-role deduplication and bulk role lookup |
| 14 | TransactionDaoIntegrationTest | 9 | Existing fixtures plus date boundaries, ownership, account filters |
| — | Combined selection below | **142** | All affected/new test classes |

The last four classes need the functioning local Oracle environment. Existing test-fixture patterns are retained. `TransactionDaoIntegrationTest` creates and removes its own isolated fixtures through the owner connection; its new account case modifies only that fixture's reserved amount/status to prove current-balance filtering and inactive-account visibility. `AccountDaoIntegrationTest` reads the two existing V12 internal account identities to prove they cannot be retrieved as customer details. No test reseeds or changes V12 rows.

After individual classes pass, use this combined Goals value:

~~~text
-Dtest=StaffReadAccessTest,AdminAccountServiceImplTest,AdminAccountControllerTest,AdminUserSecurityServiceImplTest,AdminUserSecurityControllerTest,CustomerProfileControllerTest,TransactionQueryServiceTest,TransactionControllerTest,StaffReadEndpointSecurityTest,SecurityAuthorizationContractTest,AccountDaoIntegrationTest,UserDaoIntegrationTest,UserRoleDaoIntegrationTest,TransactionDaoIntegrationTest test
~~~

Expected combined result: **142 tests, 0 failures, 0 errors, 0 skipped**.

Then run Goals `test` for the complete suite. This batch adds **84 test invocations** to the user-confirmed 918-test baseline, so the expected full total is **1002**, provided the same suite/profile is selected and no other changes are made. Confirm the actual console result; do not infer success from old Surefire XML totals.

**Verification gate closed:** the user supplied the combined 142-test and full-suite 1002-test passing screenshots. Phases 1–4 are locked. If subsequent tests fail, inspect the first underlying error before advancing. The Phase 5 high-value-request/replay choice has subsequently been approved; its database deployment and runtime-verification gates remain open.

**Phase 5A inspection:** before this batch, project migration locations contained V1–V11 plus showcase V12, with no V13 present. This batch adds `backend/src/main/resources/db/migration/V13__payment_category.sql`; live Flyway history has not been independently inspected. Existing table-wide SELECT/INSERT/UPDATE grants cover PAYMENT_TRANSACTION, so this change needs no new grant. Hibernate uses `ddl-auto=validate`: the new mapped category field cannot run against a target-12 database without its column. Preserve both migration locations and coordinate target 13 and reviewed migration execution before application/integration-test startup. No SQL, migration, test, or configuration operation has been executed by Codex for this phase.

### Phase 5A–6 implementation checkpoint — 19 September 2026

**Approval and closure:** the user authorized the batch and explicitly approved strict category validation, including rejection of older category-free high-value POST retries. Subsequent user-run V13 deployment, startup and verification are complete: 224 affected tests and the final 1054-test suite passed. Two older high-value fixtures in OtpChallengeDaoOracleIntegrationTest and TransactionSchedulerDaoIntegrationTest were updated to supply category; no production validation was weakened.

**Compilation:** offline Maven `-o -Dmaven.repo.local=... -DskipTests test-compile` returned **BUILD SUCCESS at 19:14:31 IST**, compiling **267 production files and 154 test files**. This checks Java compilation only, not Oracle DDL/HQL execution, JSON runtime behavior or test assertions. No tests, migration, application startup or dependency downloads were run. The compiler emitted the existing annotation-processing advisory.

#### Implemented behavior

| Area | Behavior |
|---|---|
| V13 | One nullable `PAYMENT_CATEGORY VARCHAR2(20 CHAR)` column; allowed-value, amount-band and OTHERS-purpose checks. No default, UPDATE, backfill or V1–V12 edit. |
| New payment > INR 100000.00 | Category mandatory; OTHERS requires trimmed purpose of 1–140 characters. Existing optional 280-character purpose limit remains for other categories. |
| New payment <= INR 100000.00 | Non-null category rejected; absent/null accepted. Category omitted from response JSON. |
| Legacy payments | Category stays NULL; purpose remains untouched. High-value JSON has category NULL for frontend “Not specified”; low-value JSON omits category. Existing lifecycle operations do not require retroactive categorization. |
| Idempotency | Strict validation precedes create replay lookup. Supplied category participates in the canonical fingerprint; the category-free lower-value canonical map is unchanged. Existing response constructors and old cached JSON remain supported. |
| Officer queue | Existing GET gains optional category and sort=PRIORITY (default) or OLDEST. Database orders the entire eligible result before pagination: MEDICAL, LOAN, FRIENDS_FAMILY, INVESTMENTS, OTHERS, legacy NULL; requestedAt then approvalId break ties. Count uses matching predicates. |
| Auditor reads | Four GET routes under /api/v1/audit; AUDITOR authority at routing/method/service boundaries with live active-role checks. Read-only, bounded pages; stored evidence reused, no recalculated risk. |
| Financial behavior | No category-based risk classification, OTP bypass, approval privilege change, reservation/settlement algorithm change, balance-edit or delete endpoint. |

Auditor routes are `GET /api/v1/audit/risk-reviews`, `GET /api/v1/audit/risk-reviews/{reviewId}`, `GET /api/v1/audit/transactions`, and `GET /api/v1/audit/transactions/{transactionId}`. Review lists retain status filtering and FIFO order. Transaction search supports customerId/state/from/to/page/size with UTC `[from,to)` boundaries. Detail composes customerId, the existing masked transaction response, and typed stored risk evidence. It does not expose account current/available balances or officer mutations.

#### Actual affected-file manifest

Production Java paths below are relative to `backend/src/main/java/com/ofss/`; test paths to `backend/src/test/java/com/ofss/`.

| Change | Files |
|---|---|
| 5 new production files | `beans/PaymentCategory.java`; `controller/AuditEvidenceController.java`; `services/AuditEvidenceService.java`; `services/AuditEvidenceServiceImpl.java`; `dto/audit/AuditTransactionDetailResponse.java` |
| 14 existing production files extended | `beans/TransactionDb.java`; `dto/transaction/CreateTransactionRequest.java`; `dto/transaction/TransactionResponse.java`; `dto/transaction/TransactionSummaryResponse.java`; `dto/riskreview/RiskReviewSummaryResponse.java`; `controller/TransactionController.java`; `controller/RiskReviewController.java`; `services/TransactionServiceImpl.java`; `services/RiskReviewService.java`; `services/RiskReviewServiceImpl.java`; `repository/TransactionDao.java`; `repository/RiskReviewDao.java`; `repository/TransactionRiskFactorDao.java`; `security/SecurityConfig.java` |
| One new migration, not executed | `backend/src/main/resources/db/migration/V13__payment_category.sql` |
| 2 new tests | `services/AuditEvidenceServiceImplTest.java`; `controller/AuditEvidenceControllerTest.java` |
| 15 existing tests extended/adapted | `dto/transaction/CreateTransactionRequestTest.java`; `beans/TransactionEntityTest.java`; `dto/transaction/TransactionResponseTest.java`; `dto/riskreview/RiskReviewResponseTest.java`; `services/TransactionCreationServiceTest.java`; `controller/TransactionControllerTest.java`; `controller/RiskReviewControllerTest.java`; `services/RiskReviewServiceImplTest.java`; `services/OtpServiceImplTest.java`; `security/StaffReadEndpointSecurityTest.java`; `repository/TransactionDaoIntegrationTest.java`; `services/TransactionServiceOracleIntegrationTest.java`; `services/OtpServiceOracleIntegrationTest.java`; `services/RiskReviewServiceOracleIntegrationTest.java`; `services/IdempotencyServiceOracleIntegrationTest.java` |
| Documentation only | This guide and `SafePay_Backend_DB_Context.md` |

Tests cover amount boundaries, unknown/missing/inappropriate categories, OTHERS reason rules, conditional JSON and cached-response round trips, fingerprint conflicts, SQL constraints, legacy NULL lifecycle, global queue order over page boundaries, filtered counts, stored evidence consistency and exact-role denial. Existing high-value creation fixtures now supply category, except intentional legacy-hydration cases. Oracle modifications target each test's own fixtures with the established rollback/cleanup patterns, never V12 seed rows.

#### User-controlled V13 deployment gate

1. Review the exact new V13 file. Do not start the updated application or Oracle tests against the unchanged V12 schema.
2. In the existing OWNER connection, inspect Flyway history and confirm V13 has not already been applied by another checkout; confirm PAYMENT_CATEGORY is not already present. If history or structure differs, stop and reconcile rather than renaming a migration or using repair/clean.
3. Record the pre-migration payment count and existing purpose/state values, and the account balance snapshot for comparison. No seed rewrite or financial update is part of this deployment.
4. After explicit deployment approval, the user coordinates the existing Flyway path: retain `classpath:db/migration,classpath:db/showcase`, advance the relevant run/test `SPRING_FLYWAY_TARGET` from `12` to `13`, and use the existing OWNER migration / APP runtime credentials. Do not execute V13 manually and then ask Flyway to apply it again. Do not use Flyway clean/repair or edit old checksums.
5. Verify successful V13 history, nullable column and all three new constraints; existing rows must have NULL category with original purpose/state/balances intact. Only then run the Oracle test group and full suite. Stop on any migration/validation error and inspect its first cause.

These are user-run steps, not operations already performed. No new environment variable, package, grant, table, sequence or index is introduced. Target coordination is required because the entity mapping changed. Preserve the working test settings that disable real email/mail connection checks and settlement processing; existing Maven scheduler overrides remain unchanged.

#### User-run Maven verification handoff

Use the existing Eclipse Maven Build configuration with base directory `SafePay/backend`. Refresh source files. The first twelve classes are isolated tests and do not apply V13; the last five require Oracle and the completed deployment gate above. Run each row's Goals value individually; expect BUILD SUCCESS, the indicated count, and zero failures/errors/skips. Counts are derived from the current test definitions, **not observed test passes**.

| Order | Goals | Expected tests |
|---|---|---:|
| 1 | `-Dtest=CreateTransactionRequestTest test` | 8 |
| 2 | `-Dtest=TransactionEntityTest test` | 18 |
| 3 | `-Dtest=TransactionResponseTest test` | 7 |
| 4 | `-Dtest=RiskReviewResponseTest test` | 3 |
| 5 | `-Dtest=TransactionCreationServiceTest test` | 6 |
| 6 | `-Dtest=TransactionControllerTest test` | 22 |
| 7 | `-Dtest=RiskReviewControllerTest test` | 14 |
| 8 | `-Dtest=RiskReviewServiceImplTest test` | 17 |
| 9 | `-Dtest=OtpServiceImplTest test` | 17 |
| 10 | `-Dtest=AuditEvidenceServiceImplTest test` | 8 |
| 11 | `-Dtest=AuditEvidenceControllerTest test` | 3 |
| 12 | `-Dtest=StaffReadEndpointSecurityTest test` | 66 |
| 13 | `-Dtest=TransactionDaoIntegrationTest test` | 11 |
| 14 | `-Dtest=TransactionServiceOracleIntegrationTest test` | 6 |
| 15 | `-Dtest=OtpServiceOracleIntegrationTest test` | 5 |
| 16 | `-Dtest=RiskReviewServiceOracleIntegrationTest test` | 7 |
| 17 | `-Dtest=IdempotencyServiceOracleIntegrationTest test` | 6 |

Combined isolated group: **189 tests**:

~~~text
-Dtest=CreateTransactionRequestTest,TransactionEntityTest,TransactionResponseTest,RiskReviewResponseTest,TransactionCreationServiceTest,TransactionControllerTest,RiskReviewControllerTest,RiskReviewServiceImplTest,OtpServiceImplTest,AuditEvidenceServiceImplTest,AuditEvidenceControllerTest,StaffReadEndpointSecurityTest test
~~~

Combined Oracle group, only after the V13 gate: **35 tests**:

~~~text
-Dtest=TransactionDaoIntegrationTest,TransactionServiceOracleIntegrationTest,OtpServiceOracleIntegrationTest,RiskReviewServiceOracleIntegrationTest,IdempotencyServiceOracleIntegrationTest test
~~~

Combined complete affected group: **224 tests**:

~~~text
-Dtest=CreateTransactionRequestTest,TransactionEntityTest,TransactionResponseTest,RiskReviewResponseTest,TransactionCreationServiceTest,TransactionControllerTest,RiskReviewControllerTest,RiskReviewServiceImplTest,OtpServiceImplTest,AuditEvidenceServiceImplTest,AuditEvidenceControllerTest,StaffReadEndpointSecurityTest,TransactionDaoIntegrationTest,TransactionServiceOracleIntegrationTest,OtpServiceOracleIntegrationTest,RiskReviewServiceOracleIntegrationTest,IdempotencyServiceOracleIntegrationTest test
~~~

Finally Goals `test`: **expected 1054 tests**, assuming the same suite/profile and no unrelated changes. This batch adds 52 invocations to the confirmed 1002 baseline. Require a fresh clean console result; selective-run Surefire report aggregates are not proof of a successful full run.

**Superseding verification:** user logs confirm V13 applied successfully at 19:39:44 IST and application startup at 19:39:53 IST. The affected 224-test group passed at 19:45:49 IST; after correcting two older fixture helpers, the full 1054-test suite passed at 19:57:51 IST, with zero failures/errors/skips. Phases 0–6 are locked. The historical deployment instructions above are retained for reference, not a request to rerun V13.

### Phase 7A–8 implementation checkpoint — 19 September 2026

The user explicitly authorized this batch following the 1054-test success. Source implementation is complete; user-run runtime verification remains pending. No new unresolved scope decision was identified. Existing source grants cover the reporting views/tables; live access is checked by the user's Oracle integration tests.

#### Implemented contract

| Phase | Delivered | Important behavior |
|---|---|---|
| 7A | Ledger posting list/detail | Optional transaction/status/creation range; newest-first, ID tie-breaker; opening postings retain NULL transaction; detail entries ordered by line number; masked account identifiers; no replay identity exposure |
| 7B | Ledger and reservation reconciliation | Existing views only; explicit projections, parameterized filters, database paging/counts; allowed statuses remain distinct per endpoint; reservation response excludes current/available balances |
| 7C | Exception list/detail and policy list/detail | Exceptions use lastOccurredAt with descending ID tie-breaker; safe display text replaces raw technical messages/resolution notes; policies read stored historical data and open-ended bands |
| 8 | SYSTEM_ADMIN stats and failures | One Oracle SELECT for counters with database observedAt; combined transaction-exception and failed/retrying notification feed; source plus record ID identifies rows |

All ten new routes are GET-only. Auditor reads extend the existing AuditEvidence controller/service; operations use AdminOperations controller/service. Both controller method security and routing use exact authorities; services require live active AUDITOR or SYSTEM_ADMIN through StaffReadAccess. The combined staff persona continues to mean existing role membership, not a new role.

| Route | Authority |
|---|---|
| /api/v1/audit/ledger-postings | AUDITOR |
| /api/v1/audit/ledger-postings/{postingId} | AUDITOR |
| /api/v1/audit/reconciliation/ledger | AUDITOR |
| /api/v1/audit/reconciliation/reservations | AUDITOR |
| /api/v1/audit/exceptions | AUDITOR |
| /api/v1/audit/exceptions/{exceptionId} | AUDITOR |
| /api/v1/audit/risk-policies | AUDITOR |
| /api/v1/audit/risk-policies/{policyVersion} | AUDITOR |
| /api/v1/admin/operations/stats | SYSTEM_ADMIN |
| /api/v1/admin/operations/failures | SYSTEM_ADMIN |

Concrete implementation details:

- Date ranges are UTC-normalized, inclusive from/exclusive to. Page >= 0; size 1–100. Optional IDs must be positive; required IDs must be present and positive.
- Reconciliation uses postingId/accountId ascending for deterministic paging. Policy lists use effectiveFrom DESC, riskPolicyId DESC; policyVersion validation follows the existing VARCHAR2(50 CHAR) column.
- JDBC uses explicit Oracle NUMBER-to-BigDecimal/long and TIMESTAMP WITH TIME ZONE-to-OffsetDateTime mapping. IDs remain strings in API responses.
- Failure source is TRANSACTION or NOTIFICATION; omission includes both. Transaction exception evidence includes historical/resolved records as well as current exceptions. Notifications include only RETRY_PENDING/FAILED.
- Failure occurrence is exception lastOccurredAt or notification failedAt when present, otherwise updatedAt. Ordering is event time DESC, source ASC, record ID DESC. Count and page SQL use identical filters; timestamps and all filter values are bound.
- Statistics include all payment states (zero counts included), PENDING reviews, OPEN/RETRY_PENDING/MANUAL_REVIEW exceptions, and PENDING/RETRY_PENDING/FAILED notifications. Expired protected counts match the existing MEDIUM/HIGH deadline predicate. Settlement due counts match the worker's latest SETTLEMENT exception by maximum exception ID and persisted retry deadline.
- These counters describe current state/workload, not all-time event totals, scheduler health, or full ledger-to-account-balance reconstruction.
- No entity, migration, view, grant, worker algorithm, mutation API, dependency or application configuration was changed. Flyway target remains 13 with both existing locations. Health and vector search remain excluded.

#### Actual affected files

Production paths are relative to backend/src/main/java/com/ofss/; tests to backend/src/test/java/com/ofss/.

| Change | Files |
|---|---|
| 10 new production files | dto/audit/AuditLedgerPostingResponse.java; dto/audit/AuditReconciliationResponse.java; dto/audit/AuditExceptionResponse.java; dto/audit/AuditRiskPolicyResponse.java; repository/ReportingReadRepository.java; controller/AdminOperationsController.java; services/AdminOperationsService.java; services/AdminOperationsServiceImpl.java; dto/admin/OperationalDashboardResponse.java; dto/admin/OperationalFailureResponse.java |
| 7 existing production files extended | controller/AuditEvidenceController.java; services/AuditEvidenceService.java; services/AuditEvidenceServiceImpl.java; repository/LedgerPostingDao.java; repository/TransactionExceptionDao.java; repository/RiskPolicyDao.java; security/SecurityConfig.java |
| 5 new test files | dto/audit/AuditReportingResponseTest.java; repository/ReportingReadRepositoryTest.java; repository/ReportingReadRepositoryOracleIntegrationTest.java; services/AdminOperationsServiceImplTest.java; controller/AdminOperationsControllerTest.java |
| 5 existing test files extended | services/AuditEvidenceServiceImplTest.java; controller/AuditEvidenceControllerTest.java; security/StaffReadEndpointSecurityTest.java; services/SettlementServiceOracleIntegrationTest.java; repository/TransactionSchedulerDaoIntegrationTest.java |
| Context | This guide; SafePay_Backend_DB_Context.md |

The reporting Oracle test reads existing showcase records only. New settlement reporting scenarios reuse that class's isolated OWNER fixtures and transaction rollback/cleanup. They cover absent/past/future/manual-review settlement exceptions, latest-exception selection, unrelated processing stages, notification retries/terminal failures, source pagination and exclusive upper time bounds. No test rewrites showcase records.

#### Compilation and user-run verification

Offline Maven test-compilation passed at 20:37:58 IST (277 production / 159 test source files). A final offline check after the last scoped test edits returned BUILD SUCCESS at 20:39:35 IST; Maven reported classes already up to date. No tests were executed by Codex.

Use the working V13 Eclipse Maven Build configuration, Java 21, and backend base directory. Retain both migration locations, target 13, disabled background workers and disabled real mail. Stop the separately running application while executing the suite. No migration or new environment variable is needed.

Run each Goals value individually first:

| Order | Maven Goals | Expected tests |
|---|---|---:|
| 1 | `-Dtest=AuditReportingResponseTest test` | 5 |
| 2 | `-Dtest=ReportingReadRepositoryTest test` | 4 |
| 3 | `-Dtest=AuditEvidenceServiceImplTest test` | 13 |
| 4 | `-Dtest=AuditEvidenceControllerTest test` | 5 |
| 5 | `-Dtest=AdminOperationsServiceImplTest test` | 4 |
| 6 | `-Dtest=AdminOperationsControllerTest test` | 3 |
| 7 | `-Dtest=StaffReadEndpointSecurityTest test` | 126 |
| 8 | `-Dtest=ReportingReadRepositoryOracleIntegrationTest test` | 8 |
| 9 | `-Dtest=SettlementServiceOracleIntegrationTest test` | 7 |
| 10 | `-Dtest=TransactionSchedulerDaoIntegrationTest test` | 3 |

The first seven classes total 160 isolated invocations; the final three total 18 Oracle invocations. Security has 20 routes x (one unauthenticated + five role cases), plus three live-user cases and three direct/mutation cases = 126.

Combined affected Goals:

~~~text
-Dtest=AuditReportingResponseTest,ReportingReadRepositoryTest,AuditEvidenceServiceImplTest,AuditEvidenceControllerTest,AdminOperationsServiceImplTest,AdminOperationsControllerTest,StaffReadEndpointSecurityTest,ReportingReadRepositoryOracleIntegrationTest,SettlementServiceOracleIntegrationTest,TransactionSchedulerDaoIntegrationTest test
~~~

Expected **178 tests, zero failures/errors/skips, BUILD SUCCESS**. After this passes, Goals `test` should run **1147 tests** under the same suite/profile (1054 confirmed baseline + 93 new invocations). These are definition-derived expectations, not confirmed runtime results. Inspect a fresh console result, not aggregated stale Surefire files.

**Verification gate closed — 19 September 2026:** the user supplied successful 31-test regression, 178-test affected and 1148-test full-suite results. The final scoped fixes were ReportingReadRepository statistics (EXISTS predicates preserving latest-exception eligibility), GlobalExceptionHandler safe 404 handling, and one new GlobalExceptionHandlerTest regression case. The earlier 1147 expectation is superseded by the actual 1148 result. All change-request implementation phases through 8 are locked.

**Continuation (superseded by the approved delivery checkpoint below):** backend implementation is frozen. Use V1–V13/current source/1148 evidence; stop after each documentation phase. Possible code flaws require an evidence-based chat report and explicit approval before any corrective changes. OpenAPI remains pending before frontend integration. Broader teammate comparison/frontend/presentation goals are acknowledged but excluded from the present documentation increment.

### Phase 9 documentation increment 1 — approved delivery order

The user approved the master foundation, coverage index and complete Phase 2.1 chapter, paired immediately with its separate Phase 2.1 API-test guide. Test-class explanations are brief functionality summaries, with extra detail only for confusing cases; production-class coverage remains comprehensive. Each subsequent phase must likewise deliver its master chapter and API companion together, followed by a review stop.

Created `Documentation for SafePay/SafePay_Master_Backend_Understanding_Guide.md` (foundation, 277-file production coverage index, 11 fully explained Phase 2.1 home classes and configuration) and `Documentation for SafePay/API Test Guides/SafePay_Phase_2_1_API_Test_Guide.md` (existing later endpoints exercising infrastructure; manual execution pending). No backend code/configuration/migration/test changes, Maven execution or database operations were performed. Phase 2.2 is the next documentation increment, awaiting user approval; Phase 9 as a whole is not complete.
