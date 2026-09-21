# SafePay-New implementation and acceptance plan

Checkpoint: 21 September 2026. All four canonical roles are implemented. Authorized live API/browser acceptance and before/after database reconciliation passed; OTP, scheduler-driven settlement and access-changing acceptance remain explicitly deferred in PENDING_USER_TESTS.txt.

## Decisions applied

The supplied recording and Ruchi's copied components control presentation: green/cream/lime palette, Manrope, rounded cards, existing header and payment sheet. Selective original SafePay authentication, exact-number, payment-recovery, OTP and notification logic was adapted into this repository with the user's approval. The copied backend controllers, DTOs, states and security rules control behavior.

The latest explicit user authorization supersedes the older manual-edit-only instructions in AGENTS.md. Nothing authorizes changes to the original two repositories, copied backend, migrations, Oracle grants or database foundations. Historical documentation and visual examples do not override the user's current instructions.

## Implemented screen inventory

| Audience | Routes / screens | Backend behavior exposed |
|---|---|---|
| Public | /home, /about, /login, /register | Existing visual introduction; email/mobile login; registration and returned customer ID |
| Customer | /dashboard | Account selection, separately retrieved current/reserved/available funds, recent and pending outgoing payments |
| Customer | /profile | Read-only customer identity, account/bank/IFSC details and current funds |
| Customer | /beneficiaries | Owned directory/detail; bank or UPI creation; optional nickname/relationship/purpose; disable/reactivate |
| Customer | /send-money | Recipient/amount/category/purpose, exact validation, review sheet, save instruction, separate explicit authorization, state-specific outcome |
| Customer | /transactions | Filters/pagination, detail, authoritative cancellation eligibility/countdown, authorize/cancel, OTP issue/resend/verify, recovery, risk explanation and timeline |
| Customer | /notifications | Paged notifications, mark read, related payment navigation |
| SYSTEM_ADMIN | /admin/dashboard, /admin/users, /admin/accounts, /admin/failures | Operational stats/failures; user search/detail/status/roles/session revocation; account search/detail/funds |
| RISK_OFFICER | /risk/reviews | Priority/oldest/category queue, review detail and rounds, approve/reject/request verification, internal note, transaction evidence |
| AUDITOR | /audit/logs, /audit/transactions, /audit/reviews, /audit/ledger, /audit/ledger-reconciliation, /audit/reservations, /audit/exceptions, /audit/policies | Read-only global audit, payments/risk evidence, reviews, ledger entries, both reconciliations, exceptions and versioned policies/bands |

Role grants, user status and session revocation controls are implemented, but live access-changing tests require a concrete approved target and rollback/restore plan. They have only been exercised with isolated fixtures so far.

## Contract and safety behavior

- Exact integer IDs and decimal money are preserved through lossless JSON and integer minor-unit arithmetic; unavailable balances are never displayed as zero.
- API prefix is /api/v1. Four roles remain CUSTOMER, SYSTEM_ADMIN, RISK_OFFICER and AUDITOR.
- Available balance is current balance minus reservations. No teammate retained-minimum rule was imported.
- Payment creation saves an instruction; authorization is a separate confirmed request. Category is required strictly above INR 100,000; Others has its own purpose constraint.
- The backend owns all ten transaction states, cancellation eligibility, risk policy, OTP limits, review decisions, reservation and settlement. Browser countdowns display observations and never cause settlement.
- A lost mutation response does not trigger a fresh request/key. Payment recovery retains a frozen allowlisted request and original idempotency key; unresolved/expired/corrupt recovery blocks replacement.
- OTP verification replay retains its code only in memory. Review recovery persists a minimal marker; internal notes/reasons are not stored in browser storage. Some recovery requires operator reconciliation after reload rather than invented replay.
- Tokens remain in memory. Cookie refresh uses the dedicated CSRF flow and coordinated browser locks; changed identities invalidate the previous view. Modern Chrome/Edge browser APIs are required.
- REST reads remain authoritative. STOMP events trigger refresh; polling/focus refresh supports stale views and connection gaps.
- Requests, timers and subscriptions account for navigation/disposal. Confirmation dialogs capture the selected payment, so switching a selection cannot authorize a different target.
- No fake admin reports, risk thresholds, database settings, account provisioning or unsupported backend mutations were added.

## Acceptance checkpoint

- 54 offline tests passed; the affected 23 UI/service tests were rerun after the final staff-filter fix and passed. TypeScript and the final build passed.
- 93 logged live API requests matched their expected success/denial/validation outcomes, covering all five approved users and four roles.
- Real browser acceptance covered customer payment creation and separate authorization, notification read/navigation, beneficiary creation/status, admin reads, risk queue/detail/timeline with STOMP connected, and every auditor tab. Browser sessions ended signed out.
- Baseline before and after snapshots validated all 19 tables. DATA_CHANGES.md records 1474 included-field differences across 107 rows, with no deletions; DATA_CHANGE_SUMMARY.md explains attribution.
- All current balances are unchanged. Only browser payment 1558 retains a new 1.00 reservation. The two protected API payments were cancelled and released their reservations.
- OTP challenges, reviews, ledgers, roles, policies and exceptions are unchanged. Both schedulers remain disabled. No backend file changed among the 502 captured hashes.
- OTP, live review decisions, protection expiry, released-to-settled processing, successful registration and admin access changes are deliberately pending. Follow PENDING_USER_TESTS.txt; they are not passed by inference from fixtures.
- Critical gates remain in force for backend changes, schema/grants, large financial drift and access-changing targets. No SQL cleanup or automatic scheduler enablement is authorized.

## Test data scope

| User ID | Intended test role |
|---|---|
| 2468 | Customer |
| 2470 | Second customer |
| 2498 | Risk Officer |
| 2499 | System Admin |
| 2500 | Auditor |

The user authorized direct read-only Oracle owner snapshots and the designated local credentials file. The before snapshot was captured at 02:11:50 +05:30 and after at 02:37:17 +05:30 on 21 September 2026. Both connections used read-only transactions and closed with rollback. No environment secrets were read, no backend was started/restarted, and the existing health endpoint remained UP. Owner credentials were supplied through process stdin and were not saved to source/evidence files.
