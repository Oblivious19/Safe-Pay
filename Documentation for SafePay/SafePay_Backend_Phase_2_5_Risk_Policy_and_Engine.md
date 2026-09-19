# SafePay Backend Phase 2.5 — Versioned Risk Policy and Engine

## Purpose and scope

Phase 2.5 implements SafePay V1's deterministic amount-only risk decision. It reads the immutable policy published in Oracle, selects exactly one amount band, and returns the complete policy snapshot and protection action needed by the later transaction state engine.

V1 deliberately does not evaluate beneficiary age, velocity, historical behaviour, device, location, weighted signals, or AI output. Those remain separately approved V2 work.

## Phase contents

| Subphase | Responsibility |
|---|---|
| 2.5A | Immutable JPA mappings and canonical risk/protection enums |
| 2.5B | Read-only repositories for policies, bands, and protection actions |
| 2.5C | Stable risk-result and policy-snapshot records |
| 2.5D | Read-only service that resolves one eligible policy and one matching band |
| 2.5E | Deterministic `AmountRiskEngine` orchestration |
| 2.5F | Oracle-backed boundary verification and phase documentation |

## Main code responsibilities

- `RiskPolicy`, `RiskPolicyBand`, and `ProtectionPolicy` map the V3/V10/V11 reference data as immutable database-owned entities.
- `RiskPolicyDao` finds an ACTIVE, effective, INR, `AMOUNT_ONLY` policy.
- `RiskPolicyBandDao` loads the policy, matched band, and protection action as one joined object graph.
- `RiskPolicyServiceImpl` requires exactly one eligible policy and exactly one matching band. Missing, duplicate, gapped, or overlapping configuration fails closed.
- `AmountRiskEngine` validates and normalizes the amount, obtains one server-clock evaluation timestamp, resolves the band, and creates `RiskEvaluationResult`.
- `RiskPolicySnapshot` preserves the coherent policy ID/version, band ID/code, protection-policy ID, and risk tier for later transaction persistence.
- `RiskEvaluationResult` adds the null V1 risk score, protection duration and flags, customer-safe explanation, and UTC evaluation time.

## Evaluation flow

1. The caller supplies a `BigDecimal` payment amount.
2. `MoneyUtility` rejects null, amounts below ₹1.00, excess decimal places, and values outside Oracle `NUMBER(18,2)` precision.
3. The engine captures one server-authoritative UTC timestamp.
4. The policy service queries only ACTIVE, effective, INR, `AMOUNT_ONLY` policies.
5. Exactly one policy must be returned.
6. The repository selects bands where `minimumAmount <= amount` and `maximumAmount` is either null or at least the amount.
7. Exactly one band must match.
8. The result is built from that single joined band, preventing IDs or actions from different policy rows from being combined.

Example: `5000.01` resolves to `AMOUNT_MEDIUM_V1`, produces tier `MEDIUM`, keeps `riskScore` null, and returns a 10-second cancellable timer action.

## Database alignment and safety

- Policy data is read-only to `SAFEPAY_APP`; repositories expose no save or delete operations.
- Published policy entities are also marked Hibernate `@Immutable`.
- V10 database guards require four complete, contiguous bands and prevent changes to ACTIVE or RETIRED policy history.
- Exact `BigDecimal` comparison preserves the boundaries `5000.00/5000.01`, `25000.00/25000.01`, and `100000.00/100000.01` without floating-point rounding.
- The service filters by effective time and normalizes timestamps to Oracle's six-digit fractional precision.
- Configuration anomalies throw an internal `IllegalStateException`; no fallback tier or fabricated score is produced.
- The engine does not hard-code an overall business maximum. The only upper restriction is the approved `NUMBER(18,2)` storage precision.
- The phase performs no transaction creation, balance reservation, state transition, ledger mutation, OTP action, or review action. Those belong to later phases.

## Verification coverage

- Entity tests verify immutable Oracle mappings and canonical enums.
- Repository integration tests verify the single eligible policy, four protection actions, four joined bands, exact boundaries, and read-only repository surface.
- Result tests verify coherent snapshots, null V1 score, action consistency, explanation limits, and UTC timestamp precision.
- Service unit tests verify exact policy filters, normalized values, read-only transactions, and fail-closed zero/multiple policy or band outcomes.
- Engine unit tests cover all mandatory valid and invalid boundaries, deterministic output, and configuration-failure propagation.
- Oracle engine integration tests exercise the complete Spring/JPA/Oracle path against the canonical V11 policy without changing database data.

Expected cumulative result after the Phase 2.5D–2.5F verification gate: **236 tests, 0 failures, 0 errors, 0 skipped**. This count remains pending until the user runs and confirms the test suite.

## Deferred integration

Phase 2.6 will persist the returned policy snapshot into `PAYMENT_TRANSACTION` and apply the resulting state transition. Authentication/JWT/RBAC remains on the explicit security re-entry list and is not claimed complete by this phase.
