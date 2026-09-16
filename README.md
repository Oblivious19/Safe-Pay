# SafePay — enhanced Shreya edition

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

This complete backend/frontend project starts from Safe-Pay-shreya-v1. Sahu and Ruchi source archives were left untouched.
The approved additions preserve session/CSRF authentication, one account on registration, existing endpoints and original live transaction states. On 15 September 2026, the user explicitly changed new-payment risk assessment to the amount-only policy below.

## Start the Oracle-backed UI (16 September 2026)

The default profile is now `oracle`. The backend uses the connection details in
`Backend/src/main/resources/application.properties`; `local` is an explicit,
disposable H2 option. Existing Oracle users and all their accounts are supported.
Use the account selectors on the dashboard, payment and beneficiary pages.

From Command Prompt, open two terminals:

```cmd
cd /d C:\Shreya\Safe-Pay
start-backend.cmd
```

```cmd
cd /d C:\Shreya\Safe-Pay
start-ui.cmd
```

Open http://localhost:8000/login and sign in with your existing Oracle email or mobile number
and password. The packaged launcher reads the external properties file, so it
uses your local database credentials without embedding them in the supplied JAR.
See [Oracle migration and UI setup](docs/ORACLE_UI_SETUP.md) for validation,
manual commands and backup details. This section supersedes the original setup
notes below. Do not rerun an already-applied migration.

## Updated ZIP enhancements (16 September 2026)

The approved ZIP improvements are merged: mobile/password login alongside email,
administrator details/provisioning/status audit and retry-safe simulated credits,
payment review with inline cancellation/countdown, and beneficiary validation and
responsive layouts. Existing multi-account support and amount-only risk remain.
See [module-by-module ZIP review](docs/UPDATED_ZIP_REVIEW.md) and
[new API examples](Backend/updated-zip-requests.http). Oracle migration 15 adds the
credit retry key; it has already been applied to this machine's configured schema.

## Current risk policy

| Validated payment amount | Risk tier | Initial result |
|---|---|---|
| Above zero through ₹10,000 | LOW | SETTLED immediately |
| Above ₹10,000 through ₹50,000 | MEDIUM | PROTECTED; cancellable for 10 seconds |
| Above ₹50,000 through ₹1,00,000 | HIGH | PROTECTED; cancellable for 60 seconds |
| Above ₹1,00,000 | VERY_HIGH | HARD_HOLD; server-side password verification required |

Beneficiary age, first-payment status, history, device and context do not alter these ranges. Every new decision saves a plain-language amount-range reason. Existing transactions and idempotency replays retain their saved decisions and deadlines; this change does not rewrite stored data. VERY_HIGH has no automatic timer release.

## What was added

- Registration page and administrator navigation.
- Administrator user/account status screens, account list and exact-decimal balance/type editing.
- Summary/daily report screens backed by the existing reporting APIs.
- Customer profile editing; beneficiary detail, removal and reactivation.
- Pending-payment polling and explicit hard-hold verification using the current customer's password.
- Persistent cancellation/verification retry keys, database deadline checks, account locking, reservation-aware balance edits and one database transaction per scheduled settlement.
- Additional unit, security and database integration tests; fresh Oracle schema and incremental safeguards migration.

See [module assessment](docs/BEST_MODULES_AND_CHANGES.md), [API examples](Backend/enhanced-requests.http), and [validation results](docs/VALIDATION.md).
The [original comparison](docs/SafePay_Three_Version_Module_Comparison.md) records all 24 functional areas before these additions. Older specifications and historical migration notes are retained for reference; this guide describes the delivered configuration.
See [changed files](docs/CHANGE_MANIFEST.md) for the full file inventory against Shreya v1.

## Requirements

- Java 17 and Maven (the Maven wrapper is included).
- Node.js supported by Oracle JET 21 and npm.
- Oracle for the oracle profile, or the included H2 local simulator.
- Backend port 8080; Oracle JET port 8000.

## Run locally in PowerShell

