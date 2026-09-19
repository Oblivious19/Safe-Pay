# SafePay — Phase 2.1 API-Test Guide

**Shared backend foundations · paired with master-guide Increment 1 · 19 September 2026**

[Master understanding guide](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/Documentation for SafePay/SafePay_Master_Backend_Understanding_Guide.md>)

**Status:** manual test instructions prepared from current source; these requests have not been run by Codex. The user-confirmed 1148-test Maven result remains separate automated evidence.

## 1. What this phase can actually test through HTTP

Phase 2.1 has **no direct business controller or standalone foundation endpoint**. We use existing later APIs to observe its correlation, validation, masking, pagination, money formatting and error contracts. This is not a new API specification and does not add test-only routes to production.

| Existing endpoint used here | Required authority | Foundation behavior observed | Detailed domain guide |
|---|---|---|---|
| POST /api/v1/auth/login | Public, valid credentials for successful login | JSON validation; tokens needed by later checks | 2.2 |
| GET /api/v1/accounts | CUSTOMER | Correlation, safe IDs and account masking | 2.3 |
| GET /api/v1/accounts/{accountId} | CUSTOMER, owned account | Type validation, ownership-hidden 404 | 2.3 |
| GET /api/v1/accounts/{accountId}/balance | CUSTOMER, owned account | Exact money strings | 2.3 |
| GET /api/v1/admin/accounts | SYSTEM_ADMIN | PagedResponse and query validation | 2.3 |
| POST /api/v1/beneficiaries | CUSTOMER | Invalid-DTO and malformed-body errors only here | 2.4 |
| POST /api/v1/transactions | CUSTOMER | Invalid amount/category rejection only here | 2.6/2.7 |
| GET /api/v1/accounts/{deliberately-unmapped-suffix} | CUSTOMER | Safe unmapped-resource response | Infrastructure check, not a new business API |

The last row means the exact absent URL supplied later in case P21-11. It is intentionally not a valid operation.

Do not call mappings copied from test-only controllers in RequestValidationErrorTest or GlobalExceptionHandlerTest. They do not exist in the deployed backend.

## 2. Preconditions — where to go and what to prepare

1. Use the already verified local SafePay application and Oracle database. V13 is already applied; **do not rerun V12 or reseed** to perform this guide.
2. In your HTTP client (for example Postman), create a collection named `SafePay Phase 2.1`.
3. Define `baseUrl` as `http://localhost:8080` **only if your running application uses that address**. The checked main configuration has no custom server.port; retain an existing external override if you use one.
4. Keep the already approved environment. For this showcase baseline, Flyway locations include both migration and showcase and target remains 13. Do not change credentials or secrets for these requests.
5. Refer to the existing [launch guide](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/Documentation for SafePay/SafePay_Backend_Launch_and_Server_Guide.md>) for launch prerequisites. Its historical V12 migration steps are superseded by the current V13 checkpoint and are not repeated here.
6. Use active V12 personas. Retrieve their current account IDs from the API; **persona labels such as 101 are not guaranteed database sequence IDs**.
7. Do not clear tables, delete connections, edit migrations or change grants as test preparation. If an expected result differs, record the response and correlation ID first.

### Personas used

These are synthetic local showcase personas already documented in the [seed guide](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/Documentation for SafePay/SafePay_V1_Data_Seed_Complete_Guide.md>).

| Purpose | Persona / login | Authority |
|---|---|---|
| Main customer | Priya Nair / priya.nair@gmail.com | CUSTOMER |
| Ownership comparison | Arjun Menon / arjun.menon@gmail.com | CUSTOMER |
| Staff pagination | Vikram Bhat / vikram.bhat@gmail.com | SYSTEM_ADMIN |
| Staff isolation, optional additional checks | Rhea Malhotra / rhea.malhotra@gmail.com; Anjali Thomas / anjali.thomas@gmail.com | RISK_OFFICER; AUDITOR |

Passwords below are existing local synthetic seed credentials, not database passwords, JWT signing secrets or SMTP credentials. If you changed a persona's password/status, use the current approved value and record the deviation.

