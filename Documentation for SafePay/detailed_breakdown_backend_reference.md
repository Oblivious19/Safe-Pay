**My recommendation is to retain your backend as the common financial and security foundation, and selectively adapt useful capabilities from the team’s version.** Combining the two database schemas or copying their controllers into yours would introduce conflicting rules.

The guide work is paused. I inspected the repositories read-only; **no files, database objects, configuration, or tests were changed or executed.** This comparison concerns the backend and database only.

For clarity:

- **A — Your version:** [SafePay](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay>)
- **B — Team’s version:** [SafePay-ruchi-frontend](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend>)

The decisions below are **recommendations for the eventual common backend**, not an instruction to delete or change anything in either repository.

| Decision | Meaning here |
|---|---|
| **KEEP A** | Retain your implementation and contract |
| **ADAPT B** | Consider a useful team capability or approach, implemented against A’s contracts |
| **MERGE** | Combine compatible capabilities while keeping one authoritative implementation |
| **REPLACE B → A** | In the eventual common application, use A’s implementation instead of B’s equivalent |
| **REJECT B** | Do not import that behavior into the common backend; leave the team repository untouched |

**1. What the inspection establishes**

Your team’s backend is more capable than “simple CRUD without edge cases.” It already contains:

- Database account locking.
- Reservation-aware spending checks.
- Persistent idempotency keys.
- Conditional state/version updates.
- Ownership checks.
- BCrypt authentication and login lockout.
- Session invalidation when account status or role changes.
- CSRF protection for authenticated writes.
- Separate transactions for scheduled payment settlement.
- Administrative approval **and decline**.
- Idempotent simulated credits.
- Reporting views and a data-preserving Oracle upgrade script.

These deserve credit and should not be discarded merely because the implementation is smaller.

Conversely, your extra structures address substantial differences: financial evidence, separate staff responsibilities, OTP/review rounds, durable retries, immutable audit records, ownership constraints, reconciliation, and reproducible schema deployment.

| Inspection fact | A | B |
|---|---|---|
| Main backend Java files | 277 | 110, including six `package-info` files |
| Backend Java test files | 159 | 43 |
| Separate assistant module | No adoption in current scope | 15 main Java files and nine test files under `AI-Integration` |
| Java / Spring Boot declared in Maven | Java 21 / Boot 3.5.16 | Java 17 / Boot 3.4.13 |
| Current mapped business tables | 19 | Six main entity tables |
| Database deployment | Flyway versioned migrations and repeatable grants | Manually selected SQL setup/upgrade scripts |
| Runtime evidence available here | Your confirmed 1,148-test passing baseline | Source and repository documentation; I did not independently run their application |
| End-to-end integration evidence | Manual API walkthroughs remain pending | You report an integrated working prototype |

**File counts are not a quality score.** B is easier to navigate partly because it implements fewer lifecycle and financial responsibilities. A’s automated success also does not establish that every browser interaction or manual API sequence has been validated.

There is one evidence limitation: **source files do not prove the exact current contents of the team’s live Oracle schema or that the packaged JAR matches the checked-out source.** B contains several alternative SQL generations. I distinguish them below rather than inventing a single applied history.

---

**2. Feature-by-feature comparison and proposed decisions**

| Capability | A: current behavior | B: current behavior | Proposed decision |
|---|---|---|---|
| Customer registration | Creates customer identity and `CUSTOMER` assignment | Creates customer and a ₹5,000 savings account | **KEEP A** core; account provisioning is a separate decision |
| Login | Email/mobile through `loginIdentifier`; JWT and rotating refresh sessions | Separate email/phone inputs; server-side HTTP session | **REPLACE B → A** for the common application |
| Staff roles | `SYSTEM_ADMIN`, `RISK_OFFICER`, `AUDITOR`, independently assigned | One `ADMIN` role | **KEEP A** |
| Multiple accounts | Supported by ownership model and account reads | Supported by current account reads and preserved legacy data | **MERGE** capability through A’s existing model |
| Customer profile read | Dedicated own-profile response | Own-profile response | **KEEP A** |
| Customer profile edit | Not currently exposed | Name, email, phone and optional password update | **ADAPT B** non-password capability only if approved |
| Account balances | Current, stored reserved, calculated available | Balance, derived held amount, retained minimum, transfer availability | **KEEP A** |
| Retained minimum | None | ₹5,000 | **REJECT B**; your Decision Register already resolves this |
| Beneficiary ownership | Belongs to customer; usable with their eligible source accounts | Belongs to a specific source account | **KEEP A** |
| Beneficiary destinations | Bank account and UPI | Bank account/IFSC | **KEEP A** |
| Beneficiary deactivation | Status mutation; preserves record | Soft deactivation, including a DELETE route that changes status | **KEEP A** semantics and route |
| Payment creation | Creates instruction; authorization is separate | Initiates, authorizes, assesses and routes in one request | **KEEP A** |
| Risk calculation | Versioned, persisted amount policy | Hard-coded amount bands in live engine | **KEEP A** |
| Protection windows | Medium 10 seconds, High 60 seconds | Same durations, different amount thresholds | **KEEP A** thresholds and lifecycle |
| Very-high payments | OTP → Risk Officer review → release → settlement | `ADMIN` approves hard hold directly into settlement | **REPLACE B → A** |
| Customer cancellation | Allowed in several approved pre-settlement states | Protected-window cancellation | **KEEP A** |
| Approval rejection | Review rejected; payment cancelled with reason | Decline changes payment to cancelled | **KEEP A**, retaining explicit review outcome |
| Payment categories | Conditional above ₹1 lakh; review priority/filtering | Absent | **KEEP A** |
| Idempotency | User + operation + key, fingerprint and stored response | Keys on payment/audit rows; operation-specific handling | **KEEP A** |
| Settlement evidence | Balanced ledger posting plus entries | Source balance debit and audit event | **REPLACE B → A** |
| Scheduled failures | Durable exception records, scheduled retries, manual-review routing | Log error; later polling can retry | **KEEP A** |
| Customer notifications | Durable notifications, read status and STOMP delivery | No equivalent durable notification subsystem in the main backend | **KEEP A** |
| Staff account directory | Paged, filtered, role-specific disclosures | Unpaged account list with full numbers/balances | **KEEP A** |
| Administrative balance overwrite | Prohibited | Implemented | **REJECT B** |
| Simulated interest credit | No approved equivalent | Implemented with idempotent audit receipt | **REJECT B** as a direct import; any future funding workflow needs its own financial contract |
| Account blocking | Explicitly deferred | ACTIVE/BLOCKED update | **Keep deferred** |
| User status and role controls | Status, role assignments and session revocation | Status updates; separate admin provisioning | **KEEP A**; provisioning convenience can be considered separately |
| Auditor functions | Dedicated transaction, review, ledger, reconciliation, exception and policy reads | No separate auditor authority/workflow | **KEEP A** |
| Operational reporting | State/workload/failure counters | Transaction totals and daily aggregates | **KEEP A** operations; **ADAPT B** daily reporting only if approved |
| Separate AI assistant | Outside current approved scope | Isolated local evidence/review service | **Do not import**; no new AI/vector pending item |

This is a selective merge of **product value**, rather than a merge of competing financial engines.

---

**3. The most consequential behavioral differences**

**A. The risk bands are materially different**

| Payment amount | A | B |
|---|---|---|
| ₹0.01–₹0.99 | Rejected: below ₹1 minimum | Positive amounts can be accepted |
| ₹1–₹5,000 | LOW | LOW |
| ₹5,000.01–₹10,000 | MEDIUM, 10-second protection | LOW, immediate settlement path |
| ₹10,000.01–₹25,000 | MEDIUM, 10-second protection | MEDIUM, 10-second protection |
| ₹25,000.01–₹50,000 | HIGH, 60-second protection | MEDIUM, 10-second protection |
| ₹50,000.01–₹1,00,000 | HIGH, 60-second protection | HIGH, 60-second protection |
| Above ₹1,00,000 | VERY_HIGH, OTP and review | VERY_HIGH, administrative hard hold |

