# Amount-only risk correction — 15 September 2026

The current user requirement replaces the previously preserved multi-signal Shreya policy. New payment risk is determined only by the server-validated amount.

| Amount | Risk tier | Result |
|---|---|---|
| INR 0.01 through 10,000.00 | LOW | Immediately SETTLED; no hold or verification |
| Above INR 10,000 through 50,000.00 | MEDIUM | PROTECTED for 10 seconds; cancellable before expiry |
| Above INR 50,000 through 100,000.00 | HIGH | PROTECTED for 60 seconds; cancellable before expiry |
| Above INR 100,000 | VERY_HIGH | HARD_HOLD until server-password verification; no automatic expiry |

Zero/negative amounts and values outside NUMBER(18,2) remain invalid. An exact boundary belongs to the lower band: 10,000 is LOW; 50,000 is MEDIUM; 100,000 is HIGH.

## Implementation

- RiskAssessmentEngine.assessAmount(BigDecimal) returns the tier, duration, authentication requirement and a plain-language amount-range reason.
- TransactionServiceImpl invokes that method directly after existing ownership, account, beneficiary and available-funds validation. It no longer builds or queries beneficiary/history/device/context risk evidence.
- The compatibility assess(RiskAssessmentInput) method reads only the amount. The old snapshot builder remains available as unused compatibility code.
- Existing account locking, reservations, one-time debit/audit behavior, sessions, CSRF, cancellation, scheduler isolation and hard-hold password verification are preserved.
- The frontend already displays the persisted backend tier/state/reason. No frontend source change is required.

Example reason for a NEW INR 5,000 payment:

```text
Amount INR 5000.00 is above INR 0 and at most INR 10,000: LOW, immediate settlement.
```

## Verification completed

Java 17 / Maven package completed successfully: **396 tests, 395 passed, 1 Oracle-only skip, 0 failures, 0 errors**, across 29 test classes.

Coverage includes exact boundary values and adjacent cents, context-independent classification, invalid-money rejection, rejection before risk evaluation, no history queries during initiation, fresh-beneficiary INR 5,000 immediate settlement, one-time debit on retry, database-persisted tiers, 10-/60-second timers, cancellation, and VERY_HIGH verification/ownership/failed-attempt/audit behavior. The full real Spring session/CSRF/H2 workflow also passed.

No live API requests or database migrations were run. The existing source archives and other project variants were not modified. The executable dist/SafePay.jar is rebuilt with this policy.

## Apply and manually check

Restart the backend you launched from C:\Shreya\Safe-Pay\Backend:

```cmd
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile uses in-memory H2; restarting resets its demo records. If using that profile, register/fund the demo account again using the updated Postman guide. An Oracle-backed restart does not reset its persisted records.

Submit a NEW INR 5,000 payment using a new Idempotency-Key. Expected fields:

```json
{
  "amount": 5000.00,
  "riskTier": "LOW",
  "state": "SETTLED",
  "protectionSeconds": 0,
  "authenticationRequired": false,
  "protectionExpiresAt": ""
}
```

Previously saved payments retain their original risk decision, state and audit history. Reusing an old initiation key returns that original payment, without rescoring or debiting again. Old HARD_HOLD records can still use the existing password-verification workflow; this code update does not silently settle them.

The updated Postman examples use separate LOW INR 5,000, MEDIUM INR 20,000, HIGH INR 60,000 and VERY_HIGH INR 150,000 requests. Reimport the collection/environment if your Postman copy still has the earlier contextual-risk examples.