### Header and cookie rules for this guide

| Item | How to use it |
|---|---|
| Authorization | `Bearer {{customerToken}}` or the specific persona's token on protected routes |
| Content-Type | `application/json` on POST bodies |
| Accept | `application/json, application/problem+json` |
| X-Correlation-ID | Use the exact test label below; safe shape is 1–64 characters from letters/digits/`._:-` |
| Idempotency-Key | Required by the transaction creation API; use a fresh distinct key for each invalid payment case |
| Refresh cookie / CSRF | Not required to authenticate these bearer GETs or these login/beneficiary/transaction POSTs. Refresh/logout are different operations, fully covered in 2.2 |
| Origin | A non-browser client need not invent an Origin header. If supplied, use an explicitly allowed configured origin |
| Browser preflight | Let the browser perform it; a CORS rejection can occur before the intended API behavior |
| Token expiry | Log in again if expired; do not reinterpret a 401 as a validation failure |

A refresh cookie alone is not the access credential for protected account routes. Keep personas' JWTs in separate variables. Responses from login can set a refresh cookie; the current API tests below depend on the bearer token, not on whichever login most recently changed that cookie.

## 3. Obtain tokens and discover IDs

### P21-01 — Valid customer login

Create a **POST** request to:

```text
{{baseUrl}}/api/v1/auth/login
```

Headers:

```text
Content-Type: application/json
Accept: application/json, application/problem+json
X-Correlation-ID: p21-login-priya
```

No Authorization or Idempotency-Key is needed. Body:

```json
{
  "loginIdentifier": "priya.nair@gmail.com",
  "password": "SafePay@PNair101"
}
```

Expect **200**, JSON with the following shape:

```json
{
  "accessToken": "<dynamic JWT>",
  "tokenType": "Bearer",
  "expiresAt": "<dynamic UTC instant>",
  "userId": "<actual sequence ID as string>",
  "authorities": ["CUSTOMER"]
}
```

Also expect `X-Correlation-ID: p21-login-priya` and a `Set-Cookie` response for `SAFEPAY_REFRESH`. Cookie attributes follow the current refresh-cookie configuration; local Secure=false versus HTTPS Secure=true is not a reason to alter source during this guide.

Save accessToken as `customerToken`. Do not paste it into shared evidence. Save userId as `customerUserId`.

Repeat with:

| Variable | Exact loginIdentifier | Exact original local seed password | Expected authority |
|---|---|---|---|
| `otherCustomerToken` | arjun.menon@gmail.com | SafePay@AMenon102 | CUSTOMER |
| `systemAdminToken` | vikram.bhat@gmail.com | SafePay@VBhat132 | SYSTEM_ADMIN |
| `riskOfficerToken` if needed | rhea.malhotra@gmail.com | SafePay@RMalhotra131 | RISK_OFFICER |
| `auditorToken` if needed | anjali.thomas@gmail.com | SafePay@AThomas133 | AUDITOR |

**Classes exercised:** AuthController, LoginRequest and AuthService/security collaborators; CorrelationIdFilter surrounds the request. The detailed login lifecycle belongs in 2.2.

**Side effect:** successful login creates/updates authentication/session evidence under the existing contract. This is not a database-free test. Do not repeatedly send deliberately wrong passwords here, because lockout is a real separate behavior.

### P21-02 — Owned account list and safe projection

**GET**:

```text
{{baseUrl}}/api/v1/accounts
```

Headers:

```text
Authorization: Bearer {{customerToken}}
Accept: application/json, application/problem+json
X-Correlation-ID: p21-account-list
```

**Body:** none. **Refresh/CSRF/idempotency:** none required.

Expect **200** and a **JSON array**, not a pagination object:

```json
[
  {
    "accountId": "<actual string ID>",
    "maskedAccountNumber": "<masked prefix plus final four characters>",
    "accountType": "SAVINGS",
    "bankName": "<stored bank name>",
    "ifscCode": "<stored IFSC>",
    "currency": "INR",
    "status": "ACTIVE"
  }
]
```

