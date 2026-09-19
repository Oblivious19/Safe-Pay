# SafePay Backend Phase 2.9 — Ledger Settlement and Exception Recording

## Purpose and scope

Phase 2.9 converts an eligible `RELEASED` payment into one internally simulated, balanced and immutable settlement. It debits the customer's source account and credits SafePay's configured outbound-clearing account. This represents SafePay's internal pre-settlement completion only; it does not claim that an external beneficiary or live Indian payment rail received the funds.

The phase also adds bounded polling, safe retry scheduling, exception escalation, minimum atomic audit evidence and a pending in-app notification seam. Notification delivery, read-status APIs and WebSocket publishing remain in Phase 2.12.

No Flyway migration was added or changed. The implementation maps the already applied V6 ledger/exception structures and V7 audit/notification structures.

## Phase contents

| Subphase | Implemented responsibility |
|---|---|
| 2.9A | Settlement configuration: external clearing-account ID, one-second polling, batch 25, three approved retry delays and manual-review exhaustion. |
| 2.9B | Exact V6 entity model for posting headers, immutable entries and transaction exceptions. |
| 2.9C | Restricted repositories, pessimistic mutable-row locks and append-only ledger APIs. |
| 2.9D | Deterministic posting identity and balanced debit/credit pair construction. |
| 2.9E | One atomic transaction for locks, balance movement, ledger insertion, state transition and evidence. |
| 2.9F | Restart-safe database polling and competing-instance-safe processing of at most 25 candidates. |
| 2.9G | Separate failure transaction, exact retry schedule and `MANUAL_REVIEW` exhaustion. |
| 2.9H | Immutable audit event and durable pending in-app notification for successful settlement. |
| 2.9I | Unit, contract and Oracle integration verification for atomicity, reconstruction and duplicate prevention. |

## Approved V1 operational policy

| Setting | V1 value |
|---|---|
| Poll interval | 1 second |
| Maximum batch size | 25 |
| Clearing account | Externally configured `outbound-clearing-account-id` |
| Clearing validation | Ownerless, active, INR, `OUTBOUND_CLEARING` |
| Automatic retries | Exactly 3 |
| Retry 1 | 5 seconds after the initial failed attempt |
| Retry 2 | 30 seconds after retry 1 fails |
| Retry 3 | 1 minute after retry 2 fails |
| Retry 3 failure | `MANUAL_REVIEW`; no fourth automatic retry |
| Scheduler default | Disabled until an environment-specific clearing ID is supplied and processing is explicitly enabled |

The Phase 2.8 protection-release scheduler remains independent and retains its approved batch size of 50.

## Main production components

### Ledger and exception domain

- `LedgerPosting` is the mutable V6 posting header. It starts as `PENDING` and becomes `POSTED` only after Oracle can see a complete balanced pair.
- `LedgerEntry` represents immutable line 1 `DEBIT` and line 2 `CREDIT` entries.
- `TransactionExceptionLog` stores safe failure identity, retry count, next retry time and manual-review status.
- `SettlementPostingFactory` derives the posting reference, idempotency identity, amount, currency and both ledger lines from one locked transaction snapshot.
- `SettlementPostingPair` rejects mismatched identities, amounts, currencies, line numbers, sides or same-account pairs before persistence.

### Atomic settlement

- `SettlementServiceImpl` owns the atomic settlement transaction.
- The payment row is locked first and must still be `RELEASED`.
- Source and clearing accounts are then locked in ascending account-ID order to reduce deadlock risk.
- Existing settlement postings are checked before any financial mutation.
- `SettlementAttemptOutcome` distinguishes a new settlement, a verified prior settlement, an ineligible state and a missing row.
- `SettlementInvariantException` carries a safe upper-case failure code for settlement-specific invariant failures.

### Polling and retries

- `ReleasedTransactionSettlementScheduler` triggers at the configured one-second fixed delay only when settlement processing is enabled.
- `ReleasedTransactionSettlementProcessorImpl` retrieves one bounded database batch and processes candidates independently.
- `TransactionDao.findDueSettlementTransactionIds` returns either a newly released payment with no settlement exception or a retry whose persisted `next_retry_at` is due.
- Manual-review, open non-retryable and future-retry rows are excluded by the database query.
- `SettlementFailureRecorderImpl` uses a separate `REQUIRES_NEW` transaction after the failed settlement transaction has rolled back.

### Audit and notification seam

- `AuditLog` maps the immutable V7 `AUDIT_LOG` table and records the system-owned `RELEASED -> SETTLED` event.
- `AppNotification` maps the durable V7 `APP_NOTIFICATION` outbox and inserts one deduplicated pending `PAYMENT_SETTLED` in-app notification.
- `SettlementEvidenceServiceImpl` appends both records inside the same transaction as the balances, ledger and state transition.
- The notification text explicitly describes SafePay's simulated settlement and does not assert external-bank receipt.

## Canonical successful settlement flow

```text
Database candidate query
        |
        v
Lock PAYMENT_TRANSACTION
        |
        +-- missing / not RELEASED ----------> no financial effect
        |
        v
Validate no prior posting
        |
        v
Lock source and clearing accounts
in lower-ID-first order
        |
        v
Validate ownership, type, active status,
INR currency and full reservation
        |
        v
Insert PENDING LEDGER_POSTING
        |
        v
Insert DEBIT line 1 + CREDIT line 2
        |
        v
Consume source reservation and balance
Credit clearing balance by same amount
        |
        v
End transaction reservation
RELEASED -> SETTLED
        |
        v
Finalize posting as POSTED
        |
        v
Append immutable audit + pending notification
        |
        v
Commit everything together
```