For example, **₹8,000 is protected in A but immediately settled by B’s initiation path**. A shared screen cannot safely assume that matching labels mean matching behavior.

Both current main payment engines are **amount-only**. A has stronger policy provenance and enforcement, but it should not be described as already running device, location, velocity or weighted fraud detection.

**Decision: KEEP A.** Your current Decision Register and migration constraints already establish these bands.

**B. “Create payment” is not the same operation**

A’s flow:

```text
Create instruction
    → CREATED

Explicit customer authorization
    → assess risk and reserve funds
    → LOW: RELEASED
    → MEDIUM/HIGH: PROTECTED
    → VERY_HIGH: VERIFICATION_REQUIRED

Eligible release
    → RELEASED

Settlement processor
    → balanced posting + account mutations + SETTLED
```

B’s live flow:

```text
Initiate request
    → validate source/beneficiary/funds
    → assess amount
    → LOW: debit and SETTLED
    → MEDIUM/HIGH: PROTECTED
    → VERY_HIGH: HARD_HOLD
```

B has `CREATED`, `AUTHORIZED` and `RISK_ASSESSED` transitions inside initiation, but that does not make them equivalent to A’s separately exposed creation and authorization workflow.

A’s distinction matters because:

- Creating an instruction is different from confirming execution.
- Eligibility can be checked again at authorization.
- An approved payment can be ready for settlement without already being settled.
- Settlement errors have somewhere explicit to live.
- Cancellation eligibility can be tied to the real lifecycle stage.

**C. Very-high approval is substantially different**

In B:

```text
HARD_HOLD
    → ADMIN approves
    → source debit + SETTLED
```

In A:

```text
VERIFICATION_REQUIRED
    → successful customer OTP
    → PENDING_RISK_REVIEW
    → RISK_OFFICER approves
    → RELEASED
    → settlement processor
    → SETTLED
```

A’s officer can also reject, request reverification and append notes. A separate review record preserves the review round and decision evidence.

**B’s transaction ID cannot simply be substituted for A’s review ID.** They identify different records.

**D. A’s extra ledger represents a different financial model**

B debits the source balance and records the payment/audit outcome.

A additionally records:

- One posting identity.
- A debit entry.
- A matching credit entry.
- A credit to the internal outbound-clearing account.
- Posting finalization checks.
- Reconciliation evidence.

For a ₹10,000 payment, A can answer:

> Which posting explains this debit, where is its balancing credit, and do both entries match the payment?

B’s current schema cannot answer that through a double-entry ledger because it does not contain one.

Neither version should claim that these operations settle money on live interbank rails. A’s clearing account is part of the simulator.

Primary implementation evidence: [A transaction service](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>), [A settlement service](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/SettlementServiceImpl.java>), [B transaction service](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>), [B approval service](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/AdminApprovalService.java>).

---

**4. Database comparison: first separate B’s schema generations**

B does contain migration-like work: **manual ALTER/upgrade scripts**, rather than a Flyway-managed history.

Its 17 SQL files are not one safe sequence to execute from top to bottom.

| B script | Meaning | Common-backend decision |
|---|---|---|
| `00_enhanced_fresh_schema.sql` | Alternative fresh schema matching the enhanced small backend | **REJECT importing DDL**; compare its six-table model |
| `01_sequences.sql` | Sequences for older, larger schema | **REJECT importing** |
| `02_tables.sql` | Older 14-table design | Historical comparison only |
| `03_constraints_indexes.sql` | Constraints/indexes for that older design | Historical comparison only |
| `04_seed_data.sql` | Seed data for older design | **REJECT importing** |
| `05_reporting_views.sql` | Views over older `TRANSACTIONS` / `ACCOUNTS` names | **REJECT importing** |
| `06_phase1_mvp_alignment.sql` | Extends older schema, including risk-signal logs | Historical comparison only |
| `07_simplified_phase1_schema.sql` | Different small-schema starting point | Historical comparison; not A-compatible |
| `08_simplified_phase1_seed_data.sql` | Seeds the simplified schema | **REJECT importing** |
| `08_user_account_foundation.sql` | Adds roles, hashed-password field and account metadata; assumes one account per user | Superseded for the preserved multi-account path |
| `09_registration_password_transition.sql` | Makes legacy `PASSWORD` nullable after hash availability | Historical compatibility operation |
| `10_risk_engine_schema_compatibility.sql` | Changes tier vocabulary and policy checks | **REJECT importing**; thresholds remain incompatible with A |
| `11_transaction_state_machine.sql` | Alternative state constraint including `RELEASED`/`REJECTED` | Not proof that the active Java flow uses that state model |
| `12_admin_reporting_views.sql` | Current totals/daily reporting views over `TRANSACTION_DB` | **ADAPT reporting idea**, not its SQL unchanged |
| `13_transaction_safeguards.sql` | Adds retry keys/timestamps and widens risk reason | Useful safeguards already covered more broadly in A |
| `14_existing_oracle_ui_migration.sql` | Carefully targeted upgrade of a known legacy snapshot; preserves multiple accounts | **ADAPT preflight/preservation discipline**, not the migration |
| `15_admin_credit_request_key.sql` | Adds unique audit request key for credit receipts | No import; A has its own idempotency and ledger model |

Script 14 is particularly careful: it checks an expected legacy snapshot, creates backup copies, preserves existing values, checks relationships and verifies preservation afterward. That is a strength.

However, its expected starting state is specific—three users, six accounts, two beneficiaries, four settled payments and seven audit rows. It is **not a generic migration for your V13 database**.

Sources: [B database directory](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Database>), [B preservation migration](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Database/14_existing_oracle_ui_migration.sql>).

**The active six-table comparison**

For the following field inventory:

- `ID` means A’s `NUMBER(19,0)` identity/reference.
- `M` means `NUMBER(18,2)`.
- `V(n)` means `VARCHAR2` of the shown length.
- A generally declares character semantics explicitly and uses `TIMESTAMP(6) WITH TIME ZONE`.
- B’s fresh schema mostly uses plain `VARCHAR2(n)` and timezone-free `TIMESTAMP`.
- B’s upgraded legacy schema can retain different numeric precision and column widths from its fresh schema. Exact live metadata remains unverified.

**Roles and user identity**

| Component | A | B | Decision and reason |
|---|---|---|---|
| Role catalogue | `APP_ROLE`: `role_id ID`, `role_code V(30)`, `description V(200)`, `created_at` | `ROLES`: `role_id`, `role_name V(30)`, nullable `description V(255)` | **KEEP A** canonical vocabulary |
| Role values | Four exact roles enforced by check | Current seeded/checked application behavior uses `CUSTOMER`, `ADMIN` | **REPLACE B → A** |
| Assignments | `USER_ROLE(user_id, role_id, assigned_at, assigned_by_user_id)` | `USERS.role_id` directly | **KEEP A**; supports multiple independent authorities |
| User identifier | `APP_USER.user_id` | `USERS.user_id` | Same concept, different physical table |
| Name | `full_name V(120)` | `name V(100)` | **KEEP A** |
| Email | `email V(254)`, nullable when mobile exists, normalized lowercase | `email V(150)`, required | **KEEP A**; not a simple rename |
| Mobile | `mobile_number V(16)`, E.164, nullable when email exists | `phone V(10)`, required | **KEEP A**; national and international formats differ |
| Password | `password_hash V(255)` | `password_hash V(255)`; legacy upgraded schema retains nullable `password` | Keep A’s single canonical mapped field |
| Status | `ACTIVE`, `LOCKED`, `DISABLED` | `ACTIVE`, `LOCKED`, `SUSPENDED`, `INACTIVE` | **KEEP A**; do not mechanically map every B status to one A status |
| Failed login count | `failed_login_count NUMBER(5,0)` | `failed_login_attempts`; fresh DDL `NUMBER(3)`, upgrade 14 uses `NUMBER(10,0)` | Keep A’s field and validation |
| Login timestamps | `locked_until`, `last_successful_login_at`, `last_failed_login_at` | `locked_until`, `last_login_at` | Keep A’s fuller evidence |
| Password/security lifecycle | `password_changed_at`, `security_version`, `version_no` | No equivalent mapped version fields | **KEEP A** |
| Record timestamps | `created_at`, `updated_at`, timezone-aware | Same general purpose, local timestamps | **KEEP A** |

