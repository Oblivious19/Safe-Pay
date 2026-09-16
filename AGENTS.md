# SafePay — Agent Context

Read this before every task. If a request conflicts with this file, follow this file and flag the conflict instead of guessing.

## What this project is
SafePay is a **simulated**, risk-adaptive, pre-settlement transaction-control layer for
Indian bank transfers (NEFT/RTGS/IMPS/UPI). It does **not** touch real payment rails.
It sits between "payment authorized" and "payment settled" inside its own simulated
ledger, and decides — based on a rules-based risk score — whether a payment settles
instantly or gets a short cancellable "protection window" first.

Positioning line (use in any UI copy): "SafePay is a simulated risk-adaptive
pre-settlement transaction-control layer."

## Current phase: Phase 1 — Retail MVP only
Full scope details are in `PHASE1_SPEC.md`. Do not implement anything outside that
file unless explicitly asked, even if it's mentioned in the full product PRD.
Specifically, do NOT build yet (these are later phases):
- Maker-Checker / corporate roles / approval queues
- WebSocket push (poll `GET /transactions/{id}` instead)
- `LEDGER_ENTRY` / `TRANSACTION_EXCEPTION` tables
- Admin-configurable risk ranges (hardcode them in Java for now)
- Any AI/ML component
- Disputes, notifications beyond basic in-app rows, PWA installability

Current live risk policy (explicit user change, 15 September 2026): classify each
new payment by its validated amount only. Amounts above zero through INR 10,000
are LOW and settle immediately; above INR 10,000 through INR 50,000 are MEDIUM
with a 10-second cancellable hold; above INR 50,000 through INR 1,00,000 are HIGH
with a 60-second cancellable hold; above INR 1,00,000 are VERY_HIGH and enter
HARD_HOLD until successful server-side password verification. Persist the amount
range and resulting protection requirement as a plain-language risk reason.
Beneficiary age, prior payments, history, device and context must not contribute
points to a new live decision. See PHASE1_SPEC.md section 2 for exact boundaries.
The risk engine remains framework-free; it does not move money or authenticate.
VERY_HIGH's zero duration means no timed release, never immediate settlement.

Compatibility boundary: existing transactions retain their saved tier, reason,
state and hold deadline. An idempotency replay returns the saved decision; do not
rescore or rewrite existing rows. Historical contextual-engine specifications and
original comparison reports remain historical references. Live transaction
states remain CREATED/AUTHORIZED/RISK_ASSESSED/PROTECTED/HARD_HOLD/CANCELLED/SETTLED;
the separate eight-state domain is not wired into this flow. The approved additions
include server-password verification from HARD_HOLD directly to SETTLED, persistent
retry keys, reservation-aware money updates and per-payment scheduled settlement.
Keep session/CSRF security. On 16 September 2026 the user explicitly approved preserving all migrated accounts and adding a UI account selector. An owner may have multiple accounts; registration still creates one account. Account and beneficiary selection must be authorized server-side and a payment beneficiary must belong to the selected source account. See docs/BEST_MODULES_AND_CHANGES.md.
No AI/ML or new device tracking is introduced.
Approved updated-ZIP merge (16 September 2026): retain email/password and add
phone/password with shared lockout; no PIN-only login, simulated KYC or OTP.
Add admin details, all-account lookup, audited provisioning/status actions and
idempotent simulated credits. Credits require Idempotency-Key and persist a unique
AUDIT_LOG.REQUEST_KEY together with balance changes. A replay returns its original
receipt; it must never credit again. Provisioning uses an initial password, not
an expiring password, and never auto-promotes a live customer.
Protected requests verify current database status/role so suspended or changed
sessions cannot retain access. Preserve the current public registration, profile,
multi-account, amount-risk, verification and reservation behavior. Payment review
and cancellation timers use database-derived remaining duration; the database
still decides whether cancellation succeeds. See docs/UPDATED_ZIP_REVIEW.md.

## Tech stack
- Java 17, Spring Boot 3.x, Spring MVC
- Spring Data JPA (Hibernate) + Oracle DB, `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Use Spring Security session authentication, not JWT.
- Spring `@Scheduled` for the auto-release job
- Maven build
- Lombok is fine to use for boilerplate reduction
- Testing: JUnit 5 + Mockito

## Hard rules (never violate these, even if a prompt doesn't repeat them)
1. All money fields are `NUMBER(18,2)` in the DB and `BigDecimal` in Java — never
   `float`/`double`.
2. The database is the source of truth for transaction state — never trust
   in-memory or client-sent state.
3. Every state transition must be validated server-side against the state machine
   in `PHASE1_SPEC.md`. Invalid transitions must throw a custom exception, not
   silently succeed.
4. Every state-changing endpoint (`initiate`, `cancel`) must be idempotent —
   accept an `Idempotency-Key` header.
5. Never let a risk-rule evaluation failure default to LOW risk — fail toward the
   more protective tier.

7. Every risk decision must persist *why* (contributing signals) so it can be
   explained in plain language later — this is not optional polish.

## Code style / structure
- Use global layer packages: beans, controller, repository, services, excp, and config.
- Keep the risk-scoring logic as a plain, framework-free Java class/function that
  is unit-testable without Spring context.
- Prefer constructor injection over field injection.
- Write a JUnit test alongside any non-trivial logic (state transitions, risk
  scoring) in the same task — don't defer tests to "later."

## Workflow expectations for you (the agent)
- Work through one prompt/task at a time. Do not jump ahead to a later step even
  if it seems convenient to do now.
- After each task, summarize what you changed and how to manually verify it
  (e.g. a curl command), so it can be checked before moving on.
- If Oracle-specific SQL/DDL is needed, generate it — but let `ddl-auto=update`
  handle table creation during early development unless asked otherwise.
- Ask before deleting or overwriting existing files outside the current task's
  scope.