The two financial sides are:

```text
Customer source account    DEBIT   payment amount
SafePay outbound clearing  CREDIT  payment amount
                            ----------------------
Total debit = total credit
```

## Atomicity and rollback guarantees

The settlement service uses one transaction for all of the following:

1. payment and account locks;
2. source current-balance reduction;
3. source reserved-balance reduction;
4. clearing balance increase;
5. posting header and both ledger entries;
6. `RELEASED -> SETTLED` and reservation end;
7. immutable audit record;
8. durable pending notification.

If any operation fails, Spring rolls back the whole settlement unit. The processor catches the failure only outside that boundary and then calls the independent failure recorder. Therefore, saving retry information cannot commit a partial balance, one-sided ledger, or premature `SETTLED` state.

## Idempotency and concurrency behavior

- The deterministic posting reference is `PAYMENT-SETTLEMENT-{transactionId}`.
- The deterministic posting idempotency key is `TRANSACTION_SETTLE:{transactionReference}`.
- V6 independently enforces one posting per payment and one entry per posting side/line.
- A second caller locks the same payment after the first caller. If the first committed, the second verifies the existing `POSTED` posting and returns `ALREADY_SETTLED` without changing balances or evidence.
- Competing schedulers may discover the same candidate, but the payment lock ensures that only one can perform the financial effect.
- Source and clearing locks always use the same ascending-ID order.

## Retry and exception lifecycle

An initial temporary failure creates one retryable `SETTLEMENT` exception and schedules retry 1 after five seconds.

```text
initial failure
    -> RETRY_PENDING, retry_count 1, +5 seconds

retry 1 fails
    -> RETRY_PENDING, retry_count 2, +30 seconds

retry 2 fails
    -> RETRY_PENDING, retry_count 3, +1 minute

retry 3 fails
    -> MANUAL_REVIEW, retry_count 3, no next retry
```

There is no automatic retry 4. While a retry is pending, the payment remains `RELEASED` and its reservation remains intact.

A definitive customer-account failure is allowed to move the payment to `FAILED` only when the source reservation can be safely released in the separate failure transaction. An ambiguous or unsafe failure is retained for the approved retry/manual-review route instead of fabricating success or discarding financial evidence.

## Oracle alignment

- V6 triggers reject ledger-entry updates/deletes, entry/header mismatches, incomplete posting finalization and terminal posting mutation.
- V6 unique constraints independently prevent duplicate transaction postings and duplicate sides or lines.
- V7 makes audit rows immutable and protects notification recipient/content/business identity.
- Application repositories intentionally expose no delete operation for ledger entries, audit rows or notifications.
- Database time (`SYSTIMESTAMP`) decides settlement timestamps and retry eligibility.
- No V1–V11 migration was edited.

## Verification coverage

The Phase 2.9E–I verification package covers:

- atomic orchestration and lower-ID-first account locks;
- missing, non-released, already-settled and inconsistent candidates;
- complete reservation validation;
- batch bounds and continuation after one candidate fails;
- separate failure-recording errors without backlog blockage;
- exact 5-second, 30-second and 1-minute retry sequence;
- exhaustion at three retries with no fourth retry;
- safe definitive failure and reservation release;
- V7 entity vocabulary, mappings and immutable repository boundaries;
- deterministic audit and notification identity;
- Oracle balance movement, two-line posting, duplicate no-op and ledger reconstruction;
- database-due retry selection.

The verification classes are:

- `SettlementServiceImplTest`
- `ReleasedTransactionSettlementProcessorTest`
- `ReleasedTransactionSettlementSchedulerTest`
- `SettlementFailureRecorderImplTest`
- `SettlementEvidenceServiceTest`
- `SettlementEvidenceModelTest`
- `SettlementEvidenceRepositoryContractTest`
- `SettlementServiceOracleIntegrationTest`

## Explicitly deferred work

- Email OTP delivery and the complete OTP challenge flow belong to Phase 2.10.
- Notification delivery attempts, read/unread APIs and WebSocket publication belong to Phase 2.12.
- Real NPCI/RBI rail submission and external-beneficiary settlement confirmation are outside SafePay V1.
- Compensating ledger corrections require a separately approved feature; posted entries remain immutable.
- Production-scale tuning beyond the approved V1 batch of 25 is deferred.

## Operational notes for teammates

1. Supply `safepay.settlement-processor.outbound-clearing-account-id` (or Spring's relaxed environment form `SAFEPAY_SETTLEMENT_PROCESSOR_OUTBOUND_CLEARING_ACCOUNT_ID`) with an existing active ownerless INR `OUTBOUND_CLEARING` account ID.
2. Keep `safepay.settlement-processor.enabled=false` until the environment-specific clearing account has been verified.
3. Enable the processor only through approved environment configuration; do not hard-code or select the first clearing row.
4. A temporary failure should be inspected through `TRANSACTION_EXCEPTION`; do not manually force the payment to `SETTLED`.
5. `MANUAL_REVIEW` means automatic processing is exhausted and intentionally stopped.
6. The scheduler is restart-safe because candidates and retry deadlines live in Oracle rather than process memory.
7. Never update or delete a posted ledger entry or immutable audit row.
8. Run the focused Phase 2.9 test groups and the complete Maven suite before locking this phase.
