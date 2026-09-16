# Strongest modules across the three original versions

“Strongest” refers to the compared source, not a claim of production readiness. This delivery modifies only a copy of Shreya.
The user approved overlapping transaction safeguards, profile editing, admin account editing and beneficiary reactivation.
The original-contribution column remains a historical comparison. The current-decision column includes the user's later 15 September 2026 change to amount-only risk for new payments.

| Module | Strongest original contribution | Decision in enhanced Shreya |
|---|---|---|
| Registration/onboarding | Shreya: roles, numbered account; Sahu: validation | Keep Shreya onboarding; add registration UI |
| Login/logout/lockout | Shreya | Preserve session and lockout behavior |
| User/profile management | Sahu/Ruchi: CRUD breadth; Shreya: ownership/security | Add current-customer profile edit; no unscoped CRUD |
| Account management | Shreya: one numbered account; Ruchi root: type/status editing | Keep single account; add ADMIN balance/type screen/API using Shreya SAVINGS/CURRENT |
| Beneficiaries | Shreya: session scoping/detail/soft delete; Sahu/Ruchi: reactivation | Retain default ACTIVE list and add optional inactive list/reactivation |
| Transfer initiation | Shreya: authenticated JSON; Sahu: safeguards | Retain endpoint and payload, strengthen safety |
| Pre-transfer validation | Shreya and Sahu | Preserve Shreya validator and pending balance logic |
| Risk assessment/explanations | Shreya: several transparent signals; Sahu/Ruchi: simpler amount policy | Per the later user request, use amount-only LOW/MEDIUM/HIGH/VERY_HIGH classification for new payments and persist an amount-range reason |
| Live protection states | Shreya: separate tested domain foundation; Sahu: guarded persistence | Preserve live states, add guarded completion operations |
| Cancellation | Sahu | Port persistent keys, database deadlines and fresh reads |
| Scheduled settlement | Sahu | Account locking, reservation/minimum checks and per-payment rollback |
| Hard-hold completion | None complete | New server-password verification with idempotent settlement |
| Transaction history | Sahu: richer ordering; Shreya: customer screens | Preserve API, add refresh/polling and verification controls |
| Retry/idempotency | Sahu | Retain Shreya initiate replay checks; add cancellation/verification keys |
| Reserved funds/concurrency | Sahu; Shreya already reserves at initiation | Strengthen settlement and admin balance edits |
| Audit writes | Shared baseline | Preserve existing events; add verified-settlement event |
| Admin status management | Shreya | Preserve APIs; add usable screens |
| Admin reports | Shreya | Preserve APIs/views; add summary/date-range screens |
| Dashboard | Shreya | Preserve dashboard and its behavior |
| Frontend transport/navigation | Shreya | Extend typed services, CSRF verbs, role-aware navigation and pages |
| Access control | Shreya | Session identity throughout; only narrowly scoped new permissions |
| Validation/errors | Sahu/Shreya | Add typed validation and conflict handling for new operations |
| Database mappings | Sahu monetary/key constraints; Shreya account/role foundation | Complete monetary/key mapping, migrations and local setup |
| Tests/developer documentation | Shreya: coverage breadth; Sahu: transaction safety tests | Add safety/security/database tests, setup and verification guide |

## Explicit preservation decisions

- New payments use only amount: >0–₹10,000 LOW/immediate; >₹10,000–₹50,000 MEDIUM/10 seconds; >₹50,000–₹1,00,000 HIGH/60 seconds; >₹1,00,000 VERY_HIGH/HARD_HOLD with password verification. Beneficiary/history/device/context points no longer affect live classification.
- Existing transaction decisions, reasons, states and deadlines are preserved. Idempotency replays return the stored result without rescoring; no data rewrite is part of the policy change.
- Customer session authentication, CSRF, role separation and the one-account-per-user constraint remain.
- No JWT, fake device evidence, multiple-account model, SALARY type, real payment rails or recipient ledger was added.
- Existing customer account/user CRUD that security intentionally denied remains denied. New editing is current-customer profile or ADMIN account management only.
- Default GET /api/beneficiaries remains ACTIVE-only; includeInactive=true is opt-in.
- The pure eight-state state machine remains isolated. Existing live state meanings are unchanged.
- Hard-hold password verification is a newly implemented simulated workflow, not an OTP/SMS or bank integration.

## New API contracts

| Method/path | Caller | Request |
|---|---|---|
| PUT /api/users/current | CUSTOMER + CSRF | name, email, phone, optional password |
| GET /api/beneficiaries?includeInactive=true | CUSTOMER | Opt-in inactive records |
| PATCH /api/beneficiaries/{id}/status | CUSTOMER + CSRF | status: ACTIVE or INACTIVE |
| GET /api/admin/accounts | ADMIN | Account list |
| PUT /api/admin/accounts/{id} | ADMIN + CSRF | balance and accountType |
| POST /api/transactions/{id}/verify | CUSTOMER + CSRF + Idempotency-Key | password |

Original detail/delete, status/report, registration, login and transaction endpoints are preserved. Their new screens use the existing contracts.

## New frontend pages and controls

- /register: customer registration.
- /admin: user statuses, account status/balance/type, summary and daily reports.
- /profile: current customer details/password editing.
- /beneficiaries: detail/remove/reactivate controls and inactive visibility.
- /transactions: server polling, reusable retry keys, password verification for HARD_HOLD.
- /send-money: result polling using server state.
- Login and application shell route administrators to Administration and customers to the existing customer pages.

## Delivery layout

Backend/ contains complete Maven sources, tests and wrapper.
Frontend/SafePayJet/ contains complete Oracle JET sources, package lock and tests.
Database/ contains the original scripts plus fresh-schema and incremental safeguards SQL.
docs/ contains original documentation plus this module assessment and delivery/validation notes.
