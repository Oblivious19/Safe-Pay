# Live API test/action ledger

Only designated local demo identities are used. Passwords, tokens, cookies and OTP codes are omitted. Sign-in/refresh/logout can change session and audit rows; GET tests do not intentionally mutate financial data.

| Time UTC | Actor | Test | Request | HTTP | Expected | Result | Correlation | Resource / outcome |
|---|---|---|---|---|---|---|---|---|
| 2026-09-20T20:45:35.947Z | 2468 | LOGIN | POST /auth/login | 200 | 200 | PASS | 62c2d441-4179-4d1f-a3f7-d59159176b54 | user 2468 |
| 2026-09-20T20:45:36.242Z | 2468 | CUSTOMER_PROFILE | GET /users/me | 200 | 200 | PASS | 0fde8185-69bb-4a58-a270-1488ce4c6102 | user 2468 |
| 2026-09-20T20:45:36.363Z | 2468 | ACCOUNTS | GET /accounts | 200 | 200 | PASS | 38103a90-8f72-4e17-ad83-af3d7044c2cd | 1 items |
| 2026-09-20T20:45:36.702Z | 2468 | ACCOUNT_DETAIL | GET /accounts/1626 | 200 | 200 | PASS | 87ccff62-5487-4e33-966d-fcb431a27082 |  |
| 2026-09-20T20:45:36.767Z | 2468 | BALANCE | GET /accounts/1626/balance | 200 | 200 | PASS | 610cfe7b-268d-4895-837a-a93fde2ee286 |  |
| 2026-09-20T20:45:36.841Z | 2468 | BENEFICIARIES | GET /beneficiaries | 200 | 200 | PASS | 5e7e246d-5888-4ef8-9fd3-a14b60874b39 | 1 items |
| 2026-09-20T20:45:36.923Z | 2468 | BENEFICIARY_DETAIL | GET /beneficiaries/1158 | 200 | 200 | PASS | 95074bc2-e30f-4815-a188-19d02ca63eee | beneficiary 1158 |
| 2026-09-20T20:45:37.118Z | 2468 | PAYMENT_LIST | GET /transactions?page=0&size=5 | 200 | 200 | PASS | a81eb25c-86dd-42f4-b97b-0926127dd312 | 2 items |
| 2026-09-20T20:45:37.215Z | 2468 | PAYMENT_DETAIL | GET /transactions/1553 | 200 | 200 | PASS | 68e9892b-77ea-4506-8688-14cfe82d68d9 | transaction 1553 PROTECTED |
| 2026-09-20T20:45:37.346Z | 2468 | PAYMENT_AUDIT | GET /transactions/1553/audit?page=0&size=5 | 200 | 200 | PASS | 19ac8544-591d-4e4a-bea3-7aef96eb938c | 2 items |
| 2026-09-20T20:45:37.411Z | 2468 | NOTIFICATIONS | GET /notifications?page=0&size=5 | 200 | 200 | PASS | f9202aa0-03a4-4d1a-b3c1-15d426ed0aea | 2 items |
| 2026-09-20T20:45:38.074Z | 2470 | LOGIN | POST /auth/login | 200 | 200 | PASS | cccc3634-f3e4-4022-9b1b-16a56366d13d | user 2470 |
| 2026-09-20T20:45:38.119Z | 2470 | CUSTOMER_PROFILE | GET /users/me | 200 | 200 | PASS | 5550ef0b-0ee4-435a-91fa-c641d00c150b | user 2470 |
| 2026-09-20T20:45:38.167Z | 2470 | ACCOUNTS | GET /accounts | 200 | 200 | PASS | 7fbd200d-a544-406c-aab8-9dc5938c1595 | 1 items |
| 2026-09-20T20:45:38.236Z | 2470 | ACCOUNT_DETAIL | GET /accounts/1628 | 200 | 200 | PASS | 980097c7-d9c0-4f99-9d88-cad21e1cc4a7 |  |
| 2026-09-20T20:45:38.272Z | 2470 | BALANCE | GET /accounts/1628/balance | 200 | 200 | PASS | 180fe84f-68e7-4ff7-87e5-8101bd02b87f |  |
| 2026-09-20T20:45:38.302Z | 2470 | BENEFICIARIES | GET /beneficiaries | 200 | 200 | PASS | ce7b4cab-3fab-4715-8293-de9cebef3d46 | 1 items |
| 2026-09-20T20:45:38.340Z | 2470 | BENEFICIARY_DETAIL | GET /beneficiaries/1160 | 200 | 200 | PASS | 9a6ba055-f7a4-49bd-a81f-261adac5069e | beneficiary 1160 |
| 2026-09-20T20:45:38.385Z | 2470 | PAYMENT_LIST | GET /transactions?page=0&size=5 | 200 | 200 | PASS | 433d9be4-6c1c-4284-a1b9-5e53d5cbacdc | 1 items |
| 2026-09-20T20:45:38.439Z | 2470 | PAYMENT_DETAIL | GET /transactions/1053 | 200 | 200 | PASS | b58eba42-e6e9-4eff-8b44-2decff593f1e | transaction 1053 CANCELLED |
| 2026-09-20T20:45:38.609Z | 2470 | PAYMENT_AUDIT | GET /transactions/1053/audit?page=0&size=5 | 200 | 200 | PASS | 02ffbe3b-3e96-4d81-8e4c-ad02561810f9 | 1 items |
| 2026-09-20T20:45:38.665Z | 2470 | NOTIFICATIONS | GET /notifications?page=0&size=5 | 200 | 200 | PASS | 2bac37de-d09d-485e-811f-34d11f70e160 | 1 items |
| 2026-09-20T20:45:39.340Z | 2498 | LOGIN | POST /auth/login | 200 | 200 | PASS | 3b823285-1d8f-4878-aa4b-b3502aeca971 | user 2498 |
| 2026-09-20T20:45:39.972Z | 2499 | LOGIN | POST /auth/login | 200 | 200 | PASS | 68e52995-7b18-456a-bec1-8bcb7d95a3e4 | user 2499 |
| 2026-09-20T20:45:40.641Z | 2500 | LOGIN | POST /auth/login | 200 | 200 | PASS | 571893ca-3cb4-4a83-8121-a58392345d1e | user 2500 |
| 2026-09-20T20:45:40.690Z | 2468 | CSRF | GET /auth/csrf | 200 | 200 | PASS | 1d943f08-2402-4559-ade7-14a06a5f6bbd |  |
| 2026-09-20T20:45:40.812Z | 2468 | REFRESH | POST /auth/refresh | 200 | 200 | PASS | 1b57f88c-5a4b-42eb-af22-e4437b4902ba | user 2468 |
| 2026-09-20T20:45:40.875Z | 2468 | CUSTOMER_ADMIN_DENIED | GET /admin/users | 403 | 403 | PASS | 066b983c-6b1a-46c7-b853-574db41f07f6 | ACCESS_DENIED |
| 2026-09-20T20:45:40.917Z | 2468 | ACCOUNT_OWNERSHIP_DENIED | GET /accounts/1628/balance | 404 | 403/404 | PASS | b6ddb59c-ec6a-44eb-9aaf-ebab6e3facfe, b6ddb59c-ec6a-44eb-9aaf-ebab6e3facfe | ACCOUNT_NOT_FOUND |
| 2026-09-20T20:45:40.951Z | 2470 | PAYMENT_OWNERSHIP_DENIED | GET /transactions/1553 | 404 | 403/404 | PASS | e7f2c9fe-c678-4212-a3b0-2e18ee570ac1, e7f2c9fe-c678-4212-a3b0-2e18ee570ac1 | TRANSACTION_NOT_FOUND |
| 2026-09-20T20:45:41.220Z | 2499 | ADMIN_STATS | GET /admin/operations/stats | 200 | 200 | PASS | a32da8da-cec0-4795-9738-1e6cd0eda71f |  |
| 2026-09-20T20:45:41.423Z | 2499 | ADMIN_USERS | GET /admin/users?role=CUSTOMER&page=0&size=5 | 200 | 200 | PASS | bae3c9d8-bda0-480d-86e7-7dfd0d27f4e0 | 31 items |
| 2026-09-20T20:45:41.461Z | 2499 | ADMIN_USER_DETAIL | GET /admin/users/2468 | 200 | 200 | PASS | 011a5282-5554-49be-84f2-55e17467b409 | user 2468 |
| 2026-09-20T20:45:41.584Z | 2499 | ADMIN_ACCOUNTS | GET /admin/accounts?customerId=2468&page=0&size=5 | 200 | 200 | PASS | c3f30084-c23a-4794-8e38-4ad0cfa1d015 | 1 items |
| 2026-09-20T20:45:41.656Z | 2499 | ADMIN_ACCOUNT_DETAIL | GET /admin/accounts/1626 | 200 | 200 | PASS | 9f39dc7a-1c29-43e1-b255-8f0815c05817 |  |
| 2026-09-20T20:45:41.688Z | 2499 | ADMIN_ACCOUNT_FUNDS | GET /admin/accounts/1626/balance | 200 | 200 | PASS | 0587682f-29cc-45d8-a107-327ed7f01364 |  |
| 2026-09-20T20:45:41.764Z | 2499 | ADMIN_FAILURES | GET /admin/operations/failures?page=0&size=5 | 200 | 200 | PASS | b81c19ec-9372-4366-97b2-65e41d7af4d3 | 3 items |
| 2026-09-20T20:45:41.795Z | 2499 | ADMIN_REVIEW_DENIED | GET /admin/risk-reviews | 403 | 403 | PASS | 1ae3d279-5839-4b5f-b3e8-4a22386a4d7c | ACCESS_DENIED |
| 2026-09-20T20:45:41.980Z | 2498 | RISK_QUEUE | GET /admin/risk-reviews?sort=PRIORITY&page=0&size=5 | 200 | 200 | PASS | f8ef256d-0d12-4e3e-8d65-a3047b094c1e | 1 items |
| 2026-09-20T20:45:42.042Z | 2498 | RISK_DETAIL | GET /admin/risk-reviews/98 | 200 | 200 | PASS | f2a29fee-48f7-4a9c-a3e8-aee5137c8e0f |  |
| 2026-09-20T20:45:42.095Z | 2498 | RISK_TIMELINE | GET /transactions/1051/audit?page=0&size=5 | 200 | 200 | PASS | 25c19281-6fe6-4e4c-9765-22fdba6fa260 | 2 items |
| 2026-09-20T20:45:42.124Z | 2498 | RISK_USER_ADMIN_DENIED | GET /admin/users | 403 | 403 | PASS | 00046a6b-5bdb-42fc-b1c1-a1e541a134e3 | ACCESS_DENIED |
| 2026-09-20T20:45:42.182Z | 2500 | AUDIT_LOGS | GET /audit-logs?page=0&size=5 | 200 | 200 | PASS | c83d292f-3ec0-460d-9cbc-496cd11cc889 | 81 items |
| 2026-09-20T20:45:42.368Z | 2500 | AUDIT_PAYMENTS | GET /audit/transactions?page=0&size=5 | 200 | 200 | PASS | 68dbe8ea-b575-4320-88fa-137a7c6fca19 | 46 items |
| 2026-09-20T20:45:42.465Z | 2500 | AUDIT_PAYMENTS_DETAIL | GET /audit/transactions/1553 | 200 | 200 | PASS | a4cc4835-0597-43eb-8864-60d1edd52102 |  |
| 2026-09-20T20:45:42.550Z | 2500 | AUDIT_REVIEWS | GET /audit/risk-reviews?page=0&size=5 | 200 | 200 | PASS | 50e095c9-f7ac-4539-aa19-8dfd3d873721 | 1 items |
| 2026-09-20T20:45:42.581Z | 2500 | AUDIT_REVIEWS_DETAIL | GET /audit/risk-reviews/98 | 200 | 200 | PASS | 73b55bc7-9963-44c3-a757-3cda26d52bb5 |  |
| 2026-09-20T20:45:42.785Z | 2500 | AUDIT_LEDGER | GET /audit/ledger-postings?page=0&size=5 | 200 | 200 | PASS | 473dc3ad-c2cb-48d5-ae08-e524a27a0bd1 | 38 items |
| 2026-09-20T20:45:42.858Z | 2500 | AUDIT_LEDGER_DETAIL | GET /audit/ledger-postings/162 | 200 | 200 | PASS | d95a158c-33a1-4334-a667-b5fb945e2468 |  |
| 2026-09-20T20:45:43.055Z | 2500 | AUDIT_LEDGER_CHECKS | GET /audit/reconciliation/ledger?page=0&size=5 | 200 | 200 | PASS | e2105638-8ed4-43fc-863b-0f2e29377637 | 38 items |
| 2026-09-20T20:45:43.216Z | 2500 | AUDIT_RESERVATION_CHECKS | GET /audit/reconciliation/reservations?page=0&size=5 | 200 | 200 | PASS | 554cfce0-5934-4f25-b595-a34ba2b21fbf | 50 items |
| 2026-09-20T20:45:43.358Z | 2500 | AUDIT_EXCEPTIONS | GET /audit/exceptions?page=0&size=5 | 200 | 200 | PASS | c88a9a01-a7f9-43c2-b291-ff83312f827c | 2 items |
| 2026-09-20T20:45:43.441Z | 2500 | AUDIT_EXCEPTIONS_DETAIL | GET /audit/exceptions/38 | 200 | 200 | PASS | 43bb34f8-a7db-4613-b863-f86f3ae9983a | transaction 1074  |
| 2026-09-20T20:45:43.512Z | 2500 | AUDIT_POLICIES | GET /audit/risk-policies?page=0&size=5 | 200 | 200 | PASS | a46176e2-0234-4ed1-a908-e1adfabb0acf | 1 items |
| 2026-09-20T20:45:43.649Z | 2500 | AUDIT_POLICIES_DETAIL | GET /audit/risk-policies/AMOUNT_ONLY_V1 | 200 | 200 | PASS | 5a3443aa-5dad-4fc2-8629-b479def9f2cd |  |
| 2026-09-20T20:45:43.692Z | 2500 | AUDITOR_REVIEW_ACCESS_DENIED | GET /admin/risk-reviews | 403 | 403 | PASS | 2cc0ab7f-2e31-4ce1-bab7-6376c19c0efa | ACCESS_DENIED |
| 2026-09-20T20:45:43.725Z | 2468 | CSRF | GET /auth/csrf | 200 | 200 | PASS | 914512e2-6f3e-430f-be8c-ee60808d613c |  |
| 2026-09-20T20:45:43.783Z | 2468 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | 5f6fee8d-4591-4109-91ee-13885fc04319 |  |
| 2026-09-20T20:45:43.809Z | 2470 | CSRF | GET /auth/csrf | 200 | 200 | PASS | cc6d37a2-4c7a-4f08-a5c2-ed6f1fcad9db |  |
| 2026-09-20T20:45:43.876Z | 2470 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | 63053978-22ea-4316-b9c7-b2c69e6a9a09 |  |
| 2026-09-20T20:45:43.894Z | 2498 | CSRF | GET /auth/csrf | 200 | 200 | PASS | 2805503a-cbcd-4793-ba82-cfb89eb51760 |  |
| 2026-09-20T20:45:43.934Z | 2498 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | faa726e7-1594-4e89-b6de-487a694b580a |  |
| 2026-09-20T20:45:43.957Z | 2499 | CSRF | GET /auth/csrf | 200 | 200 | PASS | 4ab5f5e5-5dea-486c-b129-5aeba2708874 |  |
| 2026-09-20T20:45:44.007Z | 2499 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | 9a93792c-f1a5-4435-8f63-f4de920d1140 |  |
| 2026-09-20T20:45:44.031Z | 2500 | CSRF | GET /auth/csrf | 200 | 200 | PASS | b26281b6-6f90-4c54-9797-bffbfeee1087 |  |
| 2026-09-20T20:45:44.075Z | 2500 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | 4e8fc1b4-c22e-4ec8-8e83-1d295427c296 |  |
| 2026-09-20T20:50:41.080Z | 2470 | LOGIN | POST /auth/login | 200 | 200 | PASS | 4cc7343b-eb42-4d94-a250-13156a2bec29 | user 2470 |
| 2026-09-20T20:50:41.116Z | 2470 | PAYMENT_TEST_FUNDS | GET /accounts/1628/balance | 200 | 200 | PASS | c23b8b0a-f97f-46f8-9518-d7ae6b94fe50 |  |
| 2026-09-20T20:50:41.186Z | 2470 | AMOUNT_PRECISION_REJECT | POST /transactions | 400 | 400 | PASS | 147341c8-5244-41e9-8002-eb2fb7806ba7, 147341c8-5244-41e9-8002-eb2fb7806ba7 | VALIDATION_FAILED |
| 2026-09-20T20:50:41.214Z | 2470 | CATEGORY_REQUIRED_REJECT | POST /transactions | 400 | 400 | PASS | a73f34be-767c-4908-8faf-984fee90624e, a73f34be-767c-4908-8faf-984fee90624e | VALIDATION_FAILED |
| 2026-09-20T20:50:41.234Z | 2470 | CATEGORY_LOW_BOUNDARY_REJECT | POST /transactions | 400 | 400 | PASS | 2c8e6c64-57bf-4010-bc1c-97594246c056, 2c8e6c64-57bf-4010-bc1c-97594246c056 | VALIDATION_FAILED |
| 2026-09-20T20:50:41.258Z | 2470 | OTHERS_PURPOSE_REJECT | POST /transactions | 400 | 400 | PASS | 4e0806a5-5e57-4f83-885a-029f11b54a2f, 4e0806a5-5e57-4f83-885a-029f11b54a2f | VALIDATION_FAILED |
| 2026-09-20T20:50:41.545Z | 2470 | MEDIUM_CREATE | POST /transactions | 201 | 201 | PASS | 740d9a52-2aa8-4d7e-9c78-cdc8438864e9 | transaction 1554 CREATED |
| 2026-09-20T20:50:41.611Z | 2470 | CREATE_SAME_KEY_REPLAY | POST /transactions | 201 | 201 | PASS | 36693779-b709-432e-aec8-ab9823105f89 | transaction 1554 CREATED |
| 2026-09-20T20:50:41.640Z | 2470 | CREATE_KEY_PAYLOAD_CONFLICT | POST /transactions | 409 | 409 | PASS | 07716a07-e09c-416c-8a29-c3ce10044c9d, 07716a07-e09c-416c-8a29-c3ce10044c9d | IDEMPOTENCY_KEY_REUSED |
| 2026-09-20T20:50:41.671Z | 2470 | AUTHORIZE_UNCONFIRMED_REJECT | POST /transactions/1554/authorize | 400 | 400 | PASS | 743ed002-df37-4663-adb4-ba595514bd23, 743ed002-df37-4663-adb4-ba595514bd23 | VALIDATION_FAILED |
| 2026-09-20T20:50:42.371Z | 2470 | MEDIUM_AUTHORIZE | POST /transactions/1554/authorize | 200 | 200 | PASS | 9d8738d6-2707-4f2f-b246-27c29ec606f7 | transaction 1554 PROTECTED |
| 2026-09-20T20:50:42.398Z | 2470 | PROTECTION_HINTS | GET /transactions/1554 | 200 | 200 | PASS | d843a526-8e71-4133-974b-2034f8146d22 | transaction 1554 PROTECTED |
| 2026-09-20T20:50:42.477Z | 2470 | PROTECTED_CANCEL | POST /transactions/1554/cancel | 200 | 200 | PASS | 3d610f3e-65ef-4a4a-bdda-720df5df1259 | transaction 1554 CANCELLED |
| 2026-09-20T20:50:42.512Z | 2470 | CANCEL_SAME_KEY_REPLAY | POST /transactions/1554/cancel | 200 | 200 | PASS | b551f67a-7617-4198-ae61-f29fb8f3a19d | transaction 1554 CANCELLED |
| 2026-09-20T20:50:42.535Z | 2470 | CANCELLED_DETAIL | GET /transactions/1554 | 200 | 200 | PASS | 6b858d4e-39cd-498f-8a9b-9e3dc4822bff | transaction 1554 CANCELLED |
| 2026-09-20T20:50:42.587Z | 2470 | HIGH_CREATE | POST /transactions | 201 | 201 | PASS | adbf19cf-fba6-4e23-9e66-f1fee38b9943 | transaction 1555 CREATED |
| 2026-09-20T20:50:42.664Z | 2470 | HIGH_AUTHORIZE | POST /transactions/1555/authorize | 200 | 200 | PASS | 73e3e0e4-2f2d-4319-93e3-b545771266c9 | transaction 1555 PROTECTED |
| 2026-09-20T20:50:42.733Z | 2470 | HIGH_CANCEL | POST /transactions/1555/cancel | 200 | 200 | PASS | 53fa44ee-97d2-4f2d-b598-420f7366a239 | transaction 1555 CANCELLED |
| 2026-09-20T20:50:42.798Z | 2470 | VERY_HIGH_CATEGORY_CREATE | POST /transactions | 201 | 201 | PASS | dbda9a09-f16f-4e79-938d-7a88100c3d22 | transaction 1556 CREATED |
| 2026-09-20T20:50:42.858Z | 2470 | CREATED_CANCEL | POST /transactions/1556/cancel | 200 | 200 | PASS | e4b83e5e-954b-49b4-ba61-7950b427beaf | transaction 1556 CANCELLED |
| 2026-09-20T20:50:42.911Z | 2470 | INSUFFICIENT_FUNDS_CREATE | POST /transactions | 201 | 201 | PASS | 506111bd-da98-469b-9e9d-f2c94cdac553 | transaction 1557 CREATED |
| 2026-09-20T20:50:42.985Z | 2470 | INSUFFICIENT_FUNDS_AUTHORIZE | POST /transactions/1557/authorize | 200 | 200 | PASS | d654aab5-4f7a-4e65-bd05-acb44c91cf45 | transaction 1557 FAILED |
| 2026-09-20T20:50:43.034Z | 2470 | TERMINAL_AUTHORIZE_REJECT | POST /transactions/1557/authorize | 409 | 409 | PASS | a81a2405-ef69-41a2-8ed3-44794f24c0a7, a81a2405-ef69-41a2-8ed3-44794f24c0a7 | STATE_TRANSITION_CONFLICT |
| 2026-09-20T20:50:43.058Z | 2470 | PAYMENT_TEST_FUNDS | GET /accounts/1628/balance | 200 | 200 | PASS | 7d056373-6fcf-4f34-b0c4-08e30fe407d3 |  |
| 2026-09-20T20:50:43.061Z | 2470 | FUNDS_RECONCILED | GET balance assertions | 200 | unchanged | PASS | — | 1628: current 200000.00 -> 200000.00; reserved 0.00 -> 0.00 |
| 2026-09-20T20:50:43.086Z | 2470 | CSRF | GET /auth/csrf | 200 | 200 | PASS | 170c5d90-36f7-432e-91d2-b353d29fe84f |  |
| 2026-09-20T20:50:43.121Z | 2470 | LOGOUT | POST /auth/logout | 204 | 204 | PASS | e87e2d9d-b379-464a-81e0-c4a459ff2d12 |  |