Values shown in angle brackets are observations to fill in, not literals to send or fixed seeded values. Account type/status reflect the current row; assert them against your observed account, not against an invented universal value.

Check:
- accountId is a JSON string.
- Field names match the example; no password/hash/security session fields or full account number appear.
- The account number mask retains the last four for a normal-length account.
- No balances occur in this summary DTO.
- Header correlation is exactly p21-account-list.
- IDs are ordered by accountId ascending.

Save one returned accountId as `customerAccountId`. Repeat with otherCustomerToken and save `otherCustomerAccountId`. Do not assume either equals a seed persona label.

**Classes:** AccountController → AccountServiceImpl → AccountDao → AccountSummaryResponse → SensitiveDataMasker. This reads ACCOUNT through the runtime connection.

**Follow-up:** run P21-03 and P21-10 using these discovered IDs. If the list is empty, resolve the persona/account precondition; do not invent an ID.

### P21-03 — Monetary response formatting

**GET**:

```text
{{baseUrl}}/api/v1/accounts/{{customerAccountId}}/balance
```

Use customerToken, `X-Correlation-ID: p21-balance`, and no body.

Expect **200** with:

```json
{
  "accountId": "<same string ID>",
  "maskedAccountNumber": "<same masked account>",
  "currency": "INR",
  "currentBalance": "<decimal string with two places>",
  "reservedAmount": "<decimal string with two places>",
  "availableBalance": "<decimal string with two places>"
}
```

Check values as exact decimals: available = current − reserved, and no scientific notation in these three strings. Do not parse a large financial value into a JavaScript floating-point number and then call a precision loss a backend defect.

**Classes:** AccountBalanceResponse.from/formatMoney and the owned-account service path. **Follow-up:** repeat GET if no concurrent work is occurring; it must not itself change balances. Background/user activity can change actual values between reads, so a changing balance alone does not prove a read mutation.

## 4. Correlation and common error checks

### P21-04 — Missing or invalid correlation

Reuse the successful P21-02 GET with the same valid customer token. Change only X-Correlation-ID:

| Input header | Expected result |
|---|---|
| Omitted | 200; nonblank generated UUID-shaped response identifier |
| `p21-safe_01:A.B` | 200; exactly preserved |
| `bad/value` | 200; replaced, slash not echoed |
| 65 ASCII letters | 200; replaced because length exceeds 64 |
| 64 ASCII letters | 200; preserved |
| Leading/trailing ordinary spaces, if client permits them | Trimmed safe value; clients may normalize whitespace before sending |

No request body. Do not attempt newline/header-injection payloads; the simple slash case already exercises the code's rejection branch.

There is no promised response-body traceId on a successful account list. The correlation is in the header. For an error, traceId must match that header.

**Classes:** CorrelationIdFilter.resolveCorrelationId/doFilterInternal. **Follow-up:** send a new request with a different safe label; the new response must reflect its own label. HTTP alone does not prove cleanup of thread-local MDC or propagation into an arbitrary background thread.

### P21-05 — Missing bearer credential

**GET** `{{baseUrl}}/api/v1/accounts`. Remove Authorization completely; keep `X-Correlation-ID: p21-no-token`. No body.

Expect **401**, `application/problem+json`:

```json
{
  "type": "urn:safepay:problem:authentication_required",
  "title": "Authentication required",
  "status": 401,
  "detail": "A valid SafePay access token is required.",
  "instance": "/api/v1/accounts",
  "errorCode": "AUTHENTICATION_REQUIRED",
  "traceId": "p21-no-token",
  "timestamp": "<dynamic ISO-8601 timestamp>"
}
```

Repeat once with an obviously invalid bearer string and expect the same safe authentication contract. Do not use a real secret as an invalid token.

**Classes:** security entry point/writer plus correlation. The MVC advice's AUTHENTICATION_FAILED code is not the expected code for this missing-token path.

**Follow-up:** restore customerToken and confirm P21-02 still returns 200. No account details should appear in the 401 response.