A’s `USER_ROLE` is not unnecessary indirection. It allows a person to hold all three staff roles without inventing a fifth `ADMIN` role, and preserves who assigned a role.

**Accounts**

| A column | B counterpart | Difference / decision |
|---|---|---|
| `account_id ID` | `account_id` | Same identity concept; preserve A IDs |
| `owner_user_id ID` | `user_id` | A permits ownerless internal accounts; B requires a user |
| `account_number V(34)` | `account_number V(30)` | Preserve A length and value |
| `account_type V(30)` | `account_type V(20)` | Both savings/current; A additionally has internal account types |
| `bank_name V(120)` | None | Keep A |
| `ifsc_code V(11)` | None on the account | Keep A |
| `currency_code V(3)` | None | Keep explicit INR |
| `current_balance M` | `balance M` | Related meaning, not interchangeable response name |
| `reserved_amount M` | Derived from pending payments | Keep A’s stored reservation plus reconciliation |
| Virtual `available_balance M` | Derived funds response | Keep A: current minus reserved |
| `status V(20)` | `status V(20)` | A ACTIVE/INACTIVE; B ACTIVE/BLOCKED/CLOSED |
| `version_no NUMBER(19,0)` | None | Keep optimistic-version evidence |
| `created_at`, `updated_at` | Same names, timezone-free | Keep A temporal contract |

B’s funds response calculates:

```text
availableToTransfer = max(balance − pending holds − 5000, 0)
```

A calculates:

```text
availableBalance = currentBalance − reservedAmount
```

**These are different quantities.** B’s `availableToTransfer` must not be relabelled `availableBalance`.

There is no bank-branch/location column in either current mapped account model. B’s historical `BANKS` table does not establish branch filtering in its active backend.

**Beneficiaries**

| A column | B counterpart | Decision |
|---|---|---|
| `beneficiary_id ID` | `beneficiary_id` | Keep A identity |
| `owner_user_id ID` | `account_id` | **KEEP A** customer-level ownership |
| `beneficiary_name V(120)` | `beneficiary_name V(100)` | Keep A |
| `nickname V(60)` | None | Keep optional convenience metadata |
| `payment_method V(20)` | Implicit bank-account method | Keep explicit BANK_ACCOUNT/UPI |
| `bank_name V(120)` | None | Keep A |
| `bank_account_number V(34)` | `bank_account_number V(30)` | Keep A |
| `ifsc_code V(11)` | `ifsc V(20)` | Keep A name, normalization and validation |
| `upi_id V(255)` | None | Keep A |
| `relationship_label V(50)` | None | Keep optional metadata |
| `purpose_note V(140)` | None | Keep optional beneficiary-level note |
| `status V(20)` | `status V(10)` | A DISABLED versus B INACTIVE |
| `version_no NUMBER(19,0)` | None | Keep A |
| `created_at`, `updated_at` | Only `created_at` | Keep A |

Example: a customer has savings and current accounts and pays the same beneficiary from either. A stores one customer-owned beneficiary. B’s account-bound model may require separate beneficiary records.

Neither approach is inherently invalid. **A’s approach matches your current ownership contract and avoids introducing a new account-binding rule.**

**Payments**

| Field family | A: `PAYMENT_TRANSACTION` | B: `TRANSACTION_DB` | Decision |
|---|---|---|---|
| Identity | `transaction_id ID`, `transaction_reference V(64)` | `transaction_id`, `transaction_ref V(50)` | Keep A |
| Ownership | `customer_user_id`, `source_account_id`, `beneficiary_id` | `from_account_id`, `beneficiary_id`; customer reached through account | Keep explicit A customer and composite ownership checks |
| Money | `amount M`, `currency_code V(3)` | `amount M` | Keep explicit currency |
| Customer description | `purpose V(280)`, `customer_reference V(100)` | `purpose V(255)` | Keep A |
| Conditional category | `payment_category V(20)`, nullable historically | None | Keep A |
| State/reason | `state V(32)`, `terminal_reason_code V(64)` | `state V(20)` | Keep A |
| Concurrency | `version_no NUMBER(10,0)` | `version`, mapped with `@Version` | Keep A; both have meaningful concurrency control |
| Reservation | `reserved_amount M`, `reserved_at`, `reservation_ended_at` | No per-payment reservation fields; derive from state | Keep A |
| Policy references | `risk_policy_id`, `risk_policy_band_id`, `protection_policy_id` | None | Keep A |
| Risk snapshot | `risk_tier V(20)`, `risk_score NUMBER(5,2)`, `policy_version V(50)`, `matched_band_code V(50)`, `risk_explanation V(1000)` | `risk_tier V(15)`, `risk_reason V(2000)` | Keep A; `risk_score` remains null for amount-only V1 |
| Protection | `protection_seconds`, `protected_until` | `protection_seconds`, `protection_expires_at`, `authentication_required CHAR(1)` | Keep A; VERY_HIGH timing/authentication semantics differ |
| Lifecycle timestamps | `created_at`, `authorized_at`, `risk_assessed_at`, `verification_completed_at`, `released_at`, `settled_at`, `cancelled_at`, `failed_at`, `updated_at` | `created_at`, `authorized_at`, `verified_at`, `released_at`, `settled_at`, `cancelled_at` | Keep A |
| Retry keys | Separate `IDEMPOTENCY_RECORD` | `idempotency_key`, `cancel_idempotency_key`, `verification_idempotency_key`, each V(100) | Keep A scoped model |

The V9 correction from numeric to textual `policy_version` matters: the current A field is **`VARCHAR2(50 CHAR)`**, not the original V4 numeric definition.

A’s category column remains nullable for historical compatibility. Existing V12 purposes remain untouched. It does not change the amount-based risk tier.

**Audit records**

| A: `AUDIT_LOG` | B: `AUDIT_LOG` | Decision |
|---|---|---|
| `audit_log_id`, `event_reference` | `audit_id` | Keep explicit event identity |
| `actor_user_id`, `actor_type`, `actor_role_code` | `user_id` | Keep actor and role context |
| `action_code`, `entity_type`, `entity_id` | `action` | Keep structured target identity |
| `transaction_id` | `transaction_id` | Shared relationship |
| `previous_state`, `new_state` | `old_state`, `new_state` | Similar concept; widths differ |
| `outcome`, `reason_code` | No dedicated equivalents | Keep distinction between success/denial/failure |
| `correlation_id`, `idempotency_key` | Nullable unique `request_key` | Different purposes; retain A |
| `details_json CLOB` | No structured details column | Keep sanitized structured evidence |
| `occurred_at` with timezone | `created_at` without timezone | Keep A |
| Database immutability | No equivalent guard in current fresh DDL | Keep A trigger and restricted grants |

B uses audit `request_key` as a receipt identity for credits and approval actions. That is a valid compact design for those operations, but it is not equivalent to A’s general idempotency record.

