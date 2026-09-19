# SafePay Backend Phase 2.8 — Protection Scheduler and Cancellation Races

## Purpose and scope

Phase 2.8 makes expiry of MEDIUM- and HIGH-risk protection windows entirely
server-authoritative. Oracle stores the deadline, the backend periodically
discovers due rows, and a transactional worker moves an eligible transaction
from `PROTECTED` to `RELEASED`.

The phase also closes the final customer Undo-versus-expiry race. Customer
cancellation and automatic release acquire the same transaction-row lock and
re-read authoritative state. A cancellation may succeed only when Oracle time
is strictly earlier than `PROTECTED_UNTIL`; at the deadline or later, release
wins.

`RELEASED` still means that SafePay protection has completed. It is not external
UPI, IMPS, NEFT, or RTGS settlement. Phase 2.8 does not debit the account, end
the reservation, write ledger entries, or claim that an interbank payment was
posted.

## Phase contents

| Subphase | Responsibility | Result |
|---|---|---|
| 2.8A | Scheduler contract and configuration | Validated enablement, one-second polling and a V1 maximum batch size of 50 |
| 2.8B | Expired-candidate persistence boundary | Oracle-time, oldest-deadline-first, bounded discovery of timed `PROTECTED` rows |
| 2.8C | Transactional release worker | Per-row lock, fresh eligibility check, centralized state transition and reservation preservation |
| 2.8D | Scheduler orchestration | A thin scheduled trigger delegates one configured batch per fixed-delay cycle |
| 2.8E | Exact cancellation-expiry boundary | Protected cancellation uses Oracle time after the transaction lock and succeeds only strictly before expiry |
| 2.8F | Multi-instance and duplicate safety | Competing workers serialize on the row; existing `VERSION_NO` and fresh state checks permit one transition only |
| 2.8G | Restart recovery and failure isolation | Every cycle re-queries Oracle; per-row transactions and guarded iteration keep later backlog work progressing |
| 2.8H | Boundary and concurrency verification | Unit and real-Oracle tests cover timing, duplicate workers, races, batching and reservation invariants |
| 2.8I | Oracle handoff and phase guide | Constraint-valid integration fixtures, manual verification gates and this teammate-facing document |

## Approved V1 operational policy

```properties
safepay.protection-scheduler.enabled=true
safepay.protection-scheduler.poll-interval=PT1S
safepay.protection-scheduler.batch-size=50
```

- The scheduler polls once one completed invocation has been followed by the
  configured one-second fixed delay.
- One invocation requests at most 50 candidate transaction IDs.
- A smaller positive batch may be configured operationally.
- Values above 50 are rejected in V1 rather than silently accepted.
- The polling interval and batch size are external configuration, so a later
  approved version can tune them without changing the state algorithm.

Polling does not extend a customer's Undo period. For example, if
`PROTECTED_UNTIL` is `10:00:10.000000Z`, cancellation at
`10:00:10.200000Z` is already ineligible even if the next poll has not yet
changed the row to `RELEASED`.

## Main production components

### Configuration and scheduler

- `ProtectionSchedulerProperties` binds the enabled flag, polling duration and
  batch size. Its constructor rejects null/non-positive duration and batch
  values outside `1..50`.
- `ProtectionSchedulerConfiguration` enables Spring scheduling and registers
  the typed properties.
- `ProtectedTransactionScheduler` exists only when the enabled property is
  true. Its scheduled method contains no state or financial logic; it delegates
  one configured batch to the release service.

### Persistence

`TransactionDao.findExpiredProtectedTransactionIds(...)` uses Oracle
`SYSTIMESTAMP` and returns IDs ordered by:

```text
PROTECTED_UNTIL ASC, TRANSACTION_ID ASC
```

Its eligibility predicates require:

```text
STATE = PROTECTED
RISK_TIER IN (MEDIUM, HIGH)
PROTECTED_UNTIL IS NOT NULL
PROTECTED_UNTIL <= SYSTIMESTAMP
ROWNUM <= configured batch size
```

The query aligns with the existing V4 index
`IDX_PAYMENT_TX_STATE_DUE (STATE, PROTECTED_UNTIL)`. No new migration or index
was required.

