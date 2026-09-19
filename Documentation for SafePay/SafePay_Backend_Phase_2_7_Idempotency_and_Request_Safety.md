# SafePay Backend Phase 2.7 — Idempotency and Request Safety

## Purpose and scope

Phase 2.7 prevents duplicate SafePay business effects caused by double-clicks,
network retries, client timeouts, reconnects, and concurrent delivery of the
same mutation. It provides durable actor-and-operation-scoped request identity,
deterministic request fingerprints, exact response replay, Oracle-backed
concurrency arbitration, and HTTP enforcement.

The current integration protects transaction creation, authorization, and
customer cancellation. The canonical operation model also provides explicit
extension points for later scheduler release, settlement, OTP, and Risk Officer
operations without implementing those later workflows prematurely.

SafePay remains a simulated pre-settlement control layer. Idempotency prevents
duplicate effects inside SafePay; it does not claim control over posted external
UPI, IMPS, NEFT, or RTGS payments.

## Phase contents

| Subphase | Responsibility | Result |
|---|---|---|
| 2.7A | Canonical model | `IdempotencyRecord`, status lifecycle and operation scopes mapped to V4 |
| 2.7B | Persistence | Exact-scope lookup, pessimistic locking, forced insert flush and no delete API |
| 2.7C | Request fingerprints | Deterministic SHA-256 over operation, concrete target and canonical logical request |
| 2.7D | Replay and conflict contracts | Typed execution results plus safe different-request, in-progress and expired-key conflicts |
| 2.7E | Transactional coordinator | Claim, business effect, response serialization and completion in one transaction |
| 2.7F | Transaction integration | Creation, authorization and cancellation protected without changing domain services |
| 2.7G | HTTP enforcement | Required `Idempotency-Key` and replay-only `Idempotency-Replayed` response header |
| 2.7H | Unit and boundary verification | Lifecycle, hashing, replay, conflict, rollback and controller behavior tests |
| 2.7I | Oracle verification and handoff | Durable replay, unique-scope races, atomic rollback, 12-hour expiry and this guide |

## Main production components

### Domain and persistence

- `IdempotencyStatus` defines the exact database values `IN_PROGRESS`,
  `COMPLETED`, and `FAILED`.
- `IdempotencyOperation` provides stable uppercase action scopes. Current HTTP
  operations use `TRANSACTION_CREATE`, `TRANSACTION_AUTHORIZE`, and
  `TRANSACTION_CANCEL`. Later approved phases can use the predefined release,
  settlement, OTP, and Risk Officer scopes.
- `IdempotencyRecord` maps `SAFEPAY_OWNER.IDEMPOTENCY_RECORD` and
  `SEQ_IDEMPOTENCY_RECORD_ID`. It owns request identity, optional transaction
  linkage, lifecycle status, HTTP status, response CLOB, correlation ID,
  timestamps, expiry, and optimistic `VERSION_NO`.
- `IdempotencyRecordDao` exposes insert/update and exact-scope reads. Its locked
  lookup uses `PESSIMISTIC_WRITE`. It intentionally exposes no delete method.

The database uniqueness scope remains exactly:

```text
(user_id, operation_code, idempotency_key)
```

Therefore the same opaque key may be used safely by another authenticated user
or for another action, while reuse inside the same actor/action scope resolves
to replay or conflict.

### Fingerprinting

`RequestFingerprintService` calculates a lowercase 64-character SHA-256 value
over:

1. canonical operation code,
2. concrete request target, including resource identity, and
3. logical request content.

Object properties are sorted recursively, so harmless JSON whitespace or
property-order changes do not change the fingerprint. Array order is preserved
because array position may change request meaning. No raw request or
idempotency key is logged by the component.

Transaction creation fingerprints use an explicit logical map. The amount is
validated and normalized to the canonical two-decimal representation before
hashing, so `5000`, `5000.0`, and `5000.00` represent the same payment amount.
Optional purpose and customer-reference text has already been normalized by the
validated request DTO.

Authorization and cancellation use concrete targets such as:

```text
/api/v1/transactions/101/authorize
/api/v1/transactions/101/cancel
```

This prevents the same key and payload from being treated as the same logical
request when aimed at different transactions.

## Twelve-hour retention policy

The approved configuration is:

```properties
safepay.idempotency.retention=PT12H
```

The value is injected into the coordinator rather than scattered through
business code. It can later be changed through approved configuration without
rewriting the algorithm.

