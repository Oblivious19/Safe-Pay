# Updated Shreya ZIP review — 16 September 2026

Source: Safe-Pay-shreya-v1.zip, SHA-256
`A42D2E3BC55C1B3337A7A02E64C08210063868561BBD44C30F0A9BCD9AD857BB`.
This differs from the earlier archive used for the original delivery.
The current project in C:\Shreya\Safe-Pay remains the merge base.

The user approved the overlapping enhancements listed below. The ZIP was reviewed
as source material; its historical risk, database and one-account assumptions do
not replace the user's current requirements.

## Module decisions

| Module | Decision |
|---|---|
| Email login | Preserve existing passwords, session cookies and CSRF; retain email login |
| Mobile login | Add mobile/password with the same persisted lockout as email/password |
| PIN and demonstration KYC | Excluded as agreed; no OTP, PAN, DOB or address collection added |
| Registration | Preserve one account per registration and CUSTOMER-only public sign-up |
| Multiple accounts | Preserve all migrated accounts and all account selectors |
| Administrator users | Add details, all-account lookup, provisioning and audited status actions |
| Administrator credits | Add a simulated additive credit with persistent retry protection |
| Existing admin account editing | Preserve absolute balance/type/status management and hold reservations |
| Sessions after user-status changes | Check current database status/role on each protected request; revoke stale sessions |
| Payment initiation | Add a review step and freeze the attempted request after an uncertain response |
| Cancellation display | Add inline cancellation and estimated countdown using database-derived remaining time |
| Risk engine | Keep current amount-only ranges; incoming contextual scoring is not adopted |
| Cancellation backend | Keep current database deadline checks and persisted retry keys |
| Scheduler | Keep current account locking and separate database transaction per settlement |
| Hard holds | Keep existing server-password verification and reserved funds |
| Beneficiaries | Improve field validation and responsive presentation; keep account scoping, details, removal and reactivation |
| Payment history | Keep filters, polling, stale-response protection and all-owned-account history |
| Customer profile | Preserve session-protected editing, absent from the incoming ZIP |
| Reports | Keep existing report APIs/views; incoming backend report implementation was unchanged |
| Database | Keep migrated Oracle settings/data; add only nullable AUDIT_LOG.REQUEST_KEY plus uniqueness for credit retries |
| Public welcome / staged onboarding | Not required for the approved functionality; retain existing routes and registration flow |

## Targeted optimizations

- Load administrator list roles together with users to avoid repeated lazy role queries.
- Verify session status/role with one small database projection, without fetching credentials.
- Read database time once for a protected-payment response/list, not once per payment.
- Retain bounded UI polling, ignore obsolete responses and prevent duplicate form submissions.
- Reuse the existing audit table for credit receipts and retry keys; do not add a new ledger or receipt table.

No benchmark speedup is claimed. These changes reduce avoidable work while keeping
server-side ownership, balance and state checks in place.

## Compatibility boundaries

No existing Oracle user, password, account balance or saved risk decision is
rewritten by the feature merge. No administrator is automatically provisioned or
promoted. Credits happen only through an explicit authenticated ADMIN request.
The password supplied for a new administrator is an initial password; no expiry
or forced password-change flow is claimed.

The original comparison report and migration 14 remain historical records. Never
apply the incoming ZIP's older schema/risk scripts over the migrated database.

## Oracle migration and data verification

`Database/15_admin_credit_request_key.sql` was applied successfully on 16 September
2026 to the Oracle schema configured by the delivered application.properties.
It adds only a nullable, unique AUDIT_LOG.REQUEST_KEY. Existing audit rows keep a
null key. The live schema was validated by Hibernate and the opt-in Oracle tests.
Do not rerun the older database migrations or the incoming ZIP's schema scripts.
For a different existing database, apply migration 15 after the earlier supported
schema migrations before starting the updated backend. Fresh schema script 00
already includes this column.

All original user, account, beneficiary, transaction and audit data matched the
pre-migration snapshots after validation. The new Oracle test records, credits,
status changes and temporary administrators were rolled back. No existing user
was granted administrator access. Sequence numbers may have gaps after tests.
The real connection settings remain in the delivered external properties file;
the packaged JAR contains environment placeholders instead of credentials.

## New API checks in Postman

