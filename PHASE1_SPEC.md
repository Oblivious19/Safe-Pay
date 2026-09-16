# SafePay — Phase 1 Spec (Retail MVP)

**Approved updated-ZIP additions (16 September 2026):** retain email/password and
add mobile/password using the same persisted lockout; add administrator details,
provisioning, audited user status actions and idempotent simulated credits; add a
payment review, database-relative countdown and inline cancellation; improve
beneficiary validation and responsive layouts. No PIN-only login or simulated KYC.
Existing Oracle records, multi-account support, amount ranges, session/CSRF and
server-side password verification remain. See docs/UPDATED_ZIP_REVIEW.md.

**16 September 2026 database/UI update:** the user approved migrating all existing Oracle users and accounts and adding an account selector. Owners may have multiple accounts; new registration still creates one account. Preserve existing IDs, balances, password hashes and saved payment history. Account/beneficiary ownership and matching source accounts are server-validated. See docs/ORACLE_UI_SETUP.md for the current startup and migration instructions.

Scope: a single customer can register, log in, add a beneficiary, send a payment,
have a transparent rule-based risk assessment, get protected-and-cancellable when
applicable, and see its history. No corporate roles, WebSockets, or AI. See
`AGENTS.md` for the full exclusion list.

**Current-policy note (15 September 2026):** section 2 describes the running
amount-only policy. The retained entity sketches, standalone state-machine target
and older endpoint/acceptance sections are historical design references; they do
not override the delivered session authentication, seven live states, existing
verification endpoint or current API paths documented in README.md and the enhanced
Postman guide. No migration 11 integration is part of this policy change.

## 1. Entities (JPA, Oracle sequences for IDs)

**AppUser**
- id, email (unique), passwordHash, fullName, role (enum: CUSTOMER only for now),
  createdAt, accountLocked (boolean), failedLoginAttempts

