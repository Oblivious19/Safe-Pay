# SafePay Backend Phase 2.6 — Transaction and State Engine

## Purpose and scope

Phase 2.6 implements SafePay V1's canonical payment instruction, centralized
transaction state engine, deterministic authorization routing, customer
cancellation, ownership-safe queries, risk explanations, and REST boundary.

SafePay remains a simulated pre-settlement control layer. A transaction in
`RELEASED` has passed SafePay protection but has not yet been posted to an
external payment rail. Phase 2.6 does not claim to reverse a completed UPI,
IMPS, NEFT, or RTGS settlement.

## Phase contents

| Subphase | Responsibility | Result |
|---|---|---|
| 2.6A | Canonical entities and enums | Complete transaction, risk-evidence, lifecycle, reservation and version mappings |
| 2.6B | Persistence layer | Ownership-scoped reads, stable paging, locked mutation lookup and append-only evidence insertion |
| 2.6C | API DTO contracts | Validated inputs and masked customer-safe outputs |
| 2.6D | State engine | One approved transition matrix, terminal rules, timestamps and `409` conflicts |
| 2.6E | Transaction creation | Creates only an owned, unassessed and unreserved `CREATED` instruction |
| 2.6F | Authorization orchestration | Revalidation, risk assessment, reservation, evidence and tier routing in one transaction |
| 2.6G | Customer cancellation | Deadline enforcement and atomic reservation release before `CANCELLED` |
| 2.6H | Query and explanation workflows | Bounded owned history, detail retrieval and evidence-verified explanations |
| 2.6I | REST and Oracle verification | Six transaction endpoints plus controller and rollback-isolated Oracle tests |

## Main production components

### Domain and persistence

- `TransactionState` defines the ten canonical states and identifies terminal,
  reservation-bearing and customer-cancellable states.
- `TransactionDb` maps `SAFEPAY_OWNER.PAYMENT_TRANSACTION`, including immutable
  instruction identity, ownership, amount, state, risk snapshot, reservation,
  lifecycle timestamps and `VERSION_NO` optimistic locking.
- `TransactionRiskFactor` maps the append-only
  `TRANSACTION_RISK_FACTOR` evidence table. Its V1 factory accepts the assessed
  transaction and matched band, then derives amount, tier, explanation and
  timestamp rather than accepting independent duplicate values.
- `TransactionDao` provides insertion, stable-reference lookup, owned detail,
  newest-first paging and owned `PESSIMISTIC_WRITE` retrieval.
- `TransactionRiskFactorDao` provides insertion and ownership-scoped evidence
  queries. Neither transaction repository exposes deletion.

### Services

- `TransactionStateServiceImpl` owns the full approved transition matrix.
  Controllers and orchestration code do not recreate transition rules.
- `TransactionServiceImpl` coordinates creation, authorization, risk routing,
  cancellation, history, detail and explanation retrieval.
- `InvalidStateTransitionException` is translated into a safe HTTP `409`
  response with error code `STATE_TRANSITION_CONFLICT`.

### REST and DTO boundary

- `CreateTransactionRequest` accepts only source account, beneficiary, amount,
  purpose and customer reference.
- `AuthorizeTransactionRequest` requires explicit affirmative confirmation.
- `TransactionResponse` and `TransactionSummaryResponse` return string IDs and
  masked financial identifiers.
- `TransactionRiskExplanationResponse` exposes the customer-safe policy
  version, tier, timing and explanation, but no internal policy IDs or matched
  band code.
- `TransactionController` obtains the customer ID only from
  `SafePayPrincipal`; a public request cannot choose another user's ID.

## Canonical state model

```text
CREATED
AUTHORIZED
RISK_ASSESSED
PROTECTED
VERIFICATION_REQUIRED
PENDING_RISK_REVIEW
RELEASED
SETTLED
CANCELLED
FAILED
```

Approved transitions:

| Current state | Allowed next state(s) |
|---|---|
| `CREATED` | `AUTHORIZED`, `CANCELLED` |
| `AUTHORIZED` | `RISK_ASSESSED`, `FAILED` |
| `RISK_ASSESSED` | `RELEASED`, `PROTECTED`, `VERIFICATION_REQUIRED` |
| `PROTECTED` | `CANCELLED`, `RELEASED` |
| `VERIFICATION_REQUIRED` | `PENDING_RISK_REVIEW`, `CANCELLED` |
| `PENDING_RISK_REVIEW` | `RELEASED`, `CANCELLED`, `VERIFICATION_REQUIRED` |
| `RELEASED` | `SETTLED`, `FAILED` |
| `SETTLED`, `CANCELLED`, `FAILED` | None; terminal states never reopen |