**A’s thirteen additional tables: why each exists**

The decision for every table and its fields below is **KEEP A**.

| Table | Column responsibilities | Why it is needed |
|---|---|---|
| `USER_ROLE` | `user_id`, `role_id`, `assigned_at`, `assigned_by_user_id` | Multiple independent roles and assignment provenance |
| `AUTH_SESSION` | `session_id`, `user_id`, `token_family_key V(36)`, `refresh_token_hash V(255)`, `created_at`, `expires_at`, `last_used_at`, `revoked_at`, `revocation_reason V(60)`, `replaced_by_session_id`, `version_no` | Refresh rotation, revocation and reuse-family tracking |
| `RISK_POLICY` | `risk_policy_id`, `policy_version V(50)`, `policy_name V(120)`, `algorithm_type V(30)`, `currency_code V(3)`, `status V(20)`, effective dates, `description V(500)`, `created_at` | Identifies the policy used for a decision |
| `PROTECTION_POLICY` | `protection_policy_id`, `protection_code V(50)`, `release_mode V(30)`, `protection_seconds`, cancellation/auto-release/OTP/review Y/N flags, `description V(300)`, `created_at` | Separates classification from the protection action |
| `RISK_POLICY_BAND` | `risk_policy_band_id`, policy/protection IDs, `band_code V(50)`, `risk_tier V(20)`, minimum/maximum M, `display_order NUMBER(3,0)`, `explanation_template V(500)`, `created_at` | Stores exact boundaries and their explanation |
| `TRANSACTION_RISK_FACTOR` | `transaction_risk_factor_id`, transaction/band IDs, `factor_code V(50)`, `raw_value V(500)`, `resulting_tier V(20)`, `explanation V(1000)`, `evaluated_at` | Append-only evidence of the amount decision |
| `IDEMPOTENCY_RECORD` | Record/user/transaction IDs; `operation_code V(50)`, key V(128), hash V(64), status V(20), HTTP status NUMBER(3), response CLOB, correlation V(64), creation/completion/expiry times, version | Prevents duplicate effects and preserves the original response |
| `PAYMENT_OTP_CHALLENGE` | Challenge/transaction/customer IDs; purpose V(40), channel V(20), hash V(255), status V(20), attempt/max counts NUMBER(3), expiry/verification/invalidation/creation/update times, version | Enforces one controlled verification lifecycle |
| `RISK_REVIEW` | `approval_id`, transaction/customer IDs, `review_round NUMBER(5)`, assigned/deciding officers, status V(30), reason V(1000), requested/claimed/decided/updated times, version | Preserves independent decisions and reverification rounds |
| `LEDGER_POSTING` | Posting/reference/type, optional transaction, source system, idempotency key, amount/currency, expected entry count, status, failure code, lifecycle times, version | Groups one balanced financial operation |
| `LEDGER_ENTRY` | Entry/posting/transaction/account IDs, source/key, line NUMBER(2), entry type V(10), amount/currency/status, description V(500), creation time | Immutable debit and credit evidence |
| `TRANSACTION_EXCEPTION` | Exception/reference/transaction/posting IDs; stage V(30), code V(100), message V(2000), retry flag/status/count/time, occurrence/resolution times, resolver, resolution note V(2000), correlation, version | Distinguishes retryable failure from definitive failure/manual review |
| `APP_NOTIFICATION` | Notification/reference/recipient/transaction IDs; type/severity/title/message/channel/status, deduplication key, attempts/max, retry/delivery/failure/read times, error code, correlation, creation/update/version | Durable customer notifications independent of an open socket |

The table name `RISK_REVIEW` retains the physical primary-key column **`approval_id`**. Renaming that column merely for visual consistency would be unnecessary drift.

---

**5. Constraints, relationships, sequences and database authority**

**Relationships and constraints**

| Integrity rule | A | B | Decision |
|---|---|---|---|
| Identity uniqueness | PKs and stable reference uniqueness | PKs and reference uniqueness | Keep A identities; common concept |
| Contact identity | Normalized email, E.164 mobile, at least one contact | Required email/phone with uniqueness; registration validation is weaker | Keep A |
| Multiple roles | Composite `USER_ROLE` PK | One role FK on user | Keep A |
| Account ownership | Customer account requires owner/IFSC; internal account forbids owner/IFSC | Every account belongs to a user | Keep A |
| Balance invariants | Current/reserved nonnegative; reserved ≤ current; virtual available | Fresh schema retains balance ≥ ₹5,000; pending commitments enforced in services | Keep A |
| Beneficiary uniqueness | Customer + bank destination or customer + UPI | Source account + bank number + IFSC | Keep A |
| Destination shape | BANK_ACCOUNT and UPI fields mutually constrained | Bank destination only | Keep A |
| Payment ownership | Composite FKs bind payment customer to source account and beneficiary | Basic FKs; same-account beneficiary rule checked in service | Keep A’s service **and** DB checks |
| OTP/review ownership | Composite transaction/customer FKs | No equivalent OTP/review tables in active mapping | Keep A |
| Risk-policy consistency | Composite policy-version and band-snapshot relationships | Tier/duration/authentication checks; Java chooses amount band | Keep A |
| Published policy integrity | One active policy; boundary validation; published data guarded | Hard-coded engine | Keep A |
| Reservation lifecycle | Held states require complete reservation and appropriate timestamps | Holds inferred from state | Keep A |
| OTP uniqueness | One pending challenge per transaction | No OTP workflow | Keep A |
| Review uniqueness | One open pending review; unique transaction/round | Pending transactions themselves form queue | Keep A |
| Reviewer separation | Customer cannot act as deciding officer for own review | CUSTOMER/ADMIN separation through current role model | Keep A’s explicit invariant |
| Idempotency scope | Unique user + operation + key | Global uniqueness per payment key column; unique audit receipt key | Keep A |
| Ledger uniqueness | Posting identity plus line/side uniqueness that allows the debit-credit pair | No ledger | Keep A |
| Ledger finalization | Exactly balanced, matching entries on distinct accounts | No equivalent | Keep A |
| Audit/risk evidence mutation | Database guards and restricted grants | Ordinary mutable mapped tables | Keep A |
| Notification lifecycle | Content identity fixed; attempts/read/delivery transitions constrained | No equivalent | Keep A |
| Failure/retry lifecycle | Retry timing, resolution and state checks | No durable exception lifecycle | Keep A |

A particularly useful defense is V10’s composite ownership FK.

A basic FK proves:

> This source account exists.

A composite ownership FK proves:

> This source account exists **and belongs to the customer named on this payment**.

That protects against an application bug inserting a valid account ID with the wrong customer ID. It complements ownership checking in Java.

**Sequences**

A has 18 sequence-backed identities; `USER_ROLE` uses a composite key.

| A sequence group | Names |
|---|---|
| Identity | `SEQ_APP_ROLE_ID`, `SEQ_APP_USER_ID`, `SEQ_AUTH_SESSION_ID` |
| Accounts | `SEQ_ACCOUNT_ID`, `SEQ_BENEFICIARY_ID` |
| Policy | `SEQ_RISK_POLICY_ID`, `SEQ_PROTECTION_POLICY_ID`, `SEQ_RISK_POLICY_BAND_ID` |
| Payment/evidence | `SEQ_PAYMENT_TRANSACTION_ID`, `SEQ_TX_RISK_FACTOR_ID`, `SEQ_IDEMPOTENCY_RECORD_ID` |
| Verification/review | `SEQ_PAYMENT_OTP_CHALLENGE_ID`, `SEQ_RISK_REVIEW_ID` |
| Financial processing | `SEQ_LEDGER_POSTING_ID`, `SEQ_LEDGER_ENTRY_ID`, `SEQ_TRANSACTION_EXCEPTION_ID` |
| Communications/evidence | `SEQ_AUDIT_LOG_ID`, `SEQ_APP_NOTIFICATION_ID` |