`TransactionDao.findByIdForUpdate(...)` provides the internal-system mutation
lock without inventing a customer identity. `currentDatabaseTime()` obtains a
fresh Oracle timestamp inside the same transaction.

### Release orchestration

- `ProtectedTransactionReleaseServiceImpl` discovers one bounded list and
  invokes the worker in deterministic database order.
- `ProtectedTransactionReleaseWorkerImpl` executes each candidate in a
  separate `REQUIRES_NEW` transaction.
- The worker locks the transaction row, checks that the current state is still
  `PROTECTED`, obtains Oracle time, checks the deadline again, and delegates
  `PROTECTED -> RELEASED` to `TransactionStateService`.
- `ProtectedTransactionReleaseOutcome` distinguishes released, not-due,
  no-longer-eligible and missing candidates without treating expected races as
  duplicate financial effects.
- One worker failure is logged and does not prevent later candidate IDs in the
  same discovered batch from being attempted. The failed row remains eligible
  for a later poll after its failed transaction rolls back.

## End-to-end expiry flow

```text
fixed-delay scheduler trigger
        |
        v
query at most 50 due PROTECTED IDs using Oracle SYSTIMESTAMP
        |
        v
for each ID, start REQUIRES_NEW transaction
        |
        v
lock PAYMENT_TRANSACTION row
        |
        v
re-read state and Oracle time
        |
        +-- missing/non-PROTECTED/not-due --> safe no-op
        |
        v
central state engine: PROTECTED -> RELEASED
        |
        v
flush versioned update and commit
```

The candidate query is intentionally advisory. Eligibility is never trusted
solely because an ID appeared in an earlier scan; the worker repeats all
financially relevant checks after it owns the row lock.

## Cancellation-versus-release race

Both paths lock the transaction first.

| First lock holder | Authoritative condition | Result after both requests finish |
|---|---|---|
| Customer cancellation | Oracle time is strictly before the deadline | Cancellation releases the account reservation and commits `CANCELLED`; the waiting worker sees `CANCELLED` and does nothing |
| Customer cancellation | Oracle time is at or after the deadline | Cancellation fails without touching the account; the waiting worker releases the transaction |
| Scheduler worker | Deadline has expired | Worker commits `RELEASED`; the waiting cancellation sees `RELEASED` and returns a state conflict |
| Duplicate scheduler worker | First worker already committed | Waiting worker sees `RELEASED` and becomes a no-op |

Blocking row acquisition is deliberate for V1. `SKIP LOCKED` was not added to
the individual candidate mutation because, at the exact expiry boundary, a
cancellation could temporarily own the row, reject itself as too late, and the
same scheduler attempt could skip the row. Waiting and re-reading produces the
stronger one-winner behavior required by the Decision Register.

Hibernate's managed update continues to include the existing `VERSION_NO`
optimistic predicate. The row lock provides serialization, while the version
column detects any stale update that bypasses the expected lock path.

## Reservation and settlement boundary

Automatic release deliberately preserves:

- `RESERVED_AMOUNT = transaction amount`,
- the original `RESERVED_AT`,
- `RESERVATION_ENDED_AT = null`, and
- the corresponding source-account reserved amount.

This is required by `CK_PAYMENT_TX_RESERVE_STATE`, which treats `RELEASED` as a
reservation-bearing state. Phase 2.9 will atomically consume the reservation,
write balanced ledger lines and transition `RELEASED -> SETTLED`. Phase 2.8
must not perform any of those actions.

Customer cancellation is different: an eligible cancellation locks the source
account after the transaction, releases the exact reserved amount, ends the
transaction reservation and then enters `CANCELLED` in the same transaction.

## Restart and backlog behavior

No Java timer, future, queue item, or browser countdown is the source of truth.
After an application restart, the next scheduler invocation issues the same
Oracle due-row query and recovers every still-`PROTECTED` expired row in later
bounded batches.

If 120 rows are overdue with a batch size of 50, successive cycles can process
up to 50, 50 and 20 candidates. An earlier committed candidate is not rolled
back if a later candidate fails because each worker uses `REQUIRES_NEW`.

