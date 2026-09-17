# SafePay frontend API services

The Oracle JET UI uses `http://localhost:8080` by default. Run it at
`http://localhost:8000`. Backend CORS permits this exact origin, credentials and
`X-CSRF-TOKEN`. `configureApi()` may set a deliberate alternative API base URL.

## Authentication and transport

`authService` implements registration, login and logout. Every fetch includes
session cookies. The client obtains `X-CSRF-TOKEN` from a protected GET and sends
it on authenticated writes. Login and registration use their existing exemptions.
The browser owns JSESSIONID; passwords are never stored in the client. A 401
clears session state; a 403 refreshes token state without automatically replaying
payments. The API base must use the same hostname consistently.

## Multiple accounts

- `accountService.list()` calls GET `/api/accounts` for all owned accounts.
- `accountService.getCurrent(accountId?)` calls GET `/api/accounts/current`, with
  an optional account selector. Omission selects the lowest-ID owned account.
- `getAccounts()` returns that owned list. Legacy email parameters remain ignored.
- `getBeneficiaries(undefined, includeInactive, accountId)` selects an owned
  account. Without accountId, the API retains its all-owned-beneficiaries behavior.
- `addBeneficiary(accountId, legacyEmail, input)` sends the account ID as a resource
  selector. The backend derives identity from the session and verifies ownership.
- Beneficiary responses include `accountId`; the payment screen only offers active
  beneficiaries associated with the selected source account.

Dashboard, payment and beneficiary screens expose account selection. Account
changes clear obsolete beneficiary choices and ignore late responses from the
previous selection. New registrations still receive one account.

## Payments and other modules

`transactionService` supports list, get, create, cancel and password verification.
It sends validated decimal text for payment amounts and explicit retry keys.
Existing `api.ts` adapters retain a logical operation's key after uncertain
failures. Keys are cleared across sessions. The client never evaluates risk or
changes payment states locally; it polls the server every five seconds while a
pending-payment screen is open.

Customer profile, beneficiary detail/removal/reactivation and administrator
user/account/report services remain available. Ownership, status transitions,
minimum balance and hold reservations are checked by the backend and database.

## Verification

```cmd
npm run typecheck
npm test
npm run build
```

Tests execute the actual TypeScript modules with mocked transport. Oracle-backed
request tests live in the backend. See `docs/ORACLE_UI_SETUP.md` at the project
root for the integration validation and startup commands.
