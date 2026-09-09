# SafePay — Phase 1 Spec (Retail MVP)

Scope: a single customer can register, log in, add a beneficiary, send a payment,
have its amount placed into a fixed risk range, get protected-and-cancellable when
applicable, and see its history. No corporate roles, WebSockets, or AI. See
`AGENTS.md` for the full exclusion list.

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
  riskTier (enum: LOW/MEDIUM/HIGH/HARD_HOLD), protectionSeconds,
  authenticationRequired, riskReason, protectionExpiresAt (timestamp, nullable),
  version (int, optimistic lock), idempotencyKey,
  createdAt, authorizedAt, releasedAt, settledAt, cancelledAt

## 2. Amount-range risk engine (plain Java, unit-tested, no Spring/DB dependency)

Input: transaction amount only.

Rules (hardcode these ranges as constants):
| Amount | Tier | Result |
|---|---|---|
| ₹1–₹10,000 | LOW | Settle immediately. No hold. |
| ₹10,001–₹50,000 | MEDIUM | 10-second cancellable protection window. |
| ₹50,001–₹1,00,000 | HIGH | 60-second cancellable protection window. |
| Above ₹1,00,000 | HARD_HOLD | Do not settle until authentication is completed. |

Output: `RiskAssessment(tier, protectionWindowSeconds, authenticationRequired, reason)`.
Persist the selected range as the transaction's `riskReason`, for example:
`"Amount ₹25,000 is within the Medium-risk range."`

## 3. State machine (Phase 1 subset)

```
CREATED → AUTHORIZED → RISK_ASSESSED
  ├─ LOW           → SETTLED (immediately)
  ├─ MEDIUM/HIGH    → PROTECTED → CANCELLED
  │                              → SETTLED (via scheduled auto-release)
  └─ HARD_HOLD      → HARD_HOLD → SETTLED (after authentication)
```

Valid transitions only:
- CREATED → AUTHORIZED
- AUTHORIZED → RISK_ASSESSED
- RISK_ASSESSED → SETTLED (if LOW)
- RISK_ASSESSED → PROTECTED (if MEDIUM/HIGH)
- RISK_ASSESSED → HARD_HOLD (if amount is above ₹1,00,000)
- PROTECTED → CANCELLED (only if `now() < protectionExpiresAt`)
- PROTECTED → SETTLED (only via the scheduled job, only if `now() >= protectionExpiresAt`)
- HARD_HOLD → SETTLED (only after successful server-side authentication)

Explicitly reject (throw `InvalidStateTransitionException`): SETTLED → anything,
CANCELLED → anything, PROTECTED → SETTLED triggered by a user action instead of
the scheduler.

Concurrency: use the `version` column with a conditional update
(`WHERE id = ? AND version = ? AND state = 'PROTECTED'`) for both cancel and the
scheduler's release, so a race between "user cancels" and "timer expires" has
exactly one winner and the other gets a clean rejection, not a corrupted state.

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
`@Scheduled(fixedRate = 5000)` — query all `TRANSACTION` rows where
`state = 'PROTECTED' AND protectionExpiresAt <= now()`, and for each, attempt the
conditional update to `SETTLED`. Log how many it released each run (debug level).
Must be safe to run if the app restarts mid-window — it re-queries from the DB,
never from memory.

## 6. Definition of done for Phase 1
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