Multiple application instances may discover the same candidate. This can add
lock waiting but cannot duplicate the transition. The first commit wins and
the later worker observes the authoritative new state.

## Oracle and schema alignment

- Table: `SAFEPAY_OWNER.PAYMENT_TRANSACTION`.
- Primary key sequence: `SEQ_PAYMENT_TRANSACTION_ID`.
- Deadline: `PROTECTED_UNTIL TIMESTAMP(6) WITH TIME ZONE`.
- Concurrency version: `VERSION_NO` through JPA `@Version`.
- Due-row index: `IDX_PAYMENT_TX_STATE_DUE`.
- MEDIUM duration: 10 seconds.
- HIGH duration: 60 seconds.
- LOW has no timer and is already `RELEASED` after risk routing.
- VERY_HIGH has no deadline and is never automatically released by time.
- V10 risk-route, reservation-state and deadline-order constraints remain
  unchanged and are mirrored by the Java state/entity checks.

No Flyway migration, runtime grant, external dependency, package installation,
system change or network download was required.

## Test safety and verification coverage

Maven Surefire sets `safepay.protection-scheduler.enabled=false` during tests.
This prevents a background production schedule from racing or mutating Oracle
fixtures. Scheduler unit tests invoke the trigger explicitly, and Oracle tests
invoke the release service/worker explicitly.

Coverage includes:

- approved one-second and maximum-50 configuration,
- rejection of invalid duration and batch values,
- authoritative Oracle timestamp retrieval,
- exclusion of LOW, VERY_HIGH, future and non-`PROTECTED` rows,
- oldest-deadline-first bounded discovery,
- exact-deadline release and pre-deadline rejection,
- cancellation using Oracle time even when the Java clock differs,
- reservation preservation during automatic release,
- repeat-safe missing and already-released candidates,
- continued batch processing after one worker failure,
- restart-style backlog recovery without in-memory timer registration,
- two concurrent scheduler-style service runs producing one release,
- expired cancellation racing scheduler release,
- bounded backlog draining across later cycles, and
- real Oracle row locks, commits, version updates and constraints.

The committed Oracle race fixtures are owner-created and constraint-valid but
do not insert `TRANSACTION_RISK_FACTOR` rows. The scheduler does not read risk
factor evidence, and omitting it allows safe fixture deletion without weakening
or disabling the append-only evidence trigger. Production transaction creation
and authorization continue to use `SAFEPAY_APP` services and persist immutable
risk evidence normally.

The confirmed baseline before Phase 2.8E-I is **399 tests passing**. Phase
2.8E-I adds six tests, so the expected final full-suite total is **405 tests**.
Final lock-in remains pending until the focused and full Maven gates are run and
confirmed by the user.

## Explicitly deferred work

- **Phase 2.9:** reservation consumption, balanced double-entry ledger posting,
  `SETTLED` transition and durable transaction-exception recording.
- **Phase 2.10:** OTP challenge generation, hashing, expiry, resend and attempt
  controls.
- **Phase 2.11:** Risk Officer approval, rejection and re-verification.
- **Phase 2.12:** scheduler audit events, customer notifications, WebSocket
  status delivery and broader operational observability.
- **Deferred security phase:** complete JWT validation, RBAC enforcement and
  stable Spring Security `401`/`403` handlers.
- Distributed leader election and higher-throughput lock-skipping are not
  required for the V1 prototype. They may be reconsidered with load evidence;
  correctness currently comes from database locking, versioning and state
  revalidation.

## Operational notes for teammates

- Do not calculate expiry eligibility from the browser clock.
- Do not cache deadline ownership in Java memory.
- Do not release VERY_HIGH transactions because time passed.
- Do not increase the V1 batch above 50 without an approved policy change.
- Do not replace the worker's lock-and-recheck with a state change based only
  on the earlier candidate query.
- Do not end or consume the reservation during automatic release.
- Do not treat `RELEASED` as external settlement.
- Do not run production scheduling during integration tests.
- A repeatedly failing row remains visible for later retries; inspect internal
  logs rather than manually forcing its state.
- If an Oracle test is forcibly terminated, search for transaction references
  beginning `TX-SCHED-` and the unique fixture token before performing
  owner-controlled cleanup.