B’s enhanced fresh schema uses:

`SEQ_ROLE_ID`, `SEQ_USER_ID`, `SEQ_ACCOUNT_ID`, `SEQ_ACCOUNT_NUMBER`, `SEQ_BENEFICIARY_ID`, `SEQ_TRANSACTION_ID`, `SEQ_AUDIT_ID`.

B’s separate account-number sequence supports automatic account provisioning. A does not need that sequence merely to read existing accounts.

**Decision: preserve A’s sequences and existing values.** Sequence numbers or primary keys from the two repositories must not be treated as identifying the same person/payment.

**Triggers and stored procedure**

A’s current migration chain defines these ten guards:

| Database object | Purpose |
|---|---|
| `TRG_TX_RISK_FACTOR_IMMUTABLE` | Append-only risk evidence |
| `TRG_PAYMENT_OTP_TERMINAL_LOCK` | No deletion or mutation of terminal challenges |
| `TRG_RISK_REVIEW_FINAL_LOCK` | No deletion or mutation of completed reviews |
| `TRG_LEDGER_ENTRY_WRITE_GUARD` | Immutable entries; insertion must match posting |
| `TRG_LEDGER_POSTING_FINALIZE` | Financial identity and balanced finalization |
| `TRG_AUDIT_LOG_IMMUTABLE` | Immutable audit evidence |
| `TRG_APP_NOTIFICATION_WRITE_GUARD` | Protect notification identity/content/lifecycle |
| `TRG_RISK_POLICY_GUARD` | Controlled policy publication/retirement |
| `TRG_RISK_BAND_DRAFT_ONLY` | Prevent changes to published bands |
| `TRG_PROTECTION_POLICY_LOCK` | Protect actions referenced by published policies |

`PR_VALIDATE_RISK_POLICY_BANDS` checks the four-band policy before activation.

B’s inspected active setup scripts do not provide equivalent stored triggers/procedures. The procedures declared **inside migration PL/SQL blocks** are migration helpers, not persisted application stored procedures.

**Views and indexes**

A has five current views:

- `VW_TRANSACTION_DASHBOARD`
- `VW_RISK_SUMMARY`
- `VW_PENDING_APPROVALS`
- `VW_LEDGER_RECONCILIATION`
- `VW_RESERVATION_RECONCILIATION`

B’s current reporting repository uses:

- `VW_SP_TX_REPORT_TOTALS`
- `VW_SP_TX_REPORT_DAILY`

A’s indexes support ownership/history queries, expiry scans, pending reviews, retries, notification dispatch and reconciliation-related lookup. B has useful source-account/state, expiry and settlement-history indexes, but its active list endpoints are largely unpaged.

The daily B view is a **creation-day grouping using current transaction state**. It is not “how many transitions happened on that day.” For example, a payment created Monday and settled Tuesday contributes to Monday’s cohort with its current settled state.

**Database authority**

A separates migration ownership from runtime privileges:

- `SAFEPAY_OWNER`: schema/migration authority.
- `SAFEPAY_APP`: specific runtime grants.
- Runtime cannot generally create/delete accounts or modify immutable ledger/audit evidence.
- Policy tables are read-only to runtime.

B’s checked-in base datasource configuration names `SYSTEM`. Its Oracle profile declares `ddl-auto: validate`, while base properties contain `ddl-auto=update`; the effective runtime configuration depends on profile and overrides.

**Recommendation: KEEP A’s authority separation and Flyway ownership.** Do not import B’s datasource configuration or assume their effective connection privileges from source alone.

Sources: [A integrity hardening](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/db/migration/V10__phase_1_9_integrity_hardening.sql>), [A runtime grants](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/db/migration/R__safepay_app_grants.sql>), [B Oracle profile](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/resources/application-oracle.yml>).

**Historical B objects that must not be mistaken for active features**

The older design additionally defines `BANKS`, `RISK_FACTORS`, `PROTECTION_RULES`, `USER_SAFETY_SETTINGS`, `TRANSACTION_CONTEXT`, `TRANSACTION_RISK_FACTORS`, `APPROVALS`, `DISPUTES`, and later `RISK_SIGNAL_LOGS`, alongside older versions of users/accounts/payments/audit.

Their columns describe bank catalogues, weighted rules, customer thresholds, device/location context, checker decisions and disputes. **Those definitions do not correspond to active implementations in the current six-entity backend.**

I recommend retaining them only as historical design references. They do not justify adding weighted risk, disputes, safety-settings tables or new scope to A.

---

**6. Class-by-class responsibility mapping**

The table below follows the active team classes and maps their responsibilities to your backend. An interface and its implementation are named together where they serve the same contract.

**Identity and authentication**

| B classes/components | A counterpart | Why A differs / decision |
|---|---|---|
| `User` | `User` | A adds domain validation, security/version fields and canonical contact formats |
| `Role` | `Role`, `RoleName`, `UserRole`, `UserRoleId` | A models independent role assignments rather than one user role |
| `UserDao`, `RoleDao` | Same-named DAOs plus `UserRoleDao` | Additional repository exists because assignments are separate records |
| `AuthController` | Registration portion of `AuthController` | A accepts an explicit registration DTO rather than binding a JPA entity |
| `UserService`, `UserServiceImpl` | `UserRegistrationService`, `UserRegistrationServiceImpl`, `UserService`, `UserServiceImpl` | A separates public registration from internal user eligibility operations |
| `LoginController`, `LoginService` | `AuthController`, `AuthServiceImpl`, `CredentialAuthenticationServiceImpl`, `LoginSecurityServiceImpl` | A separates HTTP handling, credential checks, lockout and token/session issuance |
| `LoginRequest` | `dto.auth.LoginRequest` | Different request contract: email/phone versus `loginIdentifier` |
| `LoginPrincipal` | `SafePayPrincipal`, `AuthenticatedUser`, `AuthTokenResponse` | A supports an authority set and stable authenticated user ID |
| `LoginSecurityConfig` | `SecurityConfig`, JWT configuration/converter | Replace B session-login stack with A’s established stack |
| `CustomerResourceSecurityConfig` | `SecurityConfig` and controller/service checks | Keep A exact-role and ownership boundaries |
| `AdminSecurityConfig` | Same security foundation plus `StaffReadAccess` | B’s one ADMIN bucket must be split into A’s three staff authorities |
| `LogoutSecurityConfig` | Auth logout, refresh-session service, cookie/origin/CSRF handling | Preserve A’s revocation semantics |
| `CurrentSessionFilter`, `CurrentSessionService`, `SessionUserRepository` | `SafePayJwtAuthenticationConverter`, `SafePayUserDetailsService`, user/role repositories | Both check current identity; A additionally checks security version and authority equality |
| `UserStatus` | `UserStatus` | Status vocabularies differ |
| `UserController`, `UserProfileResponse` | `CustomerProfileController`, `CustomerProfileResponse` | A’s endpoint explicitly means “me” |
| `ProfileController`, `ProfileService`, `ProfileRepository`, `ProfileUpdateRequest` | No current profile-edit equivalent | Candidate capability, not approved to import |

A’s extra authentication classes are not all independent “features.” Several are small boundaries between:

1. Password verification.
2. Login-failure persistence.
3. Access-token issuance.
4. Refresh-session rotation.
5. Cookie handling.
6. Request authorization.
7. Security-event evidence.

That costs more navigation but makes each responsibility easier to change without silently changing the others.

B’s session approach is not intrinsically insecure or unsuitable for a prototype. Replacing A’s authentication now would nevertheless be a substantial contract change with little benefit.

**Accounts and beneficiaries**

