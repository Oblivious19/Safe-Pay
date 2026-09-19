# SafePay Backend Phase 2.10 — OTP Verification and Development Email Delivery

## Purpose and scope

Phase 2.10 implements the customer verification step for a `VERY_HIGH` SafePay payment. A payment in `VERIFICATION_REQUIRED` may receive a short-lived OTP at the authenticated customer's stored email address. Correct verification moves the payment to `PENDING_RISK_REVIEW`; it does not release or settle the payment. Exhausting the approved issue or attempt policy cancels the payment and releases its reserved funds.

The Gmail adapter is development/demo infrastructure only. It is not presented as a production OTP provider, has no SMS fallback and is disabled by default. SafePay remains a simulated pre-settlement system.

No Flyway migration was added or changed. The implementation maps the already applied V5 `PAYMENT_OTP_CHALLENGE` structure and its V10 ownership hardening.

## Phase contents

| Subphase | Implemented responsibility |
|---|---|
| 2.10A | Central OTP policy, the single Spring Boot Mail dependency, environment-only configuration and fail-closed defaults. |
| 2.10B | Exact challenge lifecycle: `PENDING`, `VERIFIED`, `EXPIRED`, `LOCKED` and `CANCELLED`. |
| 2.10C | Restricted challenge repository, ownership-qualified reads, pessimistic locks and per-cycle issue counting. |
| 2.10D | Six-digit `SecureRandom` generation, fresh salt, SHA-256 digest and constant-time verification. |
| 2.10E | Isolated Gmail/STARTTLS delivery adapter, stored-email routing, masking and sanitized delivery errors. |
| 2.10F | Initial issue and resend workflow with eligibility, cooldown, issue limit and prior-challenge invalidation. |
| 2.10G | Verification, expiry/reuse rejection, attempt exhaustion, cancellation and reservation release. |
| 2.10H | Three idempotent REST operations with safe request/response and frontend contracts. |
| 2.10I | Unit, contract and Oracle verification for state, ownership, locking, reservation and delivery boundaries. |

## Approved V1 OTP policy

| Setting | V1 value |
|---|---|
| Code | Exactly 6 numeric digits |
| Generator | Java `SecureRandom` |
| Validity | 5 minutes from database-authoritative issue time |
| Failed verification attempts | Maximum 3 per challenge |
| Resend cooldown | 30 seconds |
| Issue limit | 3 per verification cycle: initial issue plus 2 replacements |
| Storage | Fresh random salt plus SHA-256 digest; never plaintext |
| Comparison | Recomputed digest with `MessageDigest.isEqual` constant-time comparison |
| Reuse | Prohibited; terminal challenges cannot verify again |
| Resend effect | Previous pending challenge becomes `CANCELLED`, or `EXPIRED` when already past expiry |
| Exhaustion | Transaction becomes `CANCELLED` and the full reservation is released |
| Successful verification | Transaction becomes `PENDING_RISK_REVIEW`; reservation remains intact |
| Delivery | Development-only Gmail SMTP with STARTTLS; no SMS fallback |
| Recipient | Authenticated customer's stored email; client cannot provide or override it |

The salted SHA-256 construction is the explicitly approved V1 prototype design. A six-digit OTP has a small input space, so this representation is not suitable for a production release if database hashes are exposed. A production design must use a stronger approved secret-protection or provider-managed verification mechanism.

## Main production components

### Challenge, repository and cryptography

- `OtpChallenge` maps the V5 challenge row and owns all legal lifecycle mutations.
- `OtpPolicyProperties` rejects configuration drift from the approved six digits, five minutes, three attempts, 30 seconds and three total issues.
- `OtpChallengeDao` exposes only the required save, locked ownership reads, latest/pending lookups and issue-cycle count. It intentionally exposes no delete operation.
- `SecureOtpCodeGenerator` creates the code with `SecureRandom` and preserves leading zeroes.
- `Sha256OtpHashingService` creates a fresh 16-byte salt for every issue and compares digests with `MessageDigest.isEqual`.
- `OtpCode.toString()` is deliberately redacted so accidental object logging cannot reveal the value.