### P21-06 — Wrong authority for staff browsing

**GET**:

```text
{{baseUrl}}/api/v1/admin/accounts?page=0&size=2
```

Use **customerToken**, `X-Correlation-ID: p21-wrong-role`, no body.

Expect **403**:
- type `urn:safepay:problem:access_denied`
- title `Access denied`
- detail `The authenticated user is not authorized for this operation.`
- errorCode `ACCESS_DENIED`
- instance `/api/v1/admin/accounts` (query string is not included)
- traceId matches the response header.

Repeat with riskOfficerToken and auditorToken if obtained; their single roles must also be denied. Then use systemAdminToken and expect 200 in P21-12.

**Side effect:** access-denial security auditing may write evidence. It must not modify accounts or grant authority. This guide does not require deleting that evidence afterward.

### P21-07 — Numeric path binding failure

**GET** `{{baseUrl}}/api/v1/accounts/not-a-number`, with customerToken and `X-Correlation-ID: p21-invalid-id`; no body.

Expect **400 INVALID_REQUEST**, generic detail `The request contains an invalid value.`, matching trace/header, and instance ending `/not-a-number`. The response must not include the Java Long conversion stack trace.

**Classes:** MVC parameter binding → GlobalExceptionHandler.handleArgumentTypeMismatch.

Follow-up GET using customerAccountId should succeed. A malformed path value is different from a valid numeric ID that is not owned.

### P21-08 — Syntactically valid JSON with invalid fields

**POST** `{{baseUrl}}/api/v1/auth/login`, no bearer required, Content-Type application/json, `X-Correlation-ID: p21-validation`.

Exact body:

```json
{}
```

Expect **400**, type `urn:safepay:problem:validation_failed`, title `Request validation failed`, detail `One or more request fields are invalid.`, and:

```json
"fieldErrors": [
  {"field": "loginIdentifier", "message": "loginIdentifier is required"},
  {"field": "password", "message": "password is required"}
]
```

This fragment is part of the full ProblemDetail envelope, not a standalone JSON request. No rejected password value appears.

**Classes:** LoginRequest constraints → GlobalExceptionHandler.handleValidationFailure → FieldValidationError. Validation fails before AuthService.login, so this empty-body case does not intentionally exercise failed-password lockout.

**Follow-up:** P21-01 with valid credentials. Do not conclude all authentication behavior is verified from this one validation check.

### P21-09 — Invalid beneficiary fields versus malformed JSON

Use **POST** `{{baseUrl}}/api/v1/beneficiaries`, customerToken, Content-Type application/json, Accept as above. No Idempotency-Key/CSRF required for this route.

First send `X-Correlation-ID: p21-beneficiary-validation` with:

```json
{}
```

Expect **400 VALIDATION_FAILED**, with errors for:
- `beneficiaryName`: `beneficiaryName is required`
- `paymentMethod`: `paymentMethod is required`

Other nullable size/pattern constraints do not turn null into a required field. The cross-field method returns true when paymentMethod is null so the required-field constraint can report that issue.

Next send `X-Correlation-ID: p21-malformed` and this intentionally broken raw JSON:

```text
{"beneficiaryName":
```

Expect **400 MALFORMED_REQUEST**, title `Malformed request`, detail `The request body is malformed or contains an incompatible value.`. Do not expect fieldErrors for this parsing failure.

**Classes:** CreateBeneficiaryRequest/Jakarta validation or HTTP message conversion → corresponding advice handler.

**Follow-up:** GET /api/v1/beneficiaries with the same customer token before/after these negative cases. Neither invalid request should create a beneficiary. Do not change the broken body into a valid create request as an unplanned “quick test”; full create/duplicate/status scenarios belong in 2.4.

### P21-10 — Ownership-hidden resource

**GET**:

```text
{{baseUrl}}/api/v1/accounts/{{otherCustomerAccountId}}
```

Use **customerToken**, not otherCustomerToken. Header `X-Correlation-ID: p21-owner-isolation`. No body.