For a record created at `2026-09-16T10:00:00Z`, `expires_at` is
`2026-09-16T22:00:00Z`. Before that instant, an identical retry receives the
stored result. At or after that instant, the old key produces safe HTTP `409`
with `IDEMPOTENCY_KEY_EXPIRED`; the request is never silently executed again.
The client must submit a new key.

Phase 2.7 does not add automatic deletion. `SAFEPAY_APP` intentionally has no
`DELETE` privilege on `IDEMPOTENCY_RECORD`. A future purge or archival policy
must be separately approved and owner-controlled. Until then, expiry controls
replay eligibility while the durable record continues to block ambiguous key
reuse.

## Transactional execution flow

`IdempotencyServiceImpl` establishes a new authoritative transaction for each
idempotent mutation. Existing `TransactionServiceImpl` mutation methods join
that transaction through their normal Spring `REQUIRED` behavior.

For a new request:

1. Validate user ID, operation, key, SHA-256 fingerprint, correlation ID,
   response type, callback, and positive retention configuration.
2. Lock and inspect any existing exact-scope record.
3. If no record exists, create `IN_PROGRESS` with `expires_at = created_at +
   12 hours`.
4. Flush the insert before the business action. Oracle's unique constraint is
   now the final concurrency arbiter.
5. Execute the transaction creation, authorization, or cancellation callback.
6. Link the durable record to the resulting owned transaction.
7. Serialize the exact response body into the CLOB.
8. Store HTTP status, terminal status and completion timestamp.
9. Flush and commit the idempotency record and business mutation together.

If the callback or response serialization throws, the whole transaction rolls
back. Neither a business effect nor a stranded idempotency claim survives. A
safe retry may execute again because the prior attempt did not commit.

An explicit action result with status `400..599` is stored as `FAILED` and can
be replayed. An exception that aborts the transaction is different: it is not
converted into a durable result because doing so could commit a partial or
rollback-only financial unit of work.

## Concurrent duplicate behavior

Two requests can both observe that no committed record exists. They then race
to insert the same unique scope:

```text
Request A -> insert scope -> execute effect -> complete -> commit
Request B -> insert same scope -> waits on Oracle unique key
Request B -> unique conflict after A commits -> original transaction rolls back
Request B -> fresh transaction loads A's record -> returns replay
```

The unique-conflict recovery lookup deliberately runs in a fresh transaction.
Continuing inside the failed transaction would be unsafe because an Oracle/JPA
constraint failure leaves that transaction rollback-only.

If the original integrity failure did not come from a competing idempotency
record, the recovery lookup finds no winner and rethrows the original database
failure. Business constraints are therefore not mislabeled as duplicate
requests.

## Existing-record decisions

| Existing condition | Result |
|---|---|
| Same actor/action/key and same hash, completed before expiry | Return stored status/body/transaction identity with replay marker |
| Same actor/action/key but different hash | `409 IDEMPOTENCY_KEY_REUSED` |
| Same hash but record is still `IN_PROGRESS` | `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` |
| Same hash at or after `expires_at` | `409 IDEMPOTENCY_KEY_EXPIRED`; require a new key |
| No matching scope | Atomically claim and execute |

Hash comparison uses constant-time byte comparison. Although a request hash is
not a credential, this avoids introducing content-dependent comparison behavior
into a security-sensitive request control.

## REST integration

| Method and path | Operation scope | Normal result |
|---|---|---|
| `POST /api/v1/transactions` | `TRANSACTION_CREATE` | `201 Created`, original `Location` retained on replay |
| `POST /api/v1/transactions/{transactionId}/authorize` | `TRANSACTION_AUTHORIZE` | `200 OK` |
| `POST /api/v1/transactions/{transactionId}/cancel` | `TRANSACTION_CANCEL` | `200 OK` |

All three require:

```text
Idempotency-Key: <opaque nonblank value, maximum 128 characters>
```

Outer whitespace is normalized, letter case is preserved, and ISO control
characters are rejected. Exactly one header value is required; missing or
duplicated header values fail before the business action. The key is not an
authentication credential and is never accepted as an actor identity.

A genuine replay additionally returns:

```text
Idempotency-Replayed: true
```

The header is absent on a newly executed request. Read-only transaction list,
detail, and risk-explanation endpoints do not require idempotency keys.

The customer ID continues to come exclusively from `SafePayPrincipal`. The
correlation ID comes from `CorrelationIdFilter` and is stored with the first
request for traceability; a retry cannot change the response or business
identity of the original operation.

## Response and error contracts

`IdempotencyExecutionResult<T>` carries:

- original HTTP status,
- typed response body,
- optional transaction ID, and
- whether the response is replayed.