### Delivery boundary

- `OtpDeliveryGateway` isolates business logic from the transport.
- `EmailOtpDeliveryGateway` accepts only the approved Gmail host `smtp.gmail.com`, port `587`, authentication, STARTTLS enabled and required, and 5000 ms connection/read/write timeouts.
- `DisabledOtpDeliveryGateway` fails closed whenever email delivery is not explicitly enabled.
- `OtpEmailProperties` validates the configured sender and destination syntax.
- `OtpDeliveryException` is converted to a generic `503` problem response; mail-provider causes and credentials are never returned.
- `SensitiveDataMasker.maskEmail` exposes only the first local-part character and the domain, for example `customer@example.com` becomes `c*******@example.com`.

The V5 database literal remains `delivery_channel = 'SIMULATED'` because V1–V11 are immutable and SafePay is a simulated prototype. Actual development email transmission is represented by the isolated adapter, not by altering the historical schema vocabulary.

### Workflow and REST boundary

- `OtpServiceImpl` owns issue, resend and verification transactions.
- `OtpChallengeResult` and `OtpVerificationResult` allow policy failures that changed durable state to commit before the controller emits the safe error response.
- `VerificationController` exposes the approved issue, resend and verify operations.
- `OtpChallengeResponse` contains only challenge identity, transaction identity, status, masked destination, expiry, resend availability, remaining issue allowance and server time.
- `OtpVerificationResponse` contains only safe verification/state metadata and remaining attempts.
- `VerifyOtpRequest` accepts only a positive challenge ID and exactly six numeric digits.
- `OtpVerificationFailureException` supplies a safe `remainingAttempts` field in the RFC 7807 response.

## Canonical issue flow

```text
Authenticated CUSTOMER + transaction ID + Idempotency-Key
        |
        v
Lock owned PAYMENT_TRANSACTION
        |
        +-- not VERY_HIGH / not VERIFICATION_REQUIRED
        |   / reservation not intact ----------> safe rejection
        |
        v
Read Oracle SYSTIMESTAMP
Count issues since this verification cycle began
        |
        +-- challenge already exists ----------> use resend endpoint
        |
        v
Read stored APP_USER email
Generate transient six-digit code
Create fresh salt + SHA-256 digest
Insert PENDING challenge and flush
        |
        v
Deliver through configured gateway
        |
        +-- delivery failure ------------------> rollback insertion
        |
        v
Commit and return 202 with masked metadata only
```

The raw code exists only long enough to hash it and pass it to the delivery gateway. It is never added to an entity, response, audit record, exception message or log statement.

## Canonical resend flow

```text
Lock owned eligible transaction
        |
        v
Lock latest owned challenge
        |
        +-- before created_at + 30 seconds ----> OTP_RESEND_COOLDOWN
        |
        +-- already 3 issues ------------------> invalidate pending challenge
        |                                        release reservation
        |                                        CANCELLED
        |
        v
Invalidate previous challenge immediately
  PENDING + expired deadline -> EXPIRED
  PENDING + live deadline    -> CANCELLED
        |
        v
Create, hash, persist and deliver a fresh code
        |
        v
Return 202 with new challenge identity
```

The prior code cannot be reused after a replacement. A delivery failure rolls the replacement transaction back, including prior-challenge invalidation, so the API never reports a challenge that did not commit.

## Canonical verification flow

```text
Lock owned eligible transaction
        |
        v
Lock challenge qualified by
challenge + transaction + customer
        |
        +-- terminal --------------------------> OTP_CHALLENGE_NOT_USABLE
        |
        +-- expired ---------------------------> mark EXPIRED
        |                                        cancel/release if policy exhausted
        |
        v
Hash candidate with stored salt
constant-time compare
        |
        +-- mismatch, attempts remain --------> persist attempt; safe 422
        |
        +-- third mismatch --------------------> LOCKED
        |                                        release reservation
        |                                        transaction CANCELLED
        |
        v
Mark challenge VERIFIED
Record verification timestamp
VERIFICATION_REQUIRED -> PENDING_RISK_REVIEW
Keep source reservation intact
```

