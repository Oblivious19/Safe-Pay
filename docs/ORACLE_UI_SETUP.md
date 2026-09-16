# Oracle database and UI integration — 16 September 2026

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

## Completed setup

The project is `C:\Shreya\Safe-Pay`. The Oracle connection was verified using the
existing `Backend/src/main/resources/application.properties` details. That file's
URL, username and password were preserved. No credentials are printed here.

The default profile is now `oracle`. The Oracle profile no longer overrides the
properties file with unresolved environment placeholders. It uses Hibernate
`ddl-auto=validate` to protect the migrated schema on startup. Explicit `local`
continues to use a disposable in-memory H2 database.

The user chose to migrate the existing Oracle data and keep all accounts usable.
All 3 existing users, 6 accounts, 2 beneficiaries, 4 settled transactions and 7
audit rows were preserved. This includes the four accounts owned by user 103.
Existing password hashes, balances, IDs, timestamps and saved risk decisions were
checked against backups and remained unchanged. Mixed-case email addresses were
also preserved. New account numbers were added without changing internal IDs.

Migration `Database/14_existing_oracle_ui_migration.sql` and reporting-view script
`Database/12_admin_reporting_views.sql` were successfully applied. **Do not rerun
them on this database.** The migration rejects an already-migrated or unexpected
schema. Do not apply the older 08/10/11 scripts over this database.

## Start the application

Open two Command Prompt terminals. In the first:

```cmd
cd /d C:\Shreya\Safe-Pay
start-backend.cmd
```

In the second:

```cmd
cd /d C:\Shreya\Safe-Pay
start-ui.cmd
```

Open http://localhost:8000/login. Sign in separately in the browser using your
existing Oracle email and password; Postman and the browser have separate session
cookies. Use the same email spelling/case stored in Oracle. Select an account on
the dashboard, Send Money or Beneficiaries page. Only that account's beneficiaries
are offered for a payment.

The launchers stay attached to their terminals; Ctrl+C stops each service.
Backend port is 8080; UI port is 8000. Use `localhost` consistently for both.
The UI already includes session cookies, obtains CSRF tokens and sends them on
protected writes. No manual Postman headers are needed in the UI.

The packaged backend launcher reads the external properties file. The supplied
`dist/SafePay.jar` contains credential placeholders, not your local DB password.
Java 17 is required; set JAVA_HOME if Java is not available on PATH.

### Equivalent development commands

```cmd
cd /d C:\Shreya\Safe-Pay\Backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=oracle
```

```cmd
cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
npx ojet serve --server-port=8000
```

Do not use `--port=8000`. Frontend dependencies are already installed in this
Shreya folder. A separate copy under `C:\Safe-Pay` may have different source and
missing dependencies.

## Multiple-account compatibility

Registration still creates one account. Existing owners can keep and use multiple
accounts; no accounts were merged, archived or reassigned. The UI now has account
selectors and rejects stale beneficiary selections after a switch.

| Request | Behavior |
|---|---|
| GET `/api/accounts` | All accounts owned by the authenticated customer |
| GET `/api/accounts/current` | Lowest-ID owned account, preserving existing clients |
| GET `/api/accounts/current?accountId=1000004` | Selected owned account; other owners' accounts are rejected |
| GET `/api/beneficiaries?accountId=1000004` | Active beneficiaries of the selected owned account |
| GET `/api/beneficiaries?accountId=1000004&includeInactive=true` | Also includes inactive beneficiaries for management |
| POST `/api/beneficiaries` | Optional `accountId` selects an owned account; omission uses the default account |
| POST `/api/transactions` | `fromAccountId` and beneficiary must refer to the same owned account |

Beneficiary responses include `accountId`. The same recipient can be registered
separately for different accounts; duplicates within one account are rejected.
Balances and reservations are isolated by account. Existing cancellation,
verification, session/CSRF, amount risk ranges and transaction history remain.
The transaction list includes the customer's history across all owned accounts.

Legacy users are assigned CUSTOMER roles. The ADMIN role exists, but migration
does not automatically grant administrator privileges to an existing customer.
Public registration remains CUSTOMER-only.

## Backups

These Oracle tables contain every original column and row from before migration:

- `SPBK_20260916_USERS`
- `SPBK_20260916_ACCOUNT`
- `SPBK_20260916_BENEFICIARIES`
- `SPBK_20260916_TRANSACTION_DB`
- `SPBK_20260916_AUDIT_LOG`

They are data snapshots, not automatic rollback scripts or backups of constraints,
indexes and grants. Oracle DDL commits implicitly. Keep the snapshots for review;
do not drop them or blindly rerun migrations. Application source files changed by
this update were backed up in the local staging folder before deployment.

## Verification performed for the original Oracle migration

- Oracle 23 connection using the existing application properties succeeded.
- Migration preflight, one-time changes and bidirectional comparisons of every
  original data column passed. Post-test preservation checks also passed.
- Both reporting views have VALID status.
- **397 backend tests passed**, including the two explicitly enabled Oracle tests.
- The live Oracle request test covered registration, login, credentialed browser
  CORS, CSRF, account access, beneficiary creation, LOW settlement, idempotent
  replay, exact balance and reporting. Temporary records were rolled back; normal
  Oracle sequence gaps remain.
- Real multi-account H2 tests covered four accounts, ownership rejection,
  mismatched-account payment rejection, cancellation, verification and isolated
  reservations.
- **107 frontend tests passed**, TypeScript passed, and the full Oracle JET build
  succeeded. The existing compiled theme was used; Sass was not installed.

Application logic tests use the actual controllers/services and database. The
Oracle round-trip test uses MockMvc rather than logging into a user's browser.
The existing users' passwords were not requested, changed or exposed.

## Manual check

1. Start both services and sign in through the UI.
2. Confirm each owned account appears and its balance matches Oracle.
3. Select an account and add a beneficiary. Switch accounts and confirm the list
   changes; saved beneficiaries remain tied to their original accounts.
4. Make a deliberate test payment only when ready to change the simulated ledger.
   An amount up to INR 10,000 is LOW and settles immediately, subject to available
   funds and the INR 5,000 minimum.
5. Restart only the backend and sign in again: Oracle records remain persisted.

## Updated ZIP merge

See [Updated ZIP review](UPDATED_ZIP_REVIEW.md) for the latest additions, additive
Oracle migration 15, API examples and newer validation totals. The earlier counts
above document the original migration delivery.
