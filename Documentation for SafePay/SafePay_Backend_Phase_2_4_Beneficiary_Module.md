# SafePay Backend Phase 2.4 — Beneficiary Module

## Purpose and scope

Phase 2.4 implements customer-owned beneficiary management for the SafePay V1 simulated pre-settlement system. It supports bank-account and UPI beneficiaries, safe masked responses, duplicate prevention, owner isolation, and reversible enable/disable status changes.

Beneficiary age is stored only as ordinary creation metadata. It is not a V1 risk signal, score input, or cooling-period rule.

## Completed structure

- `BeneficiaryPaymentMethod` defines `BANK_ACCOUNT` and `UPI`.
- `BeneficiaryStatus` defines `ACTIVE` and `DISABLED`.
- `Beneficiary` maps the Oracle table, sequence, ownership relationship, immutable destination identity, optimistic version, and timestamps.
- `BeneficiaryDao` exposes narrowly scoped creation, owner-qualified reads, duplicate checks, active lookup, and a pessimistic-write status query. It intentionally exposes no delete operation.
- Request DTOs normalize text, IFSC, and UPI values and require fields that match the selected payment method.
- `BeneficiaryResponse` exposes a string ID and masked destination identifier rather than raw bank-account or UPI data.
- `BeneficiaryServiceImpl` enforces ownership, domain validation, duplicate checks, Oracle uniqueness-race translation, status locking, and payment eligibility.
- `BeneficiaryController` defines the four V1 beneficiary routes and takes identity only from a server-provided authentication principal.

## Main execution flows

### Create

1. The controller obtains the user ID from `SafePayPrincipal`.
2. Bean Validation rejects malformed or mixed destination fields.
3. The service resolves the persisted owner and creates the correct domain entity.
4. An owner-scoped duplicate query checks the normalized destination.
5. The insert is flushed so either Oracle unique-constraint race is translated to `BENEFICIARY_ALREADY_EXISTS`.
6. The response masks the destination identifier and returns `201 Created` with its resource location.

### Read

All list and detail queries include `owner_user_id`. A missing beneficiary and a beneficiary owned by somebody else both produce `BENEFICIARY_NOT_FOUND`, preventing ownership disclosure.

### Status change

The service loads the owned row with `PESSIMISTIC_WRITE`, applies `ACTIVE` or `DISABLED`, and lets the transaction commit the managed entity. Disabled beneficiaries are rejected for later payment use with `BENEFICIARY_DISABLED`.

## Oracle alignment and safety controls

- Table: `SAFEPAY_OWNER.BENEFICIARY`.
- Sequence: `SAFEPAY_OWNER.SEQ_BENEFICIARY_ID`.
- Bank uniqueness: `(OWNER_USER_ID, BANK_ACCOUNT_NUMBER, IFSC_CODE)` through `UK_BEN_OWNER_BANK`.
- UPI uniqueness: `(OWNER_USER_ID, UPI_ID)` through `UK_BEN_OWNER_UPI`.
- Payment methods, method-specific columns, status values, IFSC normalization, UPI normalization, version, and timestamps mirror the applied V2 schema.
- No migration was changed and no beneficiary delete API exists.
- Responses never contain `bankAccountNumber` or `upiId`; only a masked representation is returned.

## Test coverage

The phase tests cover entity invariants, normalization, masking, repository query execution, service ownership and lifecycle rules, REST mapping and error translation, and real Oracle create/read/list/duplicate/ownership/status behavior.

Phase 2.4 was locked after the user verified the focused gates and the complete Maven suite: **172 tests run, 0 failures, 0 errors, and 0 skipped**.

## Explicitly deferred security work

The controller is fail-closed and never accepts a customer ID from request JSON, query parameters, or paths. The previously approved deferred Spring Security phase must still add JWT validation, configure the authenticated `SafePayPrincipal`, enforce the exact `CUSTOMER` authority, and provide stable Security-layer `401` and `403` responses. Until that work is completed, this controller must not be described as production-secured.