Expect **404 ACCOUNT_NOT_FOUND**, title `Resource not found`, detail `Account was not found`. No owner name, full account number or balance should appear.

**Why 404 rather than 403:** AccountDao reads by accountId and ownerUserId together. The service does not reveal whether another customer's resource exists.

**Follow-up:** the identical URL with otherCustomerToken should return 200. Priya's own URL should also still return 200. This combination avoids a false pass caused by testing a nonexistent account rather than ownership isolation.

Also test `/api/v1/accounts/0` with customerToken: expect **400 INVALID_REQUEST** because the service requires a positive ID. Zero is not a missing-owned-record lookup.

### P21-11 — An absent route is a safe 404

**GET**:

```text
{{baseUrl}}/api/v1/accounts/__phase21_route_not_present__/extra
```

Use customerToken and `X-Correlation-ID: p21-unmapped`; no body. The extra segment prevents the URL from matching the accountId-only route.

Expect **404 RESOURCE_NOT_FOUND**, title `Resource not found`, detail `The requested resource was not found.`, with safe ProblemDetail metadata.

**Classes:** framework unmapped/static-resource resolution → GlobalExceptionHandler.handleUnmappedResource. This covers the recently fixed behavior. With no JWT the security layer can return 401 first, which would not test this 404 path.

Do not deliberately force an internal server error or disable a database to test the 500 handler. Its source/automated coverage is summarized in the master chapter; a controlled failure-injection exercise would need its own approval.

## 5. Pagination through the existing staff account API

### P21-12 — First page and next page

**GET**:

```text
{{baseUrl}}/api/v1/admin/accounts?page=0&size=2
```

Use systemAdminToken and `X-Correlation-ID: p21-page-zero`; no body/cookie/CSRF/idempotency requirement.

Expect **200**, with this response shape:

```json
{
  "items": [
    {
      "accountId": "<string ID>",
      "ownerId": "<string ID>",
      "ownerName": "<stored name>",
      "maskedAccountNumber": "<masked number>",
      "accountType": "SAVINGS",
      "bankName": "<bank name>",
      "ifscCode": "<IFSC>",
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 30,
  "totalPages": 15,
  "first": true,
  "last": false
}
```

This is a **shape example**. The example's totals apply only if exactly the original 30 eligible customer accounts remain. items may contain up to two rows; their actual values/status/type come from current data. Do not assert 30/15 after additional approved data changes.

Check the exact eight item fields. The list must not include full accountNumber, currentBalance, reservedAmount or availableBalance. Only SAVINGS/CURRENT customer accounts are queried, not ownerless clearing/control accounts.

Now request `page=1&size=2` with `p21-page-one`. Expect page=1, size=2, ascending account IDs, and no overlap between these pages if underlying account data is unchanged. Stable data is required for cross-request comparisons.

**Classes:** AdminAccountController → AdminAccountServiceImpl → AccountDao.searchCustomerAccounts → PagedResponse.from(AdminAccountSummaryResponse::from).

### P21-13 — Query boundaries and empty page

Run each as a separate GET with systemAdminToken, no body, and a distinct safe correlation value.

| Query suffix | Expected |
|---|---|
| `?page=-1&size=2` | 400 INVALID_REQUEST |
| `?page=0&size=0` | 400 INVALID_REQUEST |
| `?page=0&size=101` | 400 INVALID_REQUEST |
| `?page=abc&size=2` | 400 INVALID_REQUEST from binding |
| `?page=0&size=1` | 200, size=1 |
| `?page=0&size=100` | 200; items need not contain 100 rows |
| `?page=0&size=2&minCurrentBalance=-0.01` | 400 INVALID_REQUEST |
| `?page=0&size=2&minCurrentBalance=1.001` | 400 INVALID_REQUEST |
| `?page=0&size=2&accountType=not-an-enum` | 400 INVALID_REQUEST from enum binding |

After reading totalPages from the first response, request `page=<that totalPages value>&size=2` for a stable nonempty dataset. Since pages start at zero, this requests one page beyond the last valid data page. Expect 200, empty items, the requested page number, the same totals, first=false and last=true. If there are zero matching rows, page0 has first=true/last=true and totalPages=0; do not apply the nonempty example mechanically.

