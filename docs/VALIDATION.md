# Delivery validation — 15 September 2026

## Latest correction: amount-only risk

The user subsequently replaced contextual scoring with fixed amount bands. The updated Java 17 backend package passed **396 tests: 395 passed, one Oracle-only skip, zero failures/errors**. See [current risk policy and verification](AMOUNT_RISK_POLICY_UPDATE.md) for boundaries, real database/HTTP tests and restart instructions. Frontend source was unchanged in this correction.

The results below record the initial enhanced delivery before that policy correction.

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

## Checks completed

| Check | Result |
|---|---|
| Java 17 Maven backend tests and executable packaging | 389 tests: 388 passed, 1 skipped, 0 failures/errors; 29 test classes |
| Complete backend workflow through Spring MockMvc | Real sessions/CSRF, registration, admin funding/type edit, held-fund balance rejection, profile email/session refresh, beneficiary removal/reactivation, password verification and cancellation retries, single debit/audit, summary/daily reporting all passed against real H2 services/repositories |
| H2 database integration | Concurrent cancellation returns fresh CANCELLED state and writes one audit; expired cancellation is rejected by the database; hard-hold password failure persists its attempt counter; successful verification/retry debits once |
| New backend unit/security checks | Verification ownership/credentials/replay, profile field restrictions, admin account reservation checks, CSRF/role access, beneficiary lifecycle, scheduler failure isolation |
| Frontend independent service tests | 40 passed: 23 API foundation, 12 customer workflow, 5 admin/session |
| Frontend strict service typing | 15 service modules, zero diagnostics using installed TypeScript 6.0.3 |
| Frontend TypeScript syntax | 29 source files, zero diagnostics; existing AMD format retained |
| Frontend view binding syntax review | 181 expressions parsed across reviewed authentication/admin/customer views; no syntax errors |
| Original source archives | All three SHA-256 fingerprints match the pre-implementation comparison |
| Oracle SQL | Static review only; no scripts executed |

At the initial delivery, Shreya's multi-signal policy was preserved, and a first INR 5,000 payment to a new beneficiary could produce VERY_HIGH/HARD_HOLD. This behavior was superseded by the amount-only correction above: new INR 5,000 payments are LOW/SETTLED.

## Environment limits

- The complete Oracle JET build, full frontend view-model test suite and browser rendering were not validated. The corporate Oracle Secure Web Gateway blocked npm registry access with HTTP 403. Genuine Knockout/JET packages were unavailable locally. The locked TypeScript 5.8.3 was also unavailable, so offline service/syntax checks used the installed 6.0.3 compiler. Framework stubs were not substituted for a full build.
- The packaged JAR was produced, but a real listening Tomcat smoke run failed during Java NIO loopback initialization on this Windows machine (`Unable to establish loopback connection`, `SocketException: Invalid argument: connect`). An IPv4 attempt had the same result. The temporary process was stopped. The complete backend request workflow subsequently passed through Spring MockMvc with real services, security and H2, without a network listener.
- The Oracle-only domain/schema test is skipped unless its documented Oracle environment is supplied. H2 passing does not validate Oracle migrations, dialect locking or deployment.

## Repeat on a configured machine

From Backend, run `mvnw.cmd test`. Use Java 17.

From Frontend/SafePayJet, run `npm ci`, `npm test`, `npm run typecheck` and `npm run build` with approved access to the locked packages.

For an optional disposable-local HTTP smoke run, start the local backend on port 18081 with SAFEPAY_DEMO_ADMIN_PASSWORD, then run `node Backend/scripts/smoke-local.cjs` from the project root with the same environment variable. This script creates demo records and edits their simulated balance; use only the disposable local/H2 profile. The script is included but its HTTP workflow could not execute on this machine because of the loopback startup error.

Use the root README manual checks and Backend/enhanced-requests.http to inspect the delivered user flows.

## Preserved boundaries

- Customer session/CSRF security, one-account model and live transaction-state meanings remain. The latest amount-only policy supersedes the initially preserved Shreya scoring.
- The account balance is an absolute simulated balance, not a deposit command; admin edits must leave all reserved funds plus INR 5,000 available.
- Existing administrative sessions retain Shreya's original session-role behavior; suspending a user does not implement a new global session-revocation system.
- No real payment rails, OTP/SMS service, AI model, corporate approval workflow or recipient ledger was added.

The delivery contains full backend/frontend source, tests, package lock, SQL, documentation and a packaged backend JAR. Dependency caches, node_modules and temporary build logs are excluded.
