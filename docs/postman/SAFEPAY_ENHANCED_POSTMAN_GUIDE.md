# SafePay enhanced backend — Postman guide

SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.

Examples below match the delivered backend in C:\Shreya\Safe-Pay. No API requests were sent while preparing these files.

## 1. Start the backend in Command Prompt

For a fresh local demo, use these commands. The example administrator password is a demo value you explicitly set; it is not a built-in password. Restarting the local H2 profile resets its in-memory users and payments.

```cmd
cd /d C:\Shreya\Safe-Pay\Backend
set "SAFEPAY_DEMO_ADMIN_EMAIL=admin@safepay.local"
set "SAFEPAY_DEMO_ADMIN_PASSWORD=AdminDemo#2026"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

If the backend is already running with an administrator, use its actual credentials instead. Only the backend is needed for Postman; frontend port 8000 is unrelated to these API calls.

**Base URL:** http://localhost:8080

## 2. Import the prepared files

Import both files into Postman:

- SafePay_Enhanced_Examples.postman_collection.json
- SafePay_Enhanced_Local.postman_environment.json

Select **SafePay Enhanced Local Examples** as the active environment. Set adminPassword to the value used at startup (AdminDemo#2026 for the example above). The customer example uses shreya.postman@example.com / CustomerDemo#2026 and phone 9876501234. Change the email and phone if already registered.

The collection contains 53 requests in five folders. Run **01 - Registration and admin funding**, then **02 - Amount-only payments, verification and cancellation** in their listed order using fresh customer data. Run folder 02 without an added request delay so the MEDIUM cancellation occurs within 10 seconds. The other folders provide profile/beneficiary, administration/report and automatic-settlement examples. Folder 05 needs repeated polling until SETTLED before its final balance check; do not run that final check immediately after creation.

IDs and CSRF tokens are saved from responses. Do not assume a specific userId, accountId, beneficiaryId or transactionId. Generated idempotency keys are retained so resending a request tests the same operation. Clear only the relevant key variable when intentionally starting a new operation. For another fresh walkthrough, use fresh customer details and clear the saved IDs and operation keys. The amount-only policy applies only to new payments: replaying an old key returns its previously saved decision, even if that decision used the historical contextual engine. This guide does not migrate or rescore old transactions.

### Authentication and headers

Set Postman authorization to **No Auth**. Login establishes a **JSESSIONID cookie**; let Postman retain and send its cookies. Keep the same hostname throughout (localhost).

After customer login, send GET /api/accounts/current. After administrator login, send GET /api/admin/accounts. Copy the response header X-CSRF-TOKEN into the csrfToken environment variable when working manually; the supplied collection does this automatically.

For authenticated POST, PUT, PATCH and DELETE requests:

```text
X-CSRF-TOKEN: {{csrfToken}}
```

For JSON request bodies also use:

```text
Content-Type: application/json
```

Initiate, cancel and verify additionally require:

```text
Idempotency-Key: a-distinct-key-for-this-operation
```

Reuse the same key and request body after a timeout or retry. Use separate keys for initiation, cancellation and verification. Registration/login do not require CSRF. Logout does when authenticated and returns 200 on success. The cookie jar is shared for the host: switching from customer to admin replaces the active login. Log out, log in as the needed role, then get a new CSRF token.

## 3. Worked amount-only payment example

### Policy and expected balances

| Amount | Tier | Initial result |
|---|---|---|
| > 0 through INR 10000.00 | LOW | SETTLED immediately |
| > INR 10000.00 through INR 50000.00 | MEDIUM | PROTECTED for 10 seconds |
| > INR 50000.00 through INR 100000.00 | HIGH | PROTECTED for 60 seconds |
| > INR 100000.00 | VERY_HIGH | HARD_HOLD; password verification required |

New beneficiaries, first payments, history, device and context do not change these
ranges. Every new response persists an amount-range explanation. VERY_HIGH has no
automatic expiry. Expected balances below assume a fresh walkthrough without other
payments or administrator balance edits.

### A. Register a customer — 201

POST http://localhost:8080/api/auth/register

```json
{"name":"Shreya Postman Demo","email":"shreya.postman@example.com","phone":"9876501234","password":"CustomerDemo#2026"}
```

### B. Log in as customer — 200

POST http://localhost:8080/api/auth/login

```json
{"email":"shreya.postman@example.com","password":"CustomerDemo#2026"}
```

GET http://localhost:8080/api/accounts/current returns accountId, balance and account
number; its response includes the CSRF header. The initial INR 5000 is all required
minimum balance, so the account needs simulated funding before sending money.

### C. Fund the account as administrator — 200

POST /api/auth/logout with customer CSRF. POST http://localhost:8080/api/auth/login:

```json
{"email":"admin@safepay.local","password":"AdminDemo#2026"}
```

GET http://localhost:8080/api/admin/accounts to capture administrator CSRF, then
PUT http://localhost:8080/api/admin/accounts/{{accountId}}:

```json
{"balance":"1000000.00","accountType":"SAVINGS"}
```

This sets the absolute simulated balance to INR 1000000. It must cover pending
holds plus the INR 5000 minimum. Log out as admin, log back in as customer, then GET
the current account to capture customer CSRF.

### D. Add a beneficiary — 201

POST http://localhost:8080/api/beneficiaries

```json
{"beneficiaryName":"Demo Recipient","bankAccountNumber":"123456789012","ifsc":"SBIN0001234"}
```

Save beneficiaryId. Do not submit userId/fromAccountId to this endpoint; ownership
comes from the session.

### E. LOW: the first INR 5000 payment settles immediately — 200

POST http://localhost:8080/api/transactions

Headers: customer CSRF, JSON content type, Idempotency-Key: {{lowInitiateKey}}

```json
{
  "fromAccountId": {{accountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": "5000.00",
  "purpose": "Amount-only low example"
}
```

Postman substitutes the double-brace IDs before sending JSON. Its pre-request
script creates lowInitiateKey once. Expected result: LOW / SETTLED,
protectionSeconds 0, authenticationRequired false. Save **lowTransactionId**.
Balance becomes **995000** even for a new customer/new beneficiary. This payment
does not require verification and cannot be cancelled.

### F. VERY_HIGH: INR 150000 enters HARD_HOLD — 200

POST http://localhost:8080/api/transactions with a separate
Idempotency-Key: {{hardHoldInitiateKey}}:

```json
{
  "fromAccountId": {{accountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": "150000.00",
  "purpose": "Amount-only authentication example"
}
```

Expected result: VERY_HIGH / HARD_HOLD, protectionSeconds 0,
authenticationRequired true and no expiry timer. Save **transactionId** for this
payment, keeping lowTransactionId separate. Balance remains **995000**, with
**150000 reserved**.

POST http://localhost:8080/api/transactions/{{transactionId}}/verify with customer
CSRF, JSON content type and Idempotency-Key: {{verificationKey}}:

```json
{"password":"CustomerDemo#2026"}
```

The collection first supplies one deliberately incorrect password: expect 403,
the hold remains, and balance remains 995000. Five failed attempts cause the
existing 15-minute account lock; run the wrong-password example once only. The
correct password settles the payment and leaves **845000**. Retrying the same
successful verification key/password returns SETTLED without another debit.

### G. MEDIUM: INR 20000 is cancellable for 10 seconds

POST http://localhost:8080/api/transactions with
Idempotency-Key: {{mediumInitiateKey}}:

```json
{
  "fromAccountId": {{accountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": "20000.00",
  "purpose": "Amount-only cancellation example"
}
```

Expected result: MEDIUM / PROTECTED, protectionSeconds 10. Save
**protectedTransactionId**. Immediately POST
http://localhost:8080/api/transactions/{{protectedTransactionId}}/cancel with
customer CSRF and Idempotency-Key: {{cancellationKey}}. No body is needed. Prepare
the cancellation tab before manual initiation or run folder 02 without delay.

Before expiry, response is 200 / CANCELLED and balance stays **845000**. A retry
with the same cancellation key does not add an audit or change money. If the
10-second deadline has already passed, cancellation is rejected; create a new
MEDIUM payment/key to repeat the cancellation scenario and adjust the balance
expectations for any payment that settled.

### H. HIGH: INR 60000 automatically settles after its 60-second hold

Folder 05 creates a new INR **60000.00** payment using **autoInitiateKey** and saves
**autoTransactionId**. Expected initial result: HIGH / PROTECTED,
protectionSeconds 60. Poll GET /api/transactions/{{autoTransactionId}} every five
seconds until SETTLED, after the deadline and next scheduler pass. Then run the
final account check: balance is **785000**. The collection does not fast-forward
the timer or automatically loop its polling request; send that request repeatedly
before the final check. HARD_HOLD never auto-settles.

## 4. Complete usable endpoint reference

Prefix every path below with http://localhost:8080. JSON bodies are shown inline; protected writes need CSRF as above.

| Method | Path | Role | Example body / purpose |
|---|---|---|---|
| POST | /api/auth/register | Public | name, email, phone, password as above |
| POST | /api/auth/login | Public | `{"email":"shreya.postman@example.com","password":"CustomerDemo#2026"}` |
| POST | /api/auth/logout | Current session | No body; success 200 |
| GET | /api/accounts/current | CUSTOMER | Current account and CSRF |
| GET | /api/users/current | CUSTOMER | Own profile |
| GET | /api/users/{{userId}} | CUSTOMER | Own ID only |
| PUT | /api/users/current | CUSTOMER | `{"name":"Shreya Updated","email":"shreya.postman@example.com","phone":"9876501234"}`; optional password |
| POST | /api/beneficiaries | CUSTOMER | `{"beneficiaryName":"Demo Recipient","bankAccountNumber":"123456789012","ifsc":"SBIN0001234"}` |
| GET | /api/beneficiaries | CUSTOMER | ACTIVE records; add `?includeInactive=true` for all own records |
| GET | /api/beneficiaries/{{beneficiaryId}} | CUSTOMER | Detail, including owned inactive records |
| DELETE | /api/beneficiaries/{{beneficiaryId}} | CUSTOMER | No body; soft-deactivate, success 204 |
| PATCH | /api/beneficiaries/{{beneficiaryId}}/status | CUSTOMER | `{"status":"ACTIVE"}` or `{"status":"INACTIVE"}` |
| POST | /api/transactions | CUSTOMER | Payment example above; initiation key required |
| GET | /api/transactions | CUSTOMER | Own history; optional `?state=PROTECTED` |
| GET | /api/transactions/{{transactionId}} | CUSTOMER | Current server state and timestamps |
| POST | /api/transactions/{{transactionId}}/cancel | CUSTOMER | No body; cancellation key; unexpired PROTECTED only |
| POST | /api/transactions/{{transactionId}}/verify | CUSTOMER | `{"password":"CustomerDemo#2026"}`; verification key; authentication-required HARD_HOLD only |
| GET | /api/admin/users | ADMIN | User list and CSRF |
| PATCH | /api/admin/users/{{userId}}/status | ADMIN | `{"status":"SUSPENDED"}` or `{"status":"ACTIVE"}` |
| GET | /api/admin/accounts | ADMIN | Account list and CSRF |
| PUT | /api/admin/accounts/{{accountId}} | ADMIN | `{"balance":"1000000.00","accountType":"CURRENT"}`; SAVINGS also valid |
| PATCH | /api/admin/accounts/{{accountId}}/status | ADMIN | `{"status":"BLOCKED"}` or `{"status":"ACTIVE"}` |
| GET | /api/admin/reports/transactions/summary | ADMIN | Summary counts and amounts |
| GET | /api/admin/reports/transactions/daily?from=2026-09-01&to=2026-09-30 | ADMIN | Inclusive dates; maximum 366 days |

Transaction filters accept CREATED, AUTHORIZED, RISK_ASSESSED, PROTECTED, HARD_HOLD, CANCELLED and SETTLED. Use `state`, not `status`. Initiation uses /api/transactions, not /api/transactions/initiate. Legacy generic user CRUD routes are intentionally blocked; no /api/health endpoint is implemented.

## 5. Equivalent curl commands in Command Prompt

The examples below log in as an already registered customer and use a separate curl cookie file. Postman's cookie jar is separate.

```cmd
curl.exe -i -c customer.cookies -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"shreya.postman@example.com\",\"password\":\"CustomerDemo#2026\"}"
curl.exe -i -b customer.cookies -c customer.cookies http://localhost:8080/api/accounts/current
```

Copy X-CSRF-TOKEN from that GET response:

```cmd
set "CSRF_TOKEN=paste-response-header-token-here"
curl.exe -i -b customer.cookies -c customer.cookies -X POST http://localhost:8080/api/beneficiaries -H "Content-Type: application/json" -H "X-CSRF-TOKEN: %CSRF_TOKEN%" -d "{\"beneficiaryName\":\"Curl Recipient\",\"bankAccountNumber\":\"987654321012\",\"ifsc\":\"SBIN0001234\"}"
curl.exe -i -b customer.cookies http://localhost:8080/api/transactions
curl.exe -i -b customer.cookies -c customer.cookies -X POST http://localhost:8080/api/auth/logout -H "X-CSRF-TOKEN: %CSRF_TOKEN%"
```

## 6. Reading failures

- 401: login/session cookie missing or expired.
- 403: wrong role or missing/stale CSRF. Verification can also return 403 for a wrong password or locked/inactive user.
- 404: record missing or belongs to another customer.
- 409: duplicate email/phone/beneficiary, changed payload with an already used key, invalid verification state, or an admin balance that cannot cover holds plus the minimum.
- 400: invalid fields, missing Idempotency-Key, insufficient transfer balance, or attempting to cancel an expired/nonprotected payment.
- 503 on reports: reporting views unavailable. The local profile creates them; Oracle needs the supplied reporting SQL installed.

Preparation validation: both JSON files parse; all 53 requests have API URLs; embedded request scripts compile; all JSON bodies parse after variable substitution. This does not claim the collection was executed in Postman. The earlier test report describes the historical contextual-policy run; it is not evidence that these revised amount-only examples were executed. See the current delivery validation results for separately executed backend tests.