| B classes | A counterpart | Decision |
|---|---|---|
| `Account`, `AccountType`, `AccountStatus` | Same domain names, different fields/enums | Keep A mappings and invariants |
| `AccountDao` | `AccountDao` | Both provide account locking; retain A’s canonical queries |
| `AccountController` | `AccountController`, `AccountServiceImpl`, `AccountSummaryResponse` | B returns entity-shaped account reads; A uses explicit safe responses |
| `AccountFundsController`, nested `FundsView`, `AccountFundsRepository` | `AccountController`, `AccountBalanceResponse`; financial mutation responsibility in `AccountFundsServiceImpl` | Similar display purpose; materially different funds formula |
| `AccountService`, `AccountServiceImpl` | A account service/funds services | B retains broad older create/update/delete methods; do not infer that all are exposed through current controller |
| `Beneficiary`, `BeneficiaryDao` | `Beneficiary`, `BeneficiaryDao` | Keep customer-level ownership and BANK_ACCOUNT/UPI shape |
| `BeneficiaryController`, `BeneficiaryService`, `BeneficiaryServiceImpl` | Same responsibility in A | Keep A route and validation contract |
| `BeneficiaryRequest` | `CreateBeneficiaryRequest` | A includes payment method, bank/UPI alternatives and optional metadata |
| `BeneficiaryResponse` | `BeneficiaryResponse` | Different destination disclosure and ownership fields |
| `BeneficiaryStatusRequest` | `UpdateBeneficiaryStatusRequest` | INACTIVE must not leak into A’s DISABLED vocabulary |

A’s `AccountFundsServiceImpl` exists to control **financial mutations**, not to make account reads unnecessarily complex. Its operations reserve, release or settle funds under locks and validate account types.

**Payment and risk classes**

| B classes | A counterpart | Decision |
|---|---|---|
| `TransactionDb` | `TransactionDb` | A has controlled lifecycle methods and richer financial/policy state |
| `TransactionRequest` | `CreateTransactionRequest`, `AuthorizeTransactionRequest` | A explicitly separates instruction and confirmation |
| `TransactionController` | `TransactionController` | Keep A endpoints and idempotency wrapper |
| `TransactionService`, `TransactionServiceImpl` | Same service family, plus specialized collaborators | Keep A lifecycle |
| `TransactionPreRiskValidator` | Request DTOs, `MoneyUtility`, user/account/beneficiary services and domain guards | B’s extraction is readable; A already distributes validation by owner |
| `TransactionDao` | `TransactionDao` | Both use state/ownership queries; A supports richer lifecycle and paging |
| `RiskAssessmentEngine` | `AmountRiskEngine`, `RiskPolicyServiceImpl` | Keep A persisted policy identity |
| `RuleBasedRiskResult`, `AssessmentRiskTier` | `RiskEvaluationResult`, `RiskTier` | Keep one canonical set |
| `AmountRiskEngine`, `RiskAssessment` | No need for B’s legacy parallel engine | Do not import duplicate risk vocabulary |
| `TransactionRiskInputBuilder`, `RiskAssessmentInput`, `RiskContextSignal` | No active equivalent needed for amount-only V1 | These B types do not make contextual scoring active |
| `TransactionProtectionStateMachine`, `ProtectionState` | `TransactionStateService`, `TransactionStateServiceImpl`, `TransactionState` | B’s alternate state machine is explicitly not wired to its legacy JPA state enum |
| `TransactionState`, `RiskTier` | Same concepts, different values | Keep A exact enums |
| `BooleanToYNConverter` | No equivalent payment-level authentication flag required | Do not add a redundant canonical flag |

This distinction is important: B has **both active and alternative/legacy risk/state structures**. Smaller file count does not mean there is only one conceptual model in its repository.

**Schedulers, approval and verification**

| B classes | A counterpart | Why A’s additional structure matters |
|---|---|---|
| `TransactionScheduler` | `ProtectedTransactionScheduler`, `ReleasedTransactionSettlementScheduler` | A separates release eligibility from settlement |
| `ExpiredTransactionSettlementService` | `ProtectedTransactionReleaseWorkerImpl`, `SettlementServiceImpl` | B combines protected expiry and debit; A preserves the RELEASED boundary |
| `AdminApprovalController`, `AdminApprovalService` | `RiskReviewController`, `RiskReviewServiceImpl` | Dedicated role, review identity/round, reasons and reverification |
| `VerificationRepository` | `RiskReviewDao`, `TransactionDao` | B’s repository performs hard-hold approval/decline updates |
| `AdminApprovalRequestView` | `RiskReviewSummaryResponse`, `RiskReviewDetailResponse` | Keep A disclosure and review contract |
| `VerifiedTransactionResponse` | Transaction/review responses | B response represents a different workflow outcome |
| `VerificationController`, `VerificationService`, `VerificationRequest` | `VerificationController`, OTP service/DTO family | B’s old customer password verification route is denied; it is not equivalent to A OTP |

The A settlement support classes also have distinct reasons to exist:

| A-only class family | Purpose |
|---|---|
| `SettlementPostingFactory`, `SettlementPostingPair` | Construct the matching ledger pair once |
| `SettlementServiceImpl` | Atomically settle one eligible payment |
| `SettlementAttemptOutcome` | Distinguish settled/already-settled/ineligible outcomes |
| `SettlementInvariantException` | Identify violated financial assumptions |
| `SettlementFailureRecorderImpl`, `SettlementFailureDisposition` | Persist a failure after settlement work rolls back |
| `ReleasedTransactionSettlementProcessorImpl` | Coordinate eligible attempts and failure handling |
| Settlement scheduler properties/configuration | Explicit enablement, batch size, delays and clearing-account configuration |
| `SettlementEvidenceServiceImpl` | Append settlement audit/notification evidence |

The separately transactional failure recorder is particularly important: if failure evidence were written only inside the failed settlement transaction, **the rollback could remove the evidence of the failure itself**.

**Administration and reporting**

| B classes | A counterpart | Decision |
|---|---|---|
| `AdminController`, `AdminService`, `AdminDtos` | `AdminUserSecurityController`, `AdminUserSecurityServiceImpl`, user-security DTOs | Keep A user controls; do not import account-blocking scope |
| `AdminAccountController`, `AdminAccountService`, `AdminAccountRepository`, `AdminAccountDtos` | A admin account controller/service/DTOs | Keep A read-only directory; reject direct balance/type update |
| `AdminProvisioningController`, `AdminProvisioningService`, `AdminProvisionRequest` | No equivalent single “create ADMIN” operation | Do not import a fifth role or bypass A role-assignment contract |
| `AdminCreditController`, `AdminCreditService`, `AdminCreditDtos` | No approved equivalent | No direct import |
| `AdminReportController`, `AdminReportService`, `AdminReportRepository`, `AdminReportDtos` | `AdminOperationsController`, `AdminOperationsServiceImpl`, `ReportingReadRepository`, operations DTOs | Different reporting meaning; daily aggregates are a possible addition |
| `AuditLog`, `AuditLogDao` | `AuditLog`, `AuditLogDao` plus audit/evidence services | Keep A immutable and queryable evidence |

B’s small nested DTO containers are a legitimate readability choice. A already uses nested records where useful—for example ledger/reconciliation responses. There is no need to refactor either repository merely to force one DTO-per-file or several DTOs-per-file.

**Exceptions and infrastructure**