From C:\Shreya\Safe-Pay\Backend:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:SAFEPAY_DEMO_ADMIN_EMAIL = 'admin@safepay.local'
$env:SAFEPAY_DEMO_ADMIN_PASSWORD = '<choose a local demo password>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

The local profile uses H2 in Oracle compatibility mode. It creates the schema, CUSTOMER/ADMIN roles, account-number sequence and reporting views.
The optional administrator is created only when SAFEPAY_DEMO_ADMIN_PASSWORD is supplied; there is no hardcoded administrator password.
The local database is in memory: restarting the backend resets local demo records. Oracle data is not used by this profile.

The packaged backend is also included in dist/SafePay.jar. From the delivery root, run `java -jar dist/SafePay.jar --spring.profiles.active=local` with the same optional administrator environment variables.

In another PowerShell terminal, from C:\Shreya\Safe-Pay\Frontend\SafePayJet:

```powershell
npm ci
npm run build
npx ojet serve --server-port=8000
```

Open http://localhost:8000/login. Register a customer, sign in as the administrator to set that customer's simulated balance, then sign in as the customer to send money. Newly registered accounts start at the ₹5,000 minimum and need funding before they can send a payment.

The frontend dependencies are installed in this delivered project. The current full JET build, TypeScript check and 107 frontend tests passed; see docs/ORACLE_UI_SETUP.md. The package lock remains the source for reproducible dependency installation.

## Oracle setup

The existing local Oracle schema was migrated on 16 September 2026 with the user's
approval. All existing users, accounts, beneficiaries, payments and audit rows
were preserved. The one-time script is `Database/14_existing_oracle_ui_migration.sql`;
its backup tables begin `SPBK_20260916_`. Reporting views are installed.
**Do not rerun the migration on this database.**

The default Oracle profile reads `Backend/src/main/resources/application.properties`
and validates the schema at startup. It does not erase or recreate the tables.
Use the launchers above or the exact commands in `docs/ORACLE_UI_SETUP.md`.
Existing customers sign in with their original email (including its case) and
password. Each migrated account is available in the UI account selectors.

For a separate, empty Oracle schema only, use `00_enhanced_fresh_schema.sql` followed
by `12_admin_reporting_views.sql`. Do not combine them with historical creation or
migration scripts. Registration creates CUSTOMER users; administrator access is
assigned deliberately by the database owner, never through public registration.

## Manual verification

1. Register and log in; confirm one numbered SAVINGS account with ₹5,000.
2. Log in as ADMIN. Set the customer's absolute simulated balance to ₹10,00,000. Verify user suspension and account blocking through their original endpoints, then restore ACTIVE status.
3. Log back in as CUSTOMER. Add a new beneficiary and send ₹5,000 with a new initiation key. Even this first payment is LOW/SETTLED immediately; balance becomes ₹9,95,000.
4. Send ₹1,50,000 with another initiation key. It is VERY_HIGH/HARD_HOLD; balance remains ₹9,95,000 with ₹1,50,000 reserved. A wrong verification password must leave it held. The correct password settles it once, leaving ₹8,45,000; retrying the same verification key/password must not debit again.
5. Send ₹20,000 with another initiation key: MEDIUM/PROTECTED, 10 seconds. Cancel immediately before its database deadline. Balance stays ₹8,45,000; retrying cancellation must not credit or debit again.
6. Send ₹60,000 with another initiation key: HIGH/PROTECTED, 60 seconds. Leave it uncancelled and poll its server state. After the deadline and the next scheduler pass, it settles and balance becomes ₹7,85,000.
7. Remove/reactivate the beneficiary; default beneficiary lists and send-money selectors must contain ACTIVE beneficiaries only. Edit the profile and confirm session ownership continues after an email change.
8. In Administration, verify reports and confirm balance edits cannot consume money reserved for held payments. Expected balances above assume a fresh walkthrough without other payments or balance edits.

## Tests

```powershell
# Backend
.\mvnw.cmd test
# Frontend (after npm ci)
npm test
npm run typecheck
npm run build
```

See docs/VALIDATION.md for checks actually executed in this delivery, including environment limitations.