The entity additionally checks the Oracle-compatible shape of every target
state. A reservation-bearing state cannot be entered without a complete risk
snapshot and intact reservation. A terminal state cannot retain reserved funds.
Returning from risk review to `VERIFICATION_REQUIRED` clears the previous
verification-completed marker so an old OTP result cannot be reused.

## Transaction creation flow

1. Resolve the user from the server-derived customer ID and require `ACTIVE`.
2. Resolve an active owned source account.
3. Resolve an active owned beneficiary.
4. Revalidate amount precision and minimum through `MoneyUtility`.
5. Generate a non-client-controlled `SP-` transaction reference.
6. Insert a `CREATED` instruction with INR currency and zero reservation.
7. Flush the insert so Oracle constraints are checked before returning
   `201 Created`.

Creation deliberately performs no risk assessment, balance reservation,
release, settlement, OTP or Risk Officer action.

## Authorization and routing flow

1. Require explicit `confirmed=true`.
2. Lock the owned transaction row and require the `CREATED` transition.
3. Revalidate the active customer, source-account ownership and activity, and
   beneficiary ownership and activity.
4. Move to `AUTHORIZED` and run the Phase 2.5 amount-risk engine.
5. Resolve the exact immutable policy, band and protection references from the
   evaluation result.
6. Persist the complete coherent snapshot with V1 `riskScore = null`.
7. Reserve the amount on the locked source account.
8. Move through `RISK_ASSESSED` and route by the evaluated policy action.
9. Flush the transaction snapshot before inserting append-only amount evidence,
   satisfying the Oracle composite snapshot foreign key.

Tier routing:

```text
LOW         -> RELEASED
MEDIUM      -> PROTECTED for 10 seconds
HIGH        -> PROTECTED for 60 seconds
VERY_HIGH   -> VERIFICATION_REQUIRED with no timer
```

The timer starts from the same server-authoritative UTC microsecond timestamp
used for the risk assessment and reservation. The browser does not calculate or
own the deadline.

If the selected risk configuration is unavailable, authorization records
`FAILED` with `RISK_EVALUATION_FAILED`. If funds cannot be reserved, the
transaction records the complete risk snapshot and evidence, remains
unreserved, and becomes `FAILED` with the account-domain reason code.

## Cancellation flow

- Customer cancellation is accepted only from `CREATED`, `PROTECTED`,
  `VERIFICATION_REQUIRED`, or `PENDING_RISK_REVIEW`.
- A `PROTECTED` payment must be cancelled strictly before `protectedUntil`.
- Deadline eligibility is checked before any balance mutation and again at the
  centralized transition boundary.
- Reservation-bearing cancellation locks the source account, releases the exact
  transaction amount, ends the transaction reservation and then moves to
  `CANCELLED` in one database transaction.
- Transaction and account locks are always acquired in that order, matching the
  future scheduler's required ordering and reducing deadlock risk.
- Cancellation after `RELEASED`, or from any terminal state, returns a conflict.

## Query and explanation flow

### History

The history endpoint uses an ownership-qualified repository query ordered by
`createdAt DESC, transactionId DESC`. Page numbers are zero-based and page sizes
are restricted to `1..100`; callers cannot request an unbounded result or
replace the canonical sort.

### Detail

The detail query includes the authenticated customer ID. A missing transaction
and another customer's transaction both return `TRANSACTION_NOT_FOUND`, avoiding
ownership disclosure.

### Risk explanation

The explanation endpoint first loads the owned transaction and then retrieves
its owned `PAYMENT_AMOUNT` evidence. Before returning data it verifies:

- transaction identity,
- factor code,
- risk-policy-band identity,
- resulting tier,
- explanation text, and
- evaluation timestamp.

An unassessed instruction returns `RISK_EXPLANATION_NOT_FOUND`. An assessed
transaction with missing or divergent persisted evidence fails closed as an
internal consistency error rather than presenting invented or conflicting risk
information.

## REST API