See `Backend/updated-zip-requests.http` for complete example requests. Use
`http://localhost:8080` and retain the JSESSIONID cookie returned by login.
After logging in, send a protected GET and copy its `X-CSRF-TOKEN` response header
into that header on subsequent POST/PATCH/PUT/DELETE requests. ADMIN actions need
an existing ADMIN login; a CUSTOMER session is not sufficient. Browser and Postman
cookie jars are separate, so sign in separately when switching clients.

- POST `/api/auth/login`: exactly email/password OR phone/password; no PIN.
- GET `/api/admin/users/{id}`: safe user details.
- GET `/api/admin/users/{id}/accounts`: every account owned by that user.
- POST `/api/admin/users`: name, email, phone, initialPassword; creates an ADMIN
  without a customer bank account. No automatic expiry or forced password change.
- PATCH `/api/admin/users/{id}/status`: audited ACTIVE/LOCKED/SUSPENDED/INACTIVE
  transition. Reactivation resets persisted failed-login counters/lock deadlines.
  Existing sessions lose access on the next protected request after revocation.
- POST `/api/admin/accounts/{id}/interest-credits`: `{ "amount": "1000.03" }` plus
  a unique `Idempotency-Key`. This adds money to the simulated ledger only.
  Retry with the exact same key, account and amount if the response is uncertain.
  A successful replay returns the original receipt, even if the current balance
  has since changed. A changed request with the old key is rejected.

Use the internal accountId from an account response in these URLs, not the account
number displayed to a customer. Existing account edit/status/report APIs remain.
Transaction responses add `protectionRemainingMillis` and `canCancel`. The UI
countdown is an estimate based on database time; server deadline/state validation
always decides whether a cancellation is still allowed.

## Manual UI verification

1. Start `start-backend.cmd` and `start-ui.cmd` from C:\Shreya\Safe-Pay in separate
   terminals, then open http://localhost:8000/login.
2. Sign in using your existing email/password, then sign out and try the registered
   mobile number with the same password. Confirm all owned accounts remain usable.
3. Add/select a beneficiary, review a payment, and verify account, recipient and
   amount before sending. MEDIUM/HIGH payments offer inline cancellation and an
   estimated countdown. HARD_HOLD still requires the current password.
4. With an ADMIN session, inspect user details and all their accounts. Create another
   administrator or change status only when intended. A simulated credit requires
   explicit confirmation; retrying an uncertain result keeps the same operation.

## Runtime validation limit

Backend API integration tests use MockMvc with real H2/Oracle persistence.
The JET production build and source-level UI tests run without a live browser.
A previous server launch from this agent's Windows host failed while Tomcat opened
its Java NIO selector (`SocketException: Invalid argument: connect`). A follow-up
host-specific startup action was declined, so it was not retried for this merge.
Live HTTP/browser startup remains to be checked from your own terminal using the
launchers above. This limitation does not affect the completed database checks.

## Final validation for this merge

- **494 backend tests passed, zero failures/errors/skips** across the regular
  package run and explicitly enabled Oracle tests. This includes shared login
  lockout, stale-session revocation, risk boundaries, multi-account isolation,
  database-time cancellation, audit rollback and concurrent credit idempotency.
- **135 frontend tests passed**, including payment review, replacement-view retry,
  late responses across session changes, credit replay, cancellation refresh and
  beneficiary validation. TypeScript type checking passed.
- Full Oracle JET build passed using the existing dependency versions; no package
  upgrade was required. Existing optional Sass/deprecation warnings remain.
- Backend JAR built successfully with credential placeholders. SHA-256:
  `4FC2414DC36B1349E0A25B1C46EF381F0DE9182A1079D011ABB507591F788008`.
- Oracle migration 15 succeeded and the subsequent original-data preservation
  comparison passed. The runtime/browser limitation described above still applies.

Payment attempts retain the exact body and retry key while moving between views
in the same running UI session. They are cleared at an authentication boundary.
A full browser reload does not retain a pending payment draft: check Transactions
before starting another payment after reloading with an uncertain result. The
separate simulated-credit retry draft uses session storage and clears on a change
of authentication. Passwords are never stored in either draft.

Changed files and their hashes are recorded in
`docs/UPDATED_ZIP_CHANGE_MANIFEST.json`. Previous versions of replaced files are
backed up under
`C:\Users\Gaurav Sahu\AppData\Local\Temp\safepay-zip-review-20260916\deployment-backup`.
The originals under C:\Safe-Pay and the source ZIP were not changed.
