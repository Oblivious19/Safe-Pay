# Admin-only approval for HARD_HOLD payments

Updated 16 September 2026. Applied only to C:\Shreya\Safe-Pay.

## Run and check

1. Restart the backend with your existing Oracle configuration. If using the packaged launcher, the updated dist/SafePay.jar is included. application.properties and Oracle data are unchanged.
2. Restart the frontend with npx ojet serve --server-port=8000 and hard-refresh the browser (Ctrl+F5).
3. As a customer, create a simulated payment for INR 120000. The source account must cover this amount, any other held payments and the INR 5000 minimum.
4. The payment remains HARD_HOLD / VERY_HIGH and shows Awaiting admin approval. There is no customer password verification button. It never releases on a timer.
5. Use a separate browser profile/private window to sign in with your existing ADMIN account. Open Administration. Payment approval notifications refresh every five seconds while this page is open, including when the list was empty.
6. Select Review request, inspect the customer, source account, beneficiary, purpose and exact amount, then Approve and settle.
7. The request leaves the pending list. The customer Transactions page refreshes to SETTLED. The source account is debited once. AUDIT_LOG contains ADMIN_APPROVED_SETTLED, administrator USER_ID, transaction ID, timestamp and the retry key.

## Postman

Sign in as ADMIN using the existing session login endpoint. Keep the session cookie. GET http://localhost:8080/api/admin/transactions/hard-holds returns pending requests and an X-CSRF-TOKEN response header. Copy this header to the approval request in the same session.

POST http://localhost:8080/api/admin/transactions/123/approve

Headers: X-CSRF-TOKEN: <token from GET>; Idempotency-Key: <new UUID for this approval>. No request body or customer password is required. Replace 123 with the returned transactionId. Retrying the same transaction as the same admin with the same key returns the saved settled result without another debit. Reusing a key for another transaction/actor/operation is rejected. A customer request receives 403; an anonymous request receives 401. The former customer POST /api/transactions/{id}/verify is blocked.

No database migration is needed: existing transaction verification timestamps/keys and AUDIT_LOG.REQUEST_KEY are reused. Existing saved risk reasons are historical and are not rewritten. All thresholds, timed protection windows, customer cancellation behavior, account selectors and other modules are preserved.
