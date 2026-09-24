# Customer cancellation of hard-held payments

Explicit user-requested policy change, 24 September 2026: the sender may cancel a
`HARD_HOLD` payment before administrator approval. This supersedes the earlier
restriction on customer cancellation; it does not allow customers to approve or
release payments. Existing medium/high protection deadlines are unchanged.

- The existing session/CSRF-protected `POST /api/transactions/{id}/cancel` endpoint
  requires an `Idempotency-Key` and verifies sender ownership.
- Cancellation locks the source account, checks the database state and version,
  and atomically records `HARD_HOLD -> CANCELLED`, cancellation time/key and the
  sender's audit event. No balance is debited or credited; the reservation is freed.
- Admin approval uses the same source-account lock. Approval and cancellation
  cannot both win. A settled payment cannot be cancelled or reversed by this route.
- Cancelled payments disappear from the admin review queue on its next poll.
- Customer responses expose `canCancel: true` for hard holds, without a countdown.
  Dashboard/history actions and the send-money receipt use this shared permission.
- Cancellation confirmations use JET's supported dialog open/close API and bound
  button actions; the unsupported `opened` attribute is no longer used on dialogs.
- Card-level **Refresh status** buttons are removed. Status still updates
  automatically; temporary receipt-network failures keep retrying. The main
  history-page Refresh and balance-details refresh are unchanged.

## Verify

Restart the updated backend and frontend (do not launch an older JAR), then hard
refresh the browser. Using test accounts with sufficient available funds:

1. Create a payment above INR 1,00,000. Its receipt and Transactions should offer
   cancellation while awaiting review, without a Refresh status button.
2. Cancel it. Expect Cancelled, released reserved funds, unchanged sender/receiver
   balances, and removal from the admin queue after polling.
3. For another held test payment, approve first as admin. Cancellation must no
   longer be offered; a stale cancellation request must fail without reversing it.
4. Confirm medium/high cancellations still depend on their database deadlines.
5. Temporarily interrupt connectivity: the receipt retains its last confirmed
   status and recovers automatically once connectivity returns.

No schema migration or historical-payment rewrite is needed for this change.

Automated verification: backend package build, 536 tests (533 passed, 3 skipped);
frontend type check, 228 tests passed, and JET build. Database tests use isolated
H2 databases, not the saved Oracle customer accounts.