The **service**, not PagedResponse's constructor, enforces the 100-row cap. minCurrentBalance is a search threshold allowing zero; it is not a payment amount and is not validated with the ₹1 transaction minimum.

**Follow-up:** rerun the valid first-page request. Validation should not alter rows or paging configuration. Full customer/type/balance filtering semantics are covered in 2.3.

## 6. Invalid money and conditional-category checks

### P21-14 — Reject invalid payment amounts before creation

Use the existing **POST**:

```text
{{baseUrl}}/api/v1/transactions
```

Headers:

```text
Authorization: Bearer {{customerToken}}
Content-Type: application/json
Accept: application/json, application/problem+json
X-Correlation-ID: p21-money-below-min
Idempotency-Key: p21-money-below-min-001
```

In the client, set numeric `sourceAccountId` and `beneficiaryId` values from that customer's current GET /api/v1/accounts and GET /api/v1/beneficiaries results. Store the latter as `customerBeneficiaryId`. Do not assume accountId=beneficiaryId.

The following is the exact request template; Postman variables in numeric positions must resolve to actual integer text before sending:

```json
{
  "sourceAccountId": {{customerAccountId}},
  "beneficiaryId": {{customerBeneficiaryId}},
  "amount": 0.99,
  "purpose": "Phase 2.1 invalid amount check",
  "customerReference": "P21-INVALID-MONEY"
}
```

Expect **400 VALIDATION_FAILED** and an amount field error `amount must be at least 1.00`. Category is omitted for this below-threshold invalid request.

Repeat, changing **only amount**, correlation and idempotency key:

| Amount value | Expected amount validation |
|---|---|
| `0.00` | Minimum violation |
| `-1.00` | Minimum violation |
| `1.000` | `amount must fit NUMBER(18,2)` |
| `5000.001` | Same digits/scale violation |
| `10000000000000000.00` | Same digits/precision violation |
| `null` | `amount is required` |
| Omit amount property entirely | `amount is required` |

All rows should return 400 VALIDATION_FAILED, not 500 or 201. Every case must preserve its intended numeric representation in the raw body; a client that rewrites 1.000 to 1 changes what is being tested.

**Expected state:** Jakarta validation fails before the controller body, so the request does not reach durable idempotency execution, payment creation, funds reservation or transaction lifecycle evidence.

**Follow-up:** compare GET /api/v1/transactions and the owned balance before/after, looking for the new reference and any balance change attributable to the request. Do not assume a fixed total in a running system with other activity. A 400 alone is the transport check; unchanged state is the additional business-boundary check.

**Do not send the valid ₹1 or maximum-precision value here just to test the utility**: an accepted request can create persistent payment state. Successful creation, risk-band boundaries and replay are exercised deliberately in 2.6/2.7.

### P21-15 — The current category rule is conditional

Use the same valid customer/account/beneficiary setup and POST template from P21-14, with distinct keys. These are deliberate invalid requests:

| Body changes | Expected |
|---|---|
| amount=100000.01, category omitted, purpose unchanged | 400 VALIDATION_FAILED, field `categoryValid` |
| amount=100000.00, category="MEDICAL" | 400 VALIDATION_FAILED, field `categoryValid` |
| amount=100000.01, category="OTHERS", purpose=null | 400 VALIDATION_FAILED, field `categoryValid` |

The expected cross-field message is:

```text
Above INR 100000.00 category is required; OTHERS needs a 1-140 character purpose; lower payments must omit category
```

`categoryValid` is the validation property for `isCategoryValid()`, not an extra JSON field the user should submit.

**Why include this later rule here?** It is a useful real example of shared validation handling a cross-field business contract. It does not move the full category chapter into 2.1.

**Do not test old high-value replay by omitting category and expecting replay success.** The approved strict requirement rejects that malformed new request before the idempotency path. Existing stored legacy payments remain processable through their applicable later workflow; this guide does not recategorize them or alter purpose.