Successful OTP verification is not officer approval. Phase 2.11 must decide whether the payment is released or cancelled.

## API and frontend contract

All three operations require exactly one `Idempotency-Key` header and authenticated server-side `SafePayPrincipal` ownership.

| Method | Endpoint | Request body | Success |
|---|---|---|---:|
| `POST` | `/api/v1/transactions/{transactionId}/otp` | none | `202` |
| `POST` | `/api/v1/transactions/{transactionId}/otp/resend` | none | `202` |
| `POST` | `/api/v1/transactions/{transactionId}/otp/verify` | `challengeId`, `otp` | `200` |

Each endpoint has a distinct idempotency operation scope: `OTP_ISSUE`, `OTP_RESEND` and `OTP_VERIFY`. The verify fingerprint includes the challenge identity and supplied code, but the raw request is not persisted as an idempotency response.

Example safe issue response:

```json
{
  "challengeId": "501",
  "transactionId": "1001",
  "status": "PENDING",
  "maskedDestination": "c*******@example.com",
  "expiresAt": "2026-09-16T15:05:00Z",
  "resendAvailableAt": "2026-09-16T15:00:30Z",
  "remainingIssues": 2,
  "serverTime": "2026-09-16T15:00:00Z"
}
```

The PWA must:

1. show the backend-provided masked destination and authoritative times;
2. never send an email address or SMTP data;
3. never calculate expiry/cooldown as authoritative business state;
4. never persist the OTP or queue issue/resend/verify mutations offline;
5. use a fresh idempotency key for a genuinely new user action and reuse the same key only when retrying the same logical request;
6. restore server state after reconnection rather than assuming a mail or verification result.

## Atomicity, failure and concurrency guarantees

- Issue, resend and verify each execute in one read-write database transaction.
- The transaction row is locked before the challenge, and the source account is locked only when reservation release is required. This consistent order serializes verification, resend, cancellation and exhaustion races.
- `PAYMENT_TRANSACTION`, `PAYMENT_OTP_CHALLENGE` and `ACCOUNT` retain optimistic `@Version` fields in addition to the explicit pessimistic mutation reads.
- V5 permits only one `PENDING` challenge for a transaction, providing an independent database duplicate barrier.
- Challenge reads are qualified by transaction and customer, so a foreign customer receives the same safe not-found boundary.
- Failed attempts, expiry and terminal statuses are persisted before safe failure responses are produced.
- On third incorrect attempt or issue-policy exhaustion, reservation release, reservation end, challenge terminal status and transaction cancellation commit together.
- On successful verification, challenge verification, transaction verification evidence and transition to `PENDING_RISK_REVIEW` commit together.
- SMTP is called before commit so delivery failure cannot produce a successful API result or committed replacement. A rare email-success/database-commit-failure can produce an unusable delivered code; retrying the idempotent operation or requesting a new challenge is the safe prototype recovery path.

## Gmail development activation

The application remains disabled by default. To activate it for a controlled development demonstration:

1. Create a dedicated, non-personal Gmail development mailbox.
2. Enable Google 2-Step Verification for that mailbox.
3. Create a Gmail App Password. Do not use or store the normal Gmail password.
4. Set these environment variables only in the approved local run configuration:

```text
SAFEPAY_OTP_EMAIL_ENABLED=true
SAFEPAY_OTP_EMAIL_USERNAME=<development Gmail address>
SAFEPAY_OTP_EMAIL_APP_PASSWORD=<Gmail App Password>
SAFEPAY_OTP_EMAIL_FROM=<same approved sender address>
```

