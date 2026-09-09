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

Phase 1 uses fixed transaction-amount risk ranges: LOW has no hold, MEDIUM has a
10-second cancellable hold, HIGH has a 60-second cancellable hold, and an amount
above ₹1,00,000 enters HARD_HOLD and requires server-side authentication before
settlement. Store the plain-language range reason on the transaction.

## Tech stack
- Java 17, Spring Boot 3.x, Spring MVC
- Spring Data JPA (Hibernate) + Oracle DB, `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Spring Security + JWT for auth not needed remove that
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
