# Hard-held payment review and available funds

## Run the update

Stop the existing backend with Ctrl+C in its terminal, then run C:\Shreya\Safe-Pay\start-backend.cmd. This launcher now uses dist/SafePay-hard-hold-fix.jar. The original dist/SafePay.jar remains available as the previous build. Restart the frontend if necessary and hard-refresh the browser (Ctrl+F5).

No Oracle schema migration is required. Existing balances, payments and audit history were not changed during implementation. Oracle was queried read-only to diagnose the reported balance.

## Admin review

The queue and review display **Purpose / customer note**. Check the review confirmation and select **Approve and settle** or **Decline payment**.

POST /api/admin/transactions/{id}/decline requires an active ADMIN session, CSRF token and Idempotency-Key. It changes only HARD_HOLD to CANCELLED and records ADMIN_DECLINED_CANCELLED with the administrator ID and time in AUDIT_LOG. Since held funds have not been debited, no credit/refund is applied. The reservation is released when the payment becomes CANCELLED. Customer history shows Cancelled. Other pending holds remain reserved. Replaying the same decision/key does not repeat the action; an ambiguous browser response cannot switch to the opposite decision.

## Multiple held payments

There is no one-hard-hold limit. The backend permits any number that fit the selected account's funds while retaining the existing INR 5000 minimum:

    available for a new payment = account balance - all PROTECTED/HARD_HOLD amounts - 5000

GET /api/accounts/{id}/funds returns an owner-scoped, single-query snapshot with balance, reservedBalance, minimumBalance and availableToTransfer. The Send Money page displays those values, refreshes them on account selection and before review, and reloads the account after closing a payment. The locked server-side transfer check remains authoritative if the snapshot changes.

The reported account had INR 289600, an INR 200000 hard hold and INR 84600 available for a new payment. A second INR 150000 needs at least INR 355000 total balance if no other holds exist. Balances were not increased and reservation protections were not relaxed.

## Verification

- 90 backend tests passed using isolated H2 databases: authorization/CSRF, idempotency, concurrent funded holds, insufficient funds, exact minimum, decline reservation release and approval/decline races.
- 67 frontend tests passed; TypeScript compilation passed.
- Hidden browser preview checked labelled purposes, confirmed decline, one remaining hold and available/reserved balance display. It used synthetic responses and submitted no live payment.
- Maven test dependency retrieval failed in this environment. Compiled tests ran through JUnit Platform Console 1.11.4 with Maven's resolved test classpath. The deployable JAR contains recompiled changed classes with the existing packaged dependencies preserved.