| B classes | A counterpart / decision |
|---|---|
| `ResourceNotFoundExcp` | Keep A’s stable error-code contract |
| `DuplicateEmailException`, `DuplicatePhoneException`, `DuplicateBeneficiaryException` | A uses `DuplicateResourceExcp` with specific codes |
| `AccountAlreadyExistsException` | Relevant to B’s older account-creation rules; no import |
| `InsufficientBalanceException` | A `BusinessRuleException` and financial domain checks |
| `InvalidStateTransitionException` | Same responsibility in A, richer lifecycle |
| `InvalidAdminStatusTransitionException` | Keep A administrative validation/error contract |
| `TransactionValidationException` | A DTO validation plus business exceptions |
| `GlobalExceptionHandler` | Keep A ProblemDetail-based responses |
| `WebConfig` | A browser/security configuration; preserve approved origins and headers |
| `LocalDemoConfiguration` | B’s disposable H2 convenience, not an Oracle-equivalence guarantee |
| `SafePayApplication` | Retain A application bootstrap and explicit scheduler configuration |
| Six `package-info` files | Package documentation; no runtime feature to merge |

**The remaining A-specific domains**

A’s OTP, idempotency, audit, notification and security DTO/helper files belong to these concrete responsibilities:

- **OTP:** generation, hashing, challenge lifecycle, resend/attempt policy, email gateway, disabled gateway and safe responses.
- **Idempotency:** request fingerprint, operation scope, persisted receipt and replay result.
- **Audit:** lifecycle events, actor/outcome types, detail sanitization, scoped timelines and auditor evidence.
- **Notifications:** durable content, dispatch state, retries, read status, REST DTOs and STOMP publication.
- **Security:** access-token codec/configuration, refresh-session lifecycle, cookie/origin/CSRF handling, live principal validation and socket authorization.
- **Common API:** masking, money normalization, correlation IDs, field errors and pagination.

The reason to keep them is their responsibility and active consumers—not simply that they already exist.

---

**7. Exact API-contract differences that prevent a blind merge**

**Core request/response fields**

| Meaning | B contract | A contract | Required interpretation |
|---|---|---|---|
| API prefix | `/api/...` | `/api/v1/...` | Not interchangeable |
| Registration name | `name` | `fullName` | Explicit field mapping |
| Registration phone | `phone`, ten digits | `mobileNumber`, E.164 | Requires validated conversion, not just renaming |
| Login identifier | `email` or `phone` | `loginIdentifier` | Different request |
| Login result | Session identity and cookie | Access token, expiry, user ID and authority list; refresh cookie | Different authentication contract |
| Role | Single `role` such as ADMIN | `authorities` list | ADMIN cannot be mapped blindly to SYSTEM_ADMIN |
| Source account | `fromAccountId` | `sourceAccountId` | Exact rename |
| Payment reference | `transactionRef` | `transactionReference` | Exact rename |
| Risk explanation | `riskReason` | `riskExplanation` | Related field, different supporting evidence |
| Protection deadline | `protectionExpiresAt` | `protectedUntil` | Different name/time representation |
| Payment confirmation | Included in initiation behavior | Separate authorize request with `confirmed: true` | Cannot be solved by renaming |
| Beneficiary IFSC | `ifsc` | `ifscCode` | Exact rename |
| Beneficiary binding | Optional `accountId` in creation | Customer identity comes from authentication | Ownership-model difference |
| High-value metadata | No category | Required `category` above ₹1 lakh | New mandatory condition |
| Review operation target | Transaction ID | Review ID | Different resource |
| List result | Usually bare array | Paged wrappers on the applicable history/staff routes | Different response shape |
| Missing timestamps/text | B transaction map often emits `""` | Typed nullable values | Empty string is not a timestamp |
| Error response | Primarily `{ "message": ... }` | ProblemDetail plus stable extension fields | Different error contract |
| Retry response | Often current resource state | Stored original result for idempotent replay | Different interpretation |

A’s paged wrapper is:

```text
items, page, size, totalElements, totalPages, first, last
```

It is not Spring’s raw `content` response, and it is not a bare array.

**Money and identifiers require particular care**

- A account balance/detail DTOs use decimal **strings**.
- A transaction DTOs currently use `BigDecimal` amounts, ordinarily serialized as JSON **numbers**.
- B also mixes formats: account/funds/transaction responses use numeric amounts, while administrative credit and pending-review responses use decimal strings.
- A’s public resource identifiers are commonly strings; B commonly returns numeric `Long` IDs.

Therefore, **neither backend should be described as having universally string-formatted money**. This is an existing contract consistency consideration, not something I changed.

B’s transaction response additionally supplies `protectionRemainingMillis` and `canCancel`, calculated using database time. That is useful consumer guidance. A’s ordinary transaction response does not currently expose those same fields. **ADAPT the idea only after an explicit response-contract decision**; the server must still decide whether cancellation succeeds.

**Endpoint capability mapping**

| B endpoint/capability | A equivalent or status |
|---|---|
| `POST /api/auth/register` | `POST /api/v1/auth/register`; no automatic account |
| `POST /api/auth/login` | `POST /api/v1/auth/login`; different auth contract |
| `POST /api/auth/logout` | `POST /api/v1/auth/logout`; A’s logout requirements apply |
| No token-refresh equivalent | A `/auth/csrf` and `/auth/refresh` |
| `GET /api/users/current` | `GET /api/v1/users/me` |
| `PUT /api/users/current` | No approved current equivalent |
| `GET /api/accounts` | `GET /api/v1/accounts` |
| `GET /api/accounts/current?accountId=...` | Explicit `GET /api/v1/accounts/{accountId}` |
| `GET /api/accounts/{id}/funds` | `GET /api/v1/accounts/{accountId}/balance`; different formula |
| Beneficiary create/list/detail/status | A corresponding versioned routes, different fields/ownership |
| Beneficiary DELETE | A status-change operation; no need to introduce DELETE |
| `POST /api/transactions` | A create **then authorize**, not a one-call replacement |
| Transaction list/detail/cancel | A corresponding routes with richer filters/state rules |
| Customer `/transactions/{id}/verify` | B denies this old route; A has issue/resend/verify OTP routes |
| `GET /api/admin/transactions/hard-holds` | `GET /api/v1/admin/risk-reviews`; different role/resource |
| Transaction approve/decline | Review approve/reject, targeting `reviewId` |
| No equivalent | A request-verification and review-note operations |
| Admin account list | A paged/filtered account directory |
| Admin account PUT balance/type | Prohibited in A |
| Admin account status PATCH | Deferred in A |
| Admin user list/detail/status | A corresponding administrative routes |
| Admin creation | No equivalent single ADMIN provisioning route |
| Interest credits | No approved equivalent |
| Summary/daily reports | A operations statistics are not financially equivalent reports |
| No separate auditor API | A audit logs/evidence/reviews/ledger/reconciliation/exceptions/policies |
| No durable customer notification API | A notifications/read-status and STOMP delivery |

The old broad B `UserController` mappings also require interpretation through its security configuration. The existence of a Java DELETE or list method does **not** prove that customers can invoke it: several of those legacy routes are explicitly denied.

---

**8. Critical edge cases: where each design helps**

