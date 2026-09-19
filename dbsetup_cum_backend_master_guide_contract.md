## Intended delivery order

### 1. Data Seed Complete Guide

The first separate guide will cover:

- Synthetic, realistic Indian SafePay personas—not real customer data.
- Customers, risk officers, system administrators, and auditors.
- Accounts, beneficiaries, safety settings, transactions, OTP scenarios, risk reviews, settlement states, notifications, audit evidence, and ledger examples.
- Exact insertion order based on sequences, foreign keys, check constraints, unique constraints, and triggers.
- Which credentials perform each action:
  - `SAFEPAY_OWNER` for controlled fixture creation where necessary.
  - `SAFEPAY_APP` only for permitted runtime operations.
- Positive, boundary, fraud-risk, ownership, concurrency, and failure scenarios.
- Repeatability, duplicate prevention, cleanup, and safe reset instructions.
- Mapping of every seed value to the relevant V1–V11 constraint.
- Explicit warnings where a completed or immutable row cannot legally be modified.
- No changes to V1–V11.
- No execution or database mutation by me without explicit authorization.

### 2. Backend Launch and Server Guide

After the seed guide is reviewed, the next separate guide will explain:

- Required Java, Maven, and Oracle prerequisites.
- Every required environment variable.
- Database owner/runtime credential separation.
- JWT secret generation and validation.
- SMTP/Gmail development settings.
- Outbound clearing-account configuration.
- Browser origin, cookie, CORS, and CSRF configuration.
- Flyway validation and Hibernate schema validation.
- Starting the Spring Boot backend.
- Confirming application startup and Oracle connectivity.
- Health and authentication smoke tests.
- Obtaining access and refresh tokens.
- Supplying JWT, cookies, CSRF tokens, correlation IDs, and idempotency keys during API testing.
- Safe shutdown and common startup-error diagnosis.

We will not move into the full explanation suite until you confirm that the seeded database and running backend work correctly.

### 3. Master Backend Understanding Guide

This will be one incrementally built guide covering Phase 2.1 through Phase 2.12.

For every class, including `Account.java`, I will document:

- Why the class exists.
- Its SafePay business purpose.
- Every field and datatype.
- Constructors, factories, methods, and state mutations.
- Validation and normalization logic.
- Exact table and column mapping.
- Exact V1–V11 migration mapping.
- Related primary keys, sequences, foreign keys, checks, unique constraints, indexes, views, triggers, and procedures.
- Explicit confirmation when no trigger or procedure is involved.
- Which repository, service, controller, scheduler, or security class consumes it.
- Which later phase depends upon it.
- How it supports SafePay’s risk-adaptive pre-settlement objective.
- What was introduced during that phase versus what was added later.
- Known V1 limitations and deliberately deferred work.

I will stop after completing each phase and report exactly what was appended before proceeding.

Deferred Phase 2.2 security will be presented as one logical Phase 2.2 chapter containing:

- The original Phase 2.2 foundation.
- What was intentionally deferred.
- The later SEC-A through SEC-K completion.
- Authentication, JWT, refresh rotation, RBAC, CSRF, CORS, WebSocket security, administrative controls, and security auditing.

This preserves both conceptual phase order and actual implementation chronology.

### 4. Separate Phase API-Test Guides

Each phase will receive its own new API-test guide; these will not be placed inside the master understanding guide.

Every applicable endpoint will include:

- Purpose and classes exercised.
- Preconditions and required seed persona.
- HTTP method and URL.
- Required role.
- JWT requirements.
- Refresh-cookie and CSRF requirements where applicable.
- Correlation and idempotency headers.
- Exact request body.
- Expected status, headers, and safe response body.
- Positive cases.
- Missing and malformed input.
- Boundary and database-constraint cases.
- Authentication and authorization failures.
- Ownership isolation.
- Duplicate and conflict behavior.
- Illegal state transitions.
- Idempotency replay and key-reuse conflicts.
- Concurrency-sensitive cases where externally testable.
- False-positive and false-negative risk cases.
- Data-leakage checks.
- Expected follow-up API calls for verifying the resulting state.

I will not invent an API for a phase that had no direct controller. Such a phase will be marked as having no direct endpoint, followed by the later legitimate API that exercises its code indirectly.

## Review methodology

Before documenting a phase, I will cross-check:

1. Current source code.
2. V1–V11 SQL migrations and repeatable grants.
3. Updated Decision Register.
4. Updated implementation plan.
5. Operative contract.
6. Existing phase summary guides.
7. PRD only where it does not conflict with the Decision Register.
8. “SafePay - DB Design.”
9. “Safepay - DB to Backend shift.”
10. This current backend-development task.
11. The verified final baseline of **866 passing tests**.

Each important statement will be based on the final code and schema—not solely on historical chat recollection.

## Coding-flaw rule

If this review reveals a possible implementation flaw:

- I will document the exact file, affected logic, evidence, risk, and proposed correction.
- I will not edit the code, migration, configuration, documentation, or test.
- I will wait for your explicit approval.
- Applied V1–V11 migrations will remain immutable; any approved future database correction must be forward-only.

## One clarification before starting

For the first deliverable, should the data-seed guide contain the executable seed SQL inside the guide for you to run manually, or do you also want a separate executable development-only SQL seed file?