| Method and path | Purpose | Normal response |
|---|---|---|
| `POST /api/v1/transactions` | Create owned instruction | `201 Created` with `Location` |
| `POST /api/v1/transactions/{transactionId}/authorize` | Confirm, assess, reserve and route | `200 OK` |
| `GET /api/v1/transactions?page=0&size=20` | Retrieve bounded owned history | `200 OK` |
| `GET /api/v1/transactions/{transactionId}` | Retrieve owned detail | `200 OK` |
| `GET /api/v1/transactions/{transactionId}/risk-explanation` | Retrieve evidence-verified explanation | `200 OK` |
| `POST /api/v1/transactions/{transactionId}/cancel` | Cancel while still eligible | `200 OK` |

Invalid DTO input produces safe `400` responses, illegal state changes produce
`409`, missing or foreign-owned resources produce `404`, and domain eligibility
failures use the existing safe business-rule response contract.

## Oracle alignment and concurrency controls

- Table: `SAFEPAY_OWNER.PAYMENT_TRANSACTION`.
- Sequence: `SAFEPAY_OWNER.SEQ_PAYMENT_TRANSACTION_ID`.
- Evidence table: `SAFEPAY_OWNER.TRANSACTION_RISK_FACTOR`.
- Evidence sequence: `SAFEPAY_OWNER.SEQ_TX_RISK_FACTOR_ID`.
- Amount and reservation use Oracle `NUMBER(18,2)` semantics.
- `VERSION_NO` detects stale updates.
- Mutation queries use owner-scoped transaction locks.
- Authorization and cancellation use transaction-row then account-row lock
  ordering.
- V10 all-or-none risk-snapshot, risk-route, reservation-state and deadline
  constraints are mirrored in the entity methods.
- Risk-factor evidence is inserted only after the transaction snapshot flush.
- The database immutability trigger remains unchanged; application repositories
  provide no risk-evidence update or delete path.
- No migration, runtime grant, dependency or application configuration was
  changed in this phase.

## Verification coverage

Phase 2.6 tests cover:

- complete JPA mappings and entity construction,
- every allowed and forbidden state transition,
- terminal reason and lifecycle timestamps,
- creation eligibility and no-side-effect guarantees,
- all four risk routes and exact timer boundaries,
- insufficient-funds and risk-configuration failure behavior,
- reservation release and expired-window cancellation,
- bounded owned queries and masked responses,
- persisted risk-evidence coherence,
- all six controller mappings and validation failures,
- cross-customer isolation, and
- real Spring/JPA/Oracle creation, authorization, routing, evidence and
  cancellation behavior.

The Oracle service integration test is transactionally rolled back. This is
intentional: risk-factor evidence is immutable even to normal owner DML, so the
test must roll back its insert rather than attempt an invalid cleanup delete.
Owner credentials are used only to create and remove isolated user, account and
beneficiary fixtures; production service operations run through `SAFEPAY_APP`.

The confirmed baseline before 2.6H-I is **300 tests passing**. The final Phase
2.6 total is expected to be **325 tests** and remains pending until the user runs
and confirms the focused and complete Maven gates.

## Explicitly deferred work

- **Phase 2.7:** durable idempotency records, request fingerprints and replay.
- **Phase 2.8:** bounded expiry scheduler, restart recovery and final
  cancel-versus-release race verification across application instances.
- **Phase 2.9:** balanced double-entry ledger settlement, `SETTLED` processing
  and durable transaction-exception records.
- **Phase 2.10:** OTP creation, hashing, expiry, resend and attempt controls.
- **Phase 2.11:** Risk Officer approval, rejection and re-verification workflow.
- **Phase 2.12:** audit logs, notifications and WebSocket status events.
- **Deferred security phase:** complete JWT validation, RBAC enforcement and
  stable Spring Security `401`/`403` handlers.

Until the deferred security phase is complete, the controller is fail-closed
and structurally ready for `SafePayPrincipal`, but Phase 2.6 must not be
described as production-authenticated or production-authorized.

## Operational notes for teammates

- Do not collapse creation and authorization into one endpoint.
- Do not accept customer ID, state, tier, policy ID, deadline or reservation
  values from request JSON.
- Do not bypass `TransactionStateService` for state changes.
- Do not calculate the protection timer in the frontend.
- Do not serialize JPA entities directly.
- Do not update or delete risk-factor evidence.
- Do not treat `RELEASED` as external-bank settlement or allow cancellation
  after it.
- If an Oracle integration run is forcibly terminated, inspect for owner fixture
  rows carrying the test's unique token before rerunning cleanup manually.