| Scenario | A | B | Assessment |
|---|---|---|---|
| Two simultaneous outgoing payments from one account | Locked reservation mutations and balance invariants | Account lock plus pending-total check | Both address overspending; do not claim B has no concurrency control |
| Cancel races with timer settlement | State/locking/deadline checks through separated lifecycle | Conditional version/state/deadline update and account serialization | Both contain substantive protection |
| Same initiation request retried | Scoped fingerprint and stored response | Existing payment/key and payload comparison | A has broader, more uniform semantics |
| Two users independently choose same key | Independent user scopes | Global payment-key uniqueness can conflict | Keep A |
| Same key reused for different operation | Separate operation scope | Different columns/receipt-specific handling | Keep A’s explicit model |
| Settlement fails halfway | Atomic rollback; durable separate failure recording | Per-payment rollback; logging and later poll | A has stronger operational recovery evidence |
| Process restarts while payment is held | Persistent state/reservations and scheduler discovery | Persistent state and scheduler discovery | Both can rediscover work |
| Review needs another OTP | New review cycle and verification lifecycle | No equivalent | A |
| Administrator loses privileges | Live status/role/version checks | Live role/status check invalidates session | Both address live revocation; A has richer invalidation state |
| Account is blocked after protection begins | Settlement checks active source; failure handling exists | Timer settlement worker does not check account status | B’s “block” semantics need explicit clarification |
| Customer has two accounts and one beneficiary | One user-owned beneficiary can serve either | Beneficiary tied to selected account | Different product contract |
| Notification connection drops | Durable notification remains queryable | No equivalent main-backend durable feed | A |
| Audit record is accidentally updated | DB rejects mutation | No equivalent active-schema immutability guard | A |
| Reconstruct old risk decision | Policy IDs/version, snapshot and immutable factor | Stored tier/reason; code threshold history not separately persisted | A |
| Daily business totals required | Current operations endpoint does not provide B’s daily amount report | Dedicated bounded date-range report | B provides an additional useful capability |
| New person registers for demo | Identity exists but no account is automatically provisioned | Account is created, but ₹5,000 minimum leaves zero transferable funds | Neither should be described as “register and immediately send money” without additional setup |

**Findings that deserve attention, without patching**

| Finding and exact source | Evidence and implication | Proposed treatment |
|---|---|---|
| B registration validation gap — [AuthController](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/controller/AuthController.java>) and [UserServiceImpl](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/UserServiceImpl.java>) | Registration binds `User`; service checks password presence and duplicates but does not provide A’s name/contact/password contract. JPA column annotations do not substitute for those input rules | Use A registration; do not import this path |
| B profile password update — [ProfileService](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/ProfileService.java>) | Optional password replacement has no current-password challenge in this method; live session checking does not compare password/security version | Do not import password changes; already deferred |
| B account mutation evidence — [AdminAccountService](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/AdminAccountService.java>) | Balance/type update checks reservations, but lacks a matching ledger and audit write in this service | Reject direct mutation under your existing rule |
| B blocking semantics — [ExpiredTransactionSettlementService](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/ExpiredTransactionSettlementService.java>) | Timer settlement validates funds/state/expiry but not ACTIVE account status | A blocked account can still have already-protected work processed by this path; do not import ambiguous blocking |
| B rejected-report mismatch — [report views](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Database/12_admin_reporting_views.sql>) | Report counts `REJECTED`; active decline writes `CANCELLED`, and active `TransactionState` lacks REJECTED | Do not present that counter as the number of administrative declines |
| B shortened transaction reference — [TransactionServiceImpl](</C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>) | Uses only eight UUID characters after prefix | Smaller uniqueness space; DB unique constraint still protects duplicates, but generation can conflict |
| Mixed monetary response formats — A and B DTOs | Account and transaction responses differ in JSON representation | Record per-endpoint types; any standardization needs scoped approval |
| B SQL-generation ambiguity | Fresh, simplified, old large schema and specific legacy upgrade coexist | Live metadata/active configuration must be identified before any data migration |

These are source-based findings. I have not claimed a runtime reproduction or changed anything to address them.

---

**9. What A’s complexity buys—and where it costs you**

| Extra A structure | Concrete benefit | Cost |
|---|---|---|
| Explicit request/response DTOs | Prevent entity fields becoming accidental API inputs/outputs | More files and mapping |
| Domain factories/mutation methods | Centralize valid construction and state changes | More code than setters |
| User-role join model | Independent duties and combined staff personas | Extra join/query logic |
| Policy/band/protection tables | Explainable, reproducible decisions | More schema and validation |
| Reservation fields | Explicit commitment tracking across multiple workflow states | Must remain synchronized; reconciliation is necessary |
| Posting plus entries | Balanced financial evidence | More transaction steps and constraints |
| Idempotency record | Uniform replay and conflict handling | Stored response/fingerprint lifecycle |
| Separate release/settlement workers | Clear processing boundaries and retry handling | More asynchronous states to understand |
| Failure recorder | Failure evidence survives rollback | Additional transactional component |
| OTP/review entities | Attempts, expiry, rounds and decision accountability | More state transitions |
| Durable notifications | Delivery survives disconnected clients/process cycles | Retry/deduplication machinery |
| Audit/evidence services | Safe, role-scoped inspection | More mapping and disclosure rules |
| Forward migrations and runtime grants | Reproducibility and restricted DB authority | More disciplined setup |

Two cautions apply to A:

1. **An interface with one implementation is not automatically a business necessity.** Its value is separation and substitution, not increased file count.
2. **Passing more tests does not prove every extra abstraction is optimal.** We should preserve the verified backend now, explain it clearly, and avoid a broad “simplification” refactor during integration.

B’s main strengths are:

- Shorter feature paths.
- Straightforward Java records.
- Fewer concepts needed to demonstrate a payment.
- Convenient profile/admin/reporting capabilities.
- A reportedly working integrated prototype.
- Meaningful safeguards despite its smaller schema.

Its main costs are:

- Weaker separation between payment approval and settlement.
- No balanced ledger.
- Less durable failure/review evidence.
- A single broad staff role.
- Several historical and active models coexisting.
- More endpoint-specific behavior instead of one consistent contract.

---

**10. Proposed common backend, with no drift**

I recommend the following decision set:

| Area | Proposed common choice |
|---|---|
| Runtime and dependencies | A’s existing Java/Spring backend |
| Schema | A’s current V13 schema; applied migrations remain immutable |
| Database identities | Preserve A IDs and sequences; no automatic import of B rows |
| Authentication | A JWT/refresh/security-version model |
| Roles | Exactly CUSTOMER, SYSTEM_ADMIN, RISK_OFFICER, AUDITOR |
| Customer ownership | A’s user-owned account/beneficiary rules and DB reinforcement |
| Amount rules | A bands and ₹1 minimum; no ₹5,000 retained minimum |
| Payment lifecycle | A create → authorize → protect/verify/review → release → settle |
| High-value category | Preserve current conditional requirement and historical NULL behavior |
| Money movement | A reservations, internal clearing and balanced ledger |
| Retries/audit/notifications | A existing implementations |
| API vocabulary | A exact names, statuses, IDs and response contracts |
| Team improvements | Only individually approved product capabilities, implemented through A |
| Account blocking/password changes | Remain deferred |
| Balance overwrite/account deletion | Remain prohibited |
| AI/vector integration | No addition and no new pending item |

This does **not** require replacing your backend with a new “third backend.” It gives the team one authoritative backend while preserving worthwhile product ideas from theirs.

**The decisions still worth taking before any implementation**

I would keep these separate from the already-approved financial/security rules:

| Decision | My recommendation |
|---|---|
| **Demo onboarding:** retain seeded/pre-provisioned accounts, or add automatic account provisioning? | Retain the existing prepared accounts for the immediate prototype. Automatic provisioning and funding require a separately approved ledger-consistent workflow |
| **Profile editing:** add name/email/mobile editing? | Consider it as a narrow addition, excluding password changes; decide validation and identity/session effects first |
| **Daily reporting:** retain B’s totals/daily amount capability? | Useful, but first choose the authorized role and define creation-day versus settlement-day meaning |
| **Countdown convenience:** add server-derived `canCancel` / remaining time to A responses? | Potentially useful; assess as a small API addition rather than changing timer authority |
| **Team data preservation:** must actual B users/accounts/payment history be carried into A? | Do not migrate it by default. Preserving real team data would require a separate mapping and live-schema verification decision |

**My recommended immediate approval is to use A as the common backend unchanged, with the five choices above treated individually.** The team’s existing functionality can then be evaluated against that single contract without silently changing roles, balances, risk thresholds, states or parameter names.