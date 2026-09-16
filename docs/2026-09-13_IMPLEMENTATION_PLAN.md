# SafePay remaining implementation plan

13 September 2026

## Starting point

Registration and basic session login work. Customer account and transaction routes use session identity. The September 11 handover records manual registration/login success and automated ownership tests. It does not establish whole-application security or completed live transaction testing.

Continue one bounded task at a time. Keep Oracle ddl-auto=validate, preserve existing data, and do not automatically execute SQL or create/settle live demo transactions as part of planning.

## Step 1 Secure the user and session foundation

Evidence: UserController exposes GET /api/users, GET /api/users/{userId}, PUT /api/users/{userId} and DELETE /api/users/{userId}. LoginSecurityConfig and CustomerResourceSecurityConfig do not match these URLs. UserServiceImpl.updateUser can change a password and deleteUser physically deletes eligible users. These legacy routes must not remain publicly usable.

Proposed next implementation scope, subject to agreeing the API retirement policy:

- Keep public registration and login.
- Retire unrestricted list, arbitrary-ID read/update/delete routes. Do not invent an admin API.
- Add a safe session-owned profile read endpoint and POST logout with session invalidation and CSRF protection.
- If profile editing is required now, define an allowlisted request DTO and session-owned update; otherwise defer it. Never let a profile request set role, status, password hash or balance.
- Check current database user status on protected requests so an already logged-in suspended or inactive user does not keep unrestricted use of that session.
- Keep failed-login counting/temporary lockout as a separate task. Decide threshold and duration before implementing it.

Acceptance: anonymous user CRUD is unavailable; customer A cannot view or modify customer B; profile responses contain no password/hash; logout invalidates the session; existing registration/login/ownership tests pass.

## Step 2 Standardize Beneficiary ownership

Evidence: BeneficiaryController still accepts userEmail for list, create and status updates and accepts accountId during creation. It is outside the current session security matchers.

- Derive owner from the session and current account.
- Use validated DTOs for beneficiary input and status changes.
- Keep GET bodies empty and use clean resource paths.
- Apply CSRF to writes, reject another user's beneficiary, and preserve existing data.
- Validate required name, account number, IFSC and allowed lifecycle values; determine the intended duplicate-beneficiary rule before adding constraints.

Acceptance: separate customers see only their own beneficiaries, cannot mutate each other's data, and can use their owned ACTIVE beneficiary for transaction creation. No risk redesign.

## Step 3 Verify transaction correctness

Evidence in TransactionServiceImpl: initiate checks for an existing idempotency key but does not compare the new payload; cancellation receives a key without persisting key-specific outcomes; auto-release changes account balances without an account-level concurrency mechanism visible in this flow. TransactionDao checks state/version for cancellation and settlement, but protection-time validation is outside the update predicate. These need focused review and tests before claiming robust concurrent behavior.

- Test LOW settlement, MEDIUM/HIGH cancellation and auto-release, HARD_HOLD remaining unsettled, and repeated requests.
- Define behavior for reusing a key with a different payload; prevent duplicate debits under concurrent requests.
- Test multiple payments against one balance and simultaneous cancel/settle operations.
- Respect BLOCKED/CLOSED accounts and define funds reservation during protection before changing debit behavior.
- Verify audit writes and responses reflect committed state.

Business decision: registration starts at INR 5,000 while the service retains INR 5,000. Choose approved pre-funded demo fixtures or explicitly revise the minimum-balance policy. Do not silently top up balances or remove the rule. Keep risk ranges unchanged unless separately requested.

HARD_HOLD release is not implemented. The Phase 1 specification also describes deferring its authentication endpoint. Confirm whether safe holding alone or a separate reauthentication flow is required for the college demonstration.

## Step 4 Integrate the browser frontend

Evidence: WebConfig allows localhost:8000 but does not enable credentialed CORS or expose X-CSRF-TOKEN. Existing frontend calls still contain legacy identity/URL patterns.

- Enable credentials only for the approved explicit origin and expose the CSRF response header.
- Send session cookies and CSRF tokens from the frontend; do not use local storage as authentication authority.
- Update account response handling and transaction JSON requests.
- Connect profile/session state and logout only after their backend contracts are ready.
- Add useful loading, validation and error displays before visual redesign.

Acceptance: browser login, current account, beneficiaries and transactions work without a manually entered caller email. Untrusted origins remain rejected.

## Step 5 Prepare the team demonstration

- Run the whole default test suite and opt-in Oracle checks against an approved test environment.
- Complete live Postman flows with two isolated sessions and approved fixtures; record expected and actual results without secrets.
- Review configuration: externalize database credentials and avoid Oracle SYSTEM as runtime user. Plan least-privilege migration separately; never auto-migrate a live schema.
- Reconcile PHASE1_SPEC.md with approved session authentication and new endpoint paths. It still mentions JWT, /transactions/initiate and older entity details; AGENTS.md and the approved implemented contracts take precedence where they conflict.
- Confirm Desktop and Eclipse run the same updated source. Review uncommitted work and missing/deleted tests before any approved commit or push; do not discard changes.
- Update the team setup guide, Postman collection, test evidence and ER diagram.

## Execution boundary for this planning task

Read the supplied handover, inspected the current endpoint/security/service code, and ran compilation plus the default automated test suite. No application APIs, frontend files, database schema or data are changed by this planning document.

Next decision: approve retiring the public user CRUD routes and adding session-owned profile read plus logout as the first implementation task, or specify a different bounded priority. Beneficiary and transaction correctness follow after that reviewable checkpoint.
