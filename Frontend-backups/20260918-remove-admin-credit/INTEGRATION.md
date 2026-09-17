# Latest frontend integrated with the existing SafePay backend

## Run

Use two Command Prompt windows:

    cd /d C:\Shreya\Safe-Pay
    start-backend.cmd

    cd /d C:\Shreya\Safe-Pay
    start-ui.cmd

Open **http://localhost:8000/**. Use localhost, because the existing backend allows that browser origin. Keep the backend on port 8080. The AI integration is independent and does not need to run.

If dependencies need restoring on another computer:

    cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
    npm ci
    npx ojet restore
    npx ojet serve --server-port=8000

Sign in with existing database credentials using mobile/password or email/password. Admin accounts go to /admin/dashboard. There are no browser-only demo logins.

## Connected modules

| Screen | Existing backend contract |
|---|---|
| Registration and login | POST /api/auth/register and /api/auth/login |
| Logout | POST /api/auth/logout with session CSRF |
| Dashboard and account selectors | GET /api/accounts |
| Beneficiaries | GET/POST /api/beneficiaries, accountId scope; GET/DELETE /{id}; PATCH /{id}/status |
| Send money | POST /api/transactions, explicit source account and Idempotency-Key |
| Payment status and history | GET /api/transactions and /api/transactions/{id} |
| Cancellation | POST /api/transactions/{id}/cancel with Idempotency-Key |
| Profile | GET /api/users/current and GET /api/accounts |
| Admin reports | GET /api/admin/reports/transactions/summary and /daily |
| Admin users and accounts | GET /api/admin/users and /api/admin/users/{id}/accounts |
| Interest credits | POST /api/admin/accounts/{id}/interest-credits with Idempotency-Key |
| Admin approvals | GET /api/admin/transactions/hard-holds; POST /api/admin/transactions/{id}/approve |

All account options come from the backend. Switching the source account clears the selected recipient and shows only that account's active beneficiaries. Transaction history includes the customer's accounts.

The new UI's layout, colours, payment sheets, charts, responsive screens and public home demonstration are retained. The public demonstration is explicitly labelled and never creates a payment.

## Adaptations to preserve backend behaviour

- Browser demo accounts/ledgers are disconnected from the live application. Source examples and original ZIP tests are retained under reference-demo for historical reference only; they are not imported by the application or run as current tests.
- Login and registration use the backend's password flows. The ZIP's simulated KYC, payment PIN and OTP verification call are not presented as real features.
- HARD_HOLD remains pending until an administrator explicitly reviews, confirms, and approves it. Customer pages show pending-admin status and refresh automatically. No approval/rejection rules were changed in the backend.
- The ZIP's send-code/reject actions do not correspond to backend endpoints and are not exposed. The admin screen uses the actual approval endpoint.
- Admin credits and approvals retain the same operation key after an uncertain response; retry uses the original request.
- Countdown and cancel eligibility use protectionRemainingMillis and canCancel from the server. A zero countdown never settles a transaction locally.
- The backend does not provide an all-customer detailed transaction ledger or a full four-tier breakdown. The demo-only panels are replaced by supported summary reports, high-risk totals, the real user directory and held-payment queue; empty mock arrays are not presented as actual records.
- No backend, Oracle schema/data, or AI integration changes are included.

## Validation — 17 September 2026

- TypeScript check passed.
- Oracle JET build passed; web contains the compiled UI.
- 198 regression tests passed, including real service modules with mocked transport, CSRF, payload whitelisting, account isolation, idempotent retries, admin confirmation and server-derived countdowns.
- Hidden Edge browser tested the UI against your packaged SafePay backend, using a separate in-memory H2 database and synthetic accounts. Verified: login/session/CORS, both customer accounts, beneficiary creation and isolation, INR 120000 HARD_HOLD, admin approval and settlement, second-account credits, registration, server countdown, protected cancellation, INR 5000 immediate settlement, profile and mobile viewport.
- No uncaught browser exceptions occurred in those flows. Screenshots were inspected at desktop and 390px mobile widths.
- Live Oracle records were not used for the browser tests. Use your existing credentials for the final Oracle-backed check.

The existing Backend/target/classes contained older single-account compiled output during this integration. Browser tests used the newer dist/SafePay.jar, which includes multi-account and admin approval APIs. start-backend.cmd already runs that packaged backend. A normal Maven rebuild uses the current source if you prefer running from source.

## Development checks

    npm run typecheck
    npm test
    npm run build

Backend identity, ownership, balance reservations, risk decisions and settlement remain authoritative. The UI never automatically retries a payment or decides a risk tier.
## Previous frontend backup

The previous Frontend/SafePayJet folder is preserved at:

    C:\Shreya\Safe-Pay\Frontend-backups\20260917-integrated-ui\SafePayJet