`IdempotencyConflictException` never includes the actual key, request body, or
hash. `GlobalExceptionHandler` translates it to RFC 7807-compatible safe
responses:

| Error code | Meaning |
|---|---|
| `IDEMPOTENCY_KEY_REUSED` | Same scoped key was used for different logical content |
| `IDEMPOTENCY_REQUEST_IN_PROGRESS` | A visible equivalent claim has not reached a terminal result |
| `IDEMPOTENCY_KEY_EXPIRED` | Replay window ended; caller must generate a new key |

All use HTTP `409`, matching the Decision Register.

## Oracle alignment

- Table: `SAFEPAY_OWNER.IDEMPOTENCY_RECORD`.
- Sequence: `SAFEPAY_OWNER.SEQ_IDEMPOTENCY_RECORD_ID` with allocation size 1.
- Unique constraint: `UK_IDEMPOTENCY_REQUEST`.
- Request hash: 64 hexadecimal characters.
- Key: `VARCHAR2(128 CHAR)`.
- Operation: uppercase `VARCHAR2(50 CHAR)`.
- Response: nullable Oracle `CLOB`.
- Correlation ID: nonblank `VARCHAR2(64 CHAR)`.
- Lifecycle timestamps: UTC `TIMESTAMP(6) WITH TIME ZONE` values.
- Version: JPA `@Version` over `VERSION_NO`.
- Runtime permissions: `SELECT`, `INSERT`, and `UPDATE` only, plus sequence
  `SELECT`.
- Indexes on `(status, expires_at)` and `transaction_id` remain unchanged.

No Flyway migration, grant, dependency, external package, or system setting was
added or modified for Phase 2.7.

## Verification coverage

Phase 2.7 tests cover:

- exact entity, enum, sequence, relationship, CLOB and version mappings,
- lifecycle validation and terminal immutability,
- actor/operation/key scope isolation,
- database uniqueness and pessimistic locking,
- recursive fingerprint canonicalization,
- concrete-resource and operation separation,
- response identity and replay flags,
- safe conflict responses,
- 12-hour expiry calculation,
- missing, blank, oversized and control-character keys,
- duplicated idempotency-key headers,
- one-effect replay without callback execution,
- unrelated integrity-failure preservation,
- atomic rollback of both claim and created transaction,
- all three transaction mutation HTTP boundaries,
- exact replay `Location` and response header behavior,
- real Oracle sequential retry,
- real Oracle same-key/different-payload conflict, and
- real concurrent duplicate creation with one committed payment effect.

The confirmed baseline before Phase 2.7E-I is **357 passing tests**. Phase
2.7E-I adds 23 tests, producing an expected complete-suite total of **380**.
Phase 2.7 remains verification-pending until the focused and complete Maven
gates are manually run and confirmed.

The Oracle integration test uses owner credentials only for isolated fixture
creation, inspection, controlled expiry simulation, and cleanup. Application
operations run through `SAFEPAY_APP`. Tests create only `CREATED` transactions,
so no immutable risk-evidence rows are committed during this phase's cleanup.

## Explicitly deferred work

- Owner-controlled archival or purge of expired idempotency records.
- Phase 2.8 scheduled protection expiry and release races.
- Phase 2.9 ledger settlement and settlement-operation integration.
- Phase 2.10 OTP endpoints and their predefined idempotency scopes.
- Phase 2.11 Risk Officer endpoints and their predefined idempotency scopes.
- Phase 2.12 durable audit, notifications and WebSocket events.
- Final JWT validation and RBAC enforcement in the user-deferred security phase.

The predefined future operation names are extension seams only. Their business
services, endpoints, permissions, and state transitions are not implemented by
Phase 2.7.

## Operational notes for teammates

- Generate a fresh idempotency key for every new logical mutation.
- Retry an uncertain delivery with the same key and exactly the same logical
  content.
- Never reuse a key for a changed amount, transaction, action, or payload.
- After `IDEMPOTENCY_KEY_EXPIRED`, generate a new key; do not repeatedly submit
  the expired value.
- Do not remove the pre-action insert flush. It activates Oracle's unique-key
  arbitration before the financial callback.
- Do not catch a unique failure and query again inside the rollback-only
  transaction.
- Do not move idempotency outside the transaction containing the business
  effect.
- Do not add application delete privileges to solve retention without a
  separately approved archival and compliance design.
- Do not treat the replay header as authorization; ownership still comes from
  the authenticated principal.
- Do not require idempotency keys on read-only GET endpoints.