Follow-up state checks are the same as P21-14. Successful new OTHERS/other-category creation and all category ordering cases belong in 2.6/2.11.

## 7. Applicable versus later-phase checks

This table prevents infrastructure documentation from inventing APIs or implying a business workflow has been fully verified.

| Required test dimension | Phase 2.1 treatment | Full follow-through |
|---|---|---|
| Positive API path | Login, owned account/balance, staff pagination | Detailed domain scenarios in 2.2/2.3 |
| Missing/malformed input | Empty validated bodies, broken JSON, path/query types | Domain-specific fields in each chapter |
| Boundary/constraint alignment | Money scale/precision/minimum, page size, correlation length | Actual Oracle constraint/transaction behavior in entity chapters |
| Authentication/authorization | Missing JWT; wrong staff role | SEC-A–SEC-K security matrix in 2.2 |
| Ownership | Known other customer's account returns hidden 404 | Every owned resource in its chapter |
| Duplicate/conflict | Handler map documented; no manufactured conflict endpoint | Registration/beneficiary duplicates; payment/review state conflicts in their chapters |
| Illegal transitions | No standalone transition operation in 2.1 | 2.6, 2.8, 2.10 and 2.11 |
| Idempotency replay/key reuse | No new shared endpoint; invalid request boundary only | 2.7 including operation scopes/fingerprints |
| Concurrency | Repeated requests can observe correlation independence; no financial race exercise here | Reserve/cancel/settle/review races in relevant chapters |
| False-positive risk classification | Not inferable from masking/errors alone | Exact amount boundaries and stored risk evidence in 2.5/2.6 |
| False-negative risk classification | Accepted formatting is not proof of correct risk tier | Same later domain checks |
| Data leakage | Safe error body, masked summary, no full staff-list balances/numbers | Every role projection later |
| 422/409/503/500 envelopes | Source and brief automated coverage explained in master | Real approved domain cases; no intentional outage introduced here |
| External side effects | Login and access-denial evidence acknowledged | No real OTP email or financial mutation is required by these cases |

Do not alter migration files, SQL constraints, Java code or environment settings to force these cases to pass.

## 8. Common misleading results

| Observation | Check first |
|---|---|
| 401 instead of expected 400 | Token absent/expired/invalid: security ran before body validation |
| 403 instead of expected 404 | Wrong role or browser-origin/CSRF path; use the persona specified for the case |
| Customer account response has no page fields | Correct: customer list is an array |
| Staff account list has no balances | Correct: approved summary deliberately omits them |
| Invalid correlation returns 200 | Correct when the underlying request succeeds: filter replaces the label |
| `1.000` unexpectedly accepted | Verify the client sent three fractional places rather than rewriting the number |
| A wrong-owner lookup returns 404 | Correct disclosure boundary; verify the ID exists using its owner's token |
| Timestamps/IDs differ from example | Dynamic values are placeholders, not hard-coded fixture assertions |
| Seed count has changed | Compare current data; do not reseed to restore an example's total |
| Request fails before intended check | Inspect status/errorCode/correlation and the earliest boundary reached |

## 9. Review and evidence checkpoint

For each case, record only: case ID, persona, method/path, status, errorCode when applicable, correlation ID, pass/fail and a short safe observation. Redact JWTs, cookies and credentials from shared screenshots. Do not paste full sensitive bodies into this guide.

Suggested record format:

| Case | Persona | Observed status/code | Correlation | Result / observation |
|---|---|---|---|---|
| P21-02 | Priya | Fill after execution | p21-account-list | Pending |
| P21-10 | Priya reading Arjun ID | Fill after execution | p21-owner-isolation | Pending |
| P21-12 | Vikram | Fill after execution | p21-page-zero | Pending |

This is a manual observation template, not a claim that the requests passed.

**Documentation gate:** review the Phase 2.1 master chapter and this companion together. Report any unclear explanation or unexpected output. Chapter 2.2 and its API guide begin only after the user's next approval. No Maven rerun, database change or new source implementation is required merely to accept these documents.