**Account**
- id, userId (FK), balance (BigDecimal 18,2), currency ("INR"), averageMonthlySpend
  (BigDecimal, nullable — used by risk rule #4), createdAt

**Beneficiary**
- id, accountId (FK, owner), name, bankAccountNumber, ifsc, nickname, status
  (ACTIVE/INACTIVE), createdAt (this IS the "beneficiary age" signal — no separate
  field needed)

**Transaction**
- id, transactionRef (e.g. "TXN-2026-000123", unique), fromAccountId (FK),
  beneficiaryId (FK), amount (BigDecimal 18,2), purpose, state (enum, see §3),
  riskTier (target enum: LOW/MEDIUM/HIGH/VERY_HIGH), protectionSeconds,
  authenticationRequired, riskReason, protectionExpiresAt (timestamp, nullable),
  version (int, optimistic lock), idempotencyKey,
  createdAt, authorizedAt, releasedAt, settledAt, cancelledAt

## 2. Current live risk policy — amount only (15 September 2026)

The user explicitly replaced contextual scoring for new live payments with the
fixed amount ranges below. The decision uses only the validated INR amount as a
`BigDecimal`: strictly positive, at most 16 integer digits and two decimal places.
The risk engine is framework-free and does not move money, authenticate a user,
persist a transaction or schedule a timer.

| Validated amount | riskTier | protectionSeconds | authenticationRequired | Initial live result |
|---|---|---:|---|---|
| > 0 and <= INR 10,000.00 | LOW | 0 | false | SETTLED immediately |
| > INR 10,000.00 and <= INR 50,000.00 | MEDIUM | 10 | false | PROTECTED, cancellable before expiry |
| > INR 50,000.00 and <= INR 100,000.00 | HIGH | 60 | false | PROTECTED, cancellable before expiry |
| > INR 100,000.00 | VERY_HIGH | 0 | true | HARD_HOLD until successful password verification |

Exact paise boundaries matter: 10000.00 is LOW, 10000.01 is MEDIUM; 50000.00 is
MEDIUM, 50000.01 is HIGH; 100000.00 is HIGH, 100000.01 is VERY_HIGH. First-payment
status, beneficiary age, prior settled payments, recent averages, and unknown or
elevated device/context evidence must not add points or change these outcomes.
Account ownership, beneficiary eligibility, balance reservations and the INR 5000
minimum still receive their existing server-side validation before a new payment.

Persist the selected tier, protection duration, authentication requirement and a
plain-language `riskReason` explaining the amount range and resulting protection.
A first INR 5000 payment to a newly added beneficiary therefore settles immediately;
its reason must not claim extra beneficiary/history/device/context points.
Invalid amounts stop processing; never catch an evaluation error and assume LOW.
VERY_HIGH's zero duration means **no timed release**, never immediate settlement.
The existing password-verification endpoint settles an owned HARD_HOLD once after
successful server-side authentication; wrong credentials leave its funds reserved.

### Existing transactions and idempotency

Apply this policy only when creating a new transaction. Existing rows retain their
saved risk tier, reason, state and protection deadline. An idempotency replay with
the same request returns its stored decision; it must not run the new policy again
or debit money again. There is no historical-data rewrite or amount-based backfill.
The separate historical contextual engine/domain specifications may remain for
reference; they do not define classification for new live payments.

### Live state and schema boundary

The running flow keeps CREATED, AUTHORIZED, RISK_ASSESSED, PROTECTED, HARD_HOLD,
CANCELLED and SETTLED. LOW settles during initiation; an expired PROTECTED payment
settles through the guarded scheduler; a verified HARD_HOLD settles through the
password workflow. VERY_HIGH is a risk tier and HARD_HOLD is a transaction state.

Section 3 below describes the preserved isolated eight-state domain specification;
it is not a new instruction to integrate that domain or run migration 11. Current
API/setup details are in README.md and the enhanced Postman guide. Keep the existing
reviewed schema compatibility for risk tiers and durations; do not reinterpret or
rewrite historical transactions when deploying this amount-only policy.

## 3. Transaction protection state machine — source of truth

This section is the authoritative Phase 1 transition contract. The only target
states are CREATED, AUTHORIZED, PROTECTED, HARD_HOLD, RELEASED, CANCELLED,
REJECTED and SETTLED. VERY_HIGH is a risk tier, never a transaction state.
RISK_ASSESSED is legacy data, not a ninth target state.

```text
CREATED -> AUTHORIZED
              | LOW         -> RELEASED -> SETTLED
              | MEDIUM/HIGH -> PROTECTED -> CANCELLED (owner, before expiry)
              |                         -> RELEASED -> SETTLED (at/after expiry)
              | VERY_HIGH   -> HARD_HOLD -> SETTLED (verified internal approval)
                                        -> REJECTED (internal rejection)
```

| From | To | Required server-side condition |
|---|---|---|
| CREATED | AUTHORIZED | Existing server authorization step |
| AUTHORIZED | RELEASED | LOW; no hold, no authentication requirement |
| AUTHORIZED | PROTECTED | MEDIUM (10 seconds) or HIGH (60 seconds); expiry = server now + duration |
| AUTHORIZED | HARD_HOLD | VERY_HIGH; authentication required, zero duration, no expiry |
| PROTECTED | CANCELLED | Authenticated caller owns transaction and now < expiry |
| PROTECTED | RELEASED | Internal timer action and now >= expiry |
| RELEASED | SETTLED | Internal settlement processing |
| HARD_HOLD | SETTLED | Trusted internal approval after successful verification |
| HARD_HOLD | REJECTED | Trusted internal rejection |

No other edge is allowed. SETTLED, CANCELLED and REJECTED are terminal. An expired
protection window cannot be cancelled, including at exact equality. HARD_HOLD cannot
be timer-released, cancelled, or passed through ordinary settlement. Release is not
settlement: RELEASED records permission to proceed, not evidence that funds moved.
LOW releases and settles immediately through two domain transitions, without delay.
The machine validates risk-result consistency, but does not recalculate risk scores.
Invalid transitions, inconsistent snapshots and ownership/time failures throw
`InvalidStateTransitionException`; never silently succeed or fall back to LOW.

Approve/reject are package-private internal domain operations, not APIs or a
verification mechanism. Calling code must establish a trusted verified approval or
authorized rejection before invoking them. No client-supplied state/approval flag
is accepted. No approval endpoint or authentication changes are part of this task.

### Implementation and deployment boundary

`ProtectionState` and `TransactionProtectionStateMachine` are isolated domain code
with immutable trusted snapshots and explicit server times. They have no JPA,
Spring, timer, account-balance or network side effects. They are NOT wired into
`TransactionServiceImpl`, its scheduler, controllers or repositories in this task.
The legacy `TransactionState` JPA enum remains unchanged solely for that boundary.

Migration 11 adds the eight-state check and removes the old check, after migration
10. It does not alter any row. If legacy RISK_ASSESSED rows exist, it stops BEFORE
DDL and requires explicit review of those transactions and their audit history.
There is no automatic mapping: risk assessed does not prove release or settlement.
Both migrations require review/manual execution; neither is executed here.

Future persistence integration must reload database state, obtain caller identity
from the session, and condition writes on owner, expected state, version and expiry
in the same database transaction as ledger changes. Cancellation/release races must
have only one winner. Idempotency replay belongs to that service boundary: return
the previous result without requesting another domain transition or moving money
again. These isolated tests do not prove database concurrency or atomic settlement.

## 4. API endpoints (Phase 1 only)

```
POST   /api/auth/register
POST   /api/auth/login                     → returns JWT

POST   /api/beneficiaries                  → auth required
GET    /api/beneficiaries                  → auth required, current user's only
PATCH  /api/beneficiaries/{id}/status

POST   /api/transactions/initiate          → header: Idempotency-Key
  body: { fromAccountId, beneficiaryId, amount, purpose }
  response: { transactionRef, state, riskTier, protectionExpiresAt, riskReason }

GET    /api/transactions/{id}
GET    /api/transactions?status={state}    → current user's only
POST   /api/transactions/{id}/cancel       → header: Idempotency-Key
```

## 5. Scheduled job
Target after persistence integration: poll TRANSACTION_DB for PROTECTED rows whose
expiry has passed, perform the guarded PROTECTED -> RELEASED -> SETTLED sequence
from section 3, and never timer-release HARD_HOLD. Reload state after restarts.
The existing scheduler is deliberately unchanged until migration 11 and integration.

## 6. Definition of done for Phase 1

Section 3 is authoritative for transaction transitions; the historical amount-based
examples below are not contextual-engine acceptance criteria. Live readiness requires
reviewed migrations 10/11 plus separately tested persistence integration. This task's
acceptance is isolated tests of every allowed edge, all other source-state/operation
combinations, exact expiry boundaries, ownership and invalid protection decisions.
- Can register, log in, get a JWT, and call authenticated endpoints with it.
- Can add a beneficiary and see it in the list.
- Sending an amount up to ₹10,000 settles instantly (`state = SETTLED` in the
  initiate response).
- Sending ₹10,001–₹1,00,000 returns `state = PROTECTED` with the applicable
  10- or 60-second `protectionExpiresAt` and a plain-language `riskReason`.
- Sending an amount above ₹1,00,000 returns `state = HARD_HOLD` and does not
  settle until a future authentication endpoint is implemented.
- Calling cancel before expiry moves it to `CANCELLED`; calling it after expiry
  returns a clear error, not a silent no-op.
- Waiting past `protectionExpiresAt` without cancelling results in the scheduled
  job flipping it to `SETTLED` on its own.
- `GET /transactions?status=PROTECTED` (etc.) returns the right filtered list.
- Unit tests exist for the amount-range engine covering every boundary case.

