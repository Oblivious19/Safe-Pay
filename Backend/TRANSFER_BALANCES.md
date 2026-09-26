# Sender and receiver balance updates

Implemented 24 September 2026. SafePay remains a simulation; no real bank rails are used.

## What changed

- The beneficiary's bank account number resolves an internal receiver using the unique `ACCOUNT.ACCOUNT_NUMBER`. `BENEFICIARIES.ACCOUNT_ID` is the **sender's address book**, not the recipient.
- Immediate LOW settlement, timed MEDIUM/HIGH settlement and admin-approved HARD_HOLD settlement debit the sender and credit the internal receiver in the same database transaction as payment state and audit records.
- Both account rows are locked in ascending ID order. Exact `BigDecimal` amounts, existing minimum balance/reservations, state checks and idempotency remain enforced. A failed credit or audit rolls the whole settlement back.
- `TRANSACTION_DB.TO_ACCOUNT_ID` records the internal destination. Receivers can read their own settled incoming receipts, but cannot cancel the sender's payments or see pending/declined transfers. Sender and receiver history use DEBIT and CREDIT respectively.
- Dashboard, payment history, Send Money, Profile and the admin selected-user account view refresh from the server every three seconds while visible. Background requests do not clear a draft, change account selection or show the full-page loader. Customer balance pages also refresh on window focus.
- Transfers between two different accounts of the same owner are supported. The dashboard signs a transfer according to the selected account. Paying the exact same account is rejected.

## Database deployment

`db/24-internal-transfer-recipient.sql` adds a nullable recipient column, a foreign key and an index. It is rerunnable and contains no balance updates or historical backfill. It was applied to the local Oracle database configured in this backend during this task. The local H2 profile creates the column through Hibernate; the Oracle profile continues to use schema validation.

`db/ApplyReceiverMigration.java` can check that local schema using Java 17 and the existing Oracle JDBC driver. Its default mode is read-only; the explicit `apply` argument executes the SQL. It refuses remote database URLs and does not print credentials.

Restart the backend **from the updated Backend project** and restart/rebuild the frontend, then hard-refresh the browser. A previously built JAR (including an older `dist` launcher target) does not contain these code changes. Build with `mvn package` and use `target/SafePay-0.0.1-SNAPSHOT.jar`, or run the updated project from VS Code with the `oracle` profile.

## Compatibility

- Already settled payments are not re-credited. Historical missing receiver credits need a separately reviewed reconciliation, not a blind replay.
- Existing pending payments resolve an internal receiver at settlement if they have no saved destination; their existing risk tier and deadlines are unchanged.
- An external beneficiary with no matching SafePay account retains the existing simulated outbound-only behavior. No local receiver account is invented.
- The current Account model has no IFSC column: internal resolution uses its globally unique account number. This is not real interbank routing.

## Manual verification

1. Use two existing customers in separate browser sessions (for example normal and private windows). Keep the receiver dashboard open.
2. On the sender, add/select a beneficiary whose full bank account number equals the receiver's actual SafePay account number. Ensure the sender has enough unreserved balance above the INR 5,000 minimum.
3. Send INR 500. Once SETTLED, the sender decreases by 500 and receiver increases by 500. Both dashboards update within the next visible-page poll, without a manual reload. The receiver history shows `+ INR 500` and the sender's name; sender history shows `- INR 500`.
4. Repeat with INR 25,000 / INR 75,000: no debit or credit occurs during protection. Both balances change only after the database settles the payment. Cancel within protection: neither balance changes.
5. An amount above INR 1,00,000 remains held until admin approval. Approve once: both balances change once. Decline: neither changes. Replaying the same request/approval does not move funds twice.
6. Switch among linked accounts and open Profile or admin Users & accounts: balances match the selected account, not another cached account.

Read-only database verification (substitute the two intended account IDs):

```sql
SELECT account_id, account_number, balance, updated_at
FROM account WHERE account_id IN (:sender_id, :receiver_id);

SELECT transaction_id, from_account_id, to_account_id, amount, state, settled_at
FROM transaction_db WHERE transaction_id = :transaction_id;
```

Automated verification uses isolated H2 Oracle-mode databases, not mutations of customer balances in the local Oracle database. It covers all risk tiers, duplicate/concurrent requests, opposite-direction transfers, rollback, ownership, legacy pending payments, same-owner accounts and frontend polling. Browser checks use a separately injected sample-data fixture, never production fallback data.

Final verification: backend `mvn package` succeeded (529 tests: 526 passed, 3 skipped); frontend typecheck, all 219 tests and the Oracle JET build succeeded. Browser checks confirmed the receiver dashboard gained INR 1,000 and displayed an incoming credit without reloading; an initially empty Transactions page also picked up the receipt and displayed the sender details. The temporary preview was stopped; the user's existing application servers were not stopped or replaced.
