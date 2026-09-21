# Database change summary

Before: 2026-09-21T02:11:50.438566+05:30. After: 2026-09-21T02:37:17.166955+05:30. Both consistent read-only snapshots validated all 19 tables.

1474 changed non-secret field values across 107 rows: 100 inserted, 7 updated, zero deleted. Inserts include every captured field of each new row; this is not 1474 database operations. No direct SQL DML, DDL, grant or cleanup was executed.

All 50 current account balances are unchanged. Priya account 1626: current 350000.00 unchanged; reserved 180000.00 -> 180001.00; available 170000.00 -> 169999.00. The extra 1.00 belongs to browser test payment 1558, which is RELEASED while the settlement processor is disabled. Kavya account 1628 ends at current 200000.00, reserved 0.00 and available 200000.00, exactly its initial funds.

| Table | Before rows | After rows | Changed rows | Attribution |
|---|---:|---:|---:|---|
| ACCOUNT | 50 | 50 | 2 | See field-level report and action ledgers |
| APP_NOTIFICATION | 21 | 28 | 7 | See field-level report and action ledgers |
| APP_ROLE | 4 | 4 | 0 | No included-column differences |
| APP_USER | 52 | 52 | 5 | See field-level report and action ledgers |
| AUDIT_LOG | 72 | 123 | 51 | See field-level report and action ledgers |
| AUTH_SESSION | 24 | 44 | 20 | See field-level report and action ledgers |
| BENEFICIARY | 48 | 49 | 1 | See field-level report and action ledgers |
| IDEMPOTENCY_RECORD | 13 | 25 | 12 | See field-level report and action ledgers |
| LEDGER_ENTRY | 76 | 76 | 0 | No included-column differences |
| LEDGER_POSTING | 38 | 38 | 0 | No included-column differences |
| PAYMENT_OTP_CHALLENGE | 7 | 7 | 0 | No included-column differences |
| PAYMENT_TRANSACTION | 46 | 51 | 5 | See field-level report and action ledgers |
| PROTECTION_POLICY | 4 | 4 | 0 | No included-column differences |
| RISK_POLICY | 1 | 1 | 0 | No included-column differences |
| RISK_POLICY_BAND | 4 | 4 | 0 | No included-column differences |
| RISK_REVIEW | 5 | 5 | 0 | No included-column differences |
| TRANSACTION_EXCEPTION | 2 | 2 | 0 | No included-column differences |
| TRANSACTION_RISK_FACTOR | 23 | 27 | 4 | See field-level report and action ledgers |
| USER_ROLE | 34 | 34 | 0 | No included-column differences |

New payments: 1554 (5000.01 MEDIUM) and 1555 (25000.01 HIGH) were protected then cancelled; 1556 (100000.01 MEDICAL) was cancelled before authorization; 1557 (200000.01 MEDICAL) failed with INSUFFICIENT_AVAILABLE_BALANCE; 1558 (1.00 LOW) is RELEASED and reserved. No settlement/OTP/review progression was forced.

Beneficiary 1751 is a test-only UPI recipient and ends DISABLED. All pre-existing beneficiaries and payment rows are unchanged. All 20 new session rows are revoked. The five designated users changed only successful-login timestamps, update timestamps and optimistic-lock versions. User roles/security status are unchanged.

The 51 new audit rows comprise 10 successful logins, 10 refresh rotations, 10 logouts, 5 anonymous failed restore attempts, 4 intended role-denial checks and 12 payment events. Failed anonymous restore entries line up with browser startup/sign-out, do not identify a user and are a temporal attribution rather than a demonstrated request-correlation match. No unexplained financial or protected-table drift was found in included fields.

Raw snapshots exclude secret/hash/token/key/LOB fields. Assertions and differences cannot establish equality of excluded fields. Local snapshots are ignored by Git. No cleanup was attempted.

Detailed initial/end values and per-row attribution: DATA_CHANGES.md. Executed API requests: LIVE_API_TESTS.md. Browser actions: LIVE_BROWSER_TESTS.md. Deferred checks: PENDING_USER_TESTS.txt.
