# SafePay backend: Oracle + UI

The default profile is `oracle`. Connection URL, username and password are read
from `src/main/resources/application.properties`. Keep your local credentials
private. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` can override them.

## Start from Command Prompt

```cmd
cd /d C:\Shreya\Safe-Pay\Backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=oracle
```

Or use `C:\Shreya\Safe-Pay\start-backend.cmd` for the supplied JAR. That launcher
loads the external source properties file. The supplied JAR contains credential
placeholders, not your database password.

In another terminal:

```cmd
cd /d C:\Shreya\Safe-Pay\Frontend\SafePayJet
npx ojet serve --server-port=8000
```

Open http://localhost:8000/login and use your existing Oracle email/password.
Use `localhost` consistently; CORS allows the UI on port 8000 with session cookies.
The UI obtains and sends CSRF tokens automatically.

## Data and API compatibility

- Oracle startup validates the schema; it does not recreate tables.
- Existing users may own multiple accounts. Registration still creates one account.
- GET `/api/accounts` returns all accounts owned by the signed-in customer.
- GET `/api/accounts/current` returns the lowest-ID owned account by default;
  `?accountId=...` selects another owned account.
- GET `/api/beneficiaries?accountId=...` filters the selected account's beneficiaries.
- POST `/api/beneficiaries` accepts optional `accountId`; omission retains the
  default-account behavior. The session determines the owner.
- Payment `fromAccountId` must belong to the caller and match the beneficiary's account.
- Existing transaction history and saved risk decisions are retained.

See [Oracle setup and migration](../docs/ORACLE_UI_SETUP.md). Do not run the fresh
schema script or historical migration 08/10/11 over the migrated database.

## Explicit disposable H2 profile

```cmd
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

This is an independent in-memory database, reset when stopped. It is not Oracle.

## Tests

```cmd
.\mvnw.cmd test
```

Opt-in Oracle checks use schema validation and disable the scheduler. The UI
round-trip test rolls back temporary test records; Oracle sequence gaps remain.
See the setup report for the validation actually performed.