5. Do not place any credential in `application.properties`, source code, test data, screenshots, chat messages or version control.
6. Start the backend with the existing Oracle variables and the four email variables.
7. Use an `APP_USER.EMAIL` address that the tester owns, create and authorize a `VERY_HIGH` payment, then call the issue endpoint once.
8. Confirm that the email arrives, the API shows only the masked destination, and the code verifies once.
9. Remove the App Password or disable the email flag when the demonstration is finished.

Automated tests mock or replace `JavaMailSender`/`OtpDeliveryGateway`; they never contact Gmail.

## Oracle alignment

- V5 supplies `SEQ_PAYMENT_OTP_CHALLENGE_ID`, lifecycle checks, the one-pending-challenge unique index and terminal-row protection.
- V10 binds challenge customer and transaction ownership at the database boundary.
- `SYSTIMESTAMP` determines issue, expiry, cooldown, verification and cancellation time.
- The application role can select, insert and update challenge rows but receives no challenge-delete path from the repository.
- Terminal challenges cannot be mutated back to pending or verified.
- No V1–V11 migration was edited.

## Verification coverage

The Phase 2.10 package covers:

- exact policy binding and drift rejection;
- challenge mapping, lifecycle and terminal immutability;
- secure six-digit generation including leading zeroes;
- fresh salts, constant-time matching and malformed-hash rejection;
- ownership-qualified repository contracts and Oracle constraints;
- exact Gmail host/port/auth/STARTTLS/timeout validation;
- sender/recipient validation and sanitized provider failures;
- issue eligibility, stored-email derivation and duplicate issue prevention;
- resend cooldown, previous-code invalidation and issue-limit exhaustion;
- expiry, reuse, incorrect attempts, third-attempt lock and reservation release;
- successful transition to `PENDING_RISK_REVIEW` while retaining reservation;
- safe REST DTOs, validation, distinct idempotency scopes and replay headers;
- Oracle persistence, ownership isolation, cooldown and state/funds integration;
- pessimistic lock and optimistic version contracts.

Primary verification classes include:

- `OtpPolicyPropertiesTest`
- `OtpChallengeTest`
- `OtpChallengeMappingTest`
- `OtpChallengeRepositoryContractTest`
- `OtpChallengeDaoOracleIntegrationTest`
- `SecureOtpCodeGeneratorTest`
- `Sha256OtpHashingServiceTest`
- `EmailOtpDeliveryGatewayTest`
- `OtpServiceImplTest`
- `VerificationControllerTest`
- `OtpServiceOracleIntegrationTest`
- `OtpConcurrencyContractTest`
- `TransactionCancellationServiceTest`
- `GlobalExceptionHandlerTest`

## Explicitly deferred work

- Phase 2.11 implements Risk Officer queue, approval, rejection, re-verification request and notes. OTP success alone cannot release the payment.
- Phase 2.12 implements complete notification APIs, delivery attempts and WebSocket/STOMP updates.
- Real SMS delivery, paid providers, production email infrastructure and provider-level delivery receipts are not part of V1.
- Production OTP secret protection must replace the approved prototype salted SHA-256 design before any customer release.
- Production-grade rate limiting beyond the persisted per-cycle issue/attempt policy requires a separately approved design.
- Real NPCI/RBI rail integration remains outside SafePay V1.

## Operational notes for teammates

1. Keep `safepay.otp.email.enabled=false` unless performing an approved local demonstration.
2. Never enable email without all Gmail host, port, authentication, STARTTLS, timeouts, username, App Password and sender checks passing.
3. Never accept a recipient email from the browser; always use the authenticated user's stored email.
4. Never log or return a raw OTP, hash, salt, Gmail credential or full destination.
5. Do not manually change challenge status or transaction state to bypass the service workflow.
6. A `PENDING_RISK_REVIEW` payment still holds its reservation and must wait for Phase 2.11 review.
7. `OTP_POLICY_EXHAUSTED` intentionally cancels the payment and releases funds; it is not a retryable verification state.
8. Run the focused Phase 2.10 groups, the Oracle workflow test and the complete Maven suite before locking this phase.
9. Perform the real Gmail receipt check manually and separately; it is intentionally excluded from automated tests.
