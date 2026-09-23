# SafePay Aditya — Project Update Summary

## Purpose

SafePay is a **simulated risk-adaptive pre-settlement transaction-control layer**. It does not connect to real banking or payment rails. All balances, transfers, and ledger entries are local simulation data.

## Current implementation status

### 1. Verified internal beneficiaries

Bank-account beneficiaries are now limited to active SafePay accounts.

- The account number must belong to an active SafePay customer account.
- The beneficiary name, bank name, and IFSC must match that account.
- Random, external, or mismatched bank details are rejected.
- The customer sees a simple message: “This account is not registered with SafePay, or its details do not match.”
- Bank name is provided as a controlled dropdown rather than unrestricted free text.

This prevents a customer from creating a beneficiary that cannot receive a simulated SafePay transfer.

### 2. Recipient account linkage

Once a beneficiary is verified, SafePay stores the matching destination account on the beneficiary and on every newly created payment.

- A payment cannot be created for an unverified bank beneficiary.
- A sender cannot pay their own account through a beneficiary.
- A historical payment without a destination account is not treated as a valid internal transfer.

### 3. SafePay PIN

SafePay PIN confirmation is part of payment creation.

- Users without a PIN are prompted to create and confirm a six-digit SafePay PIN once.
- The PIN is saved as a BCrypt hash, not plain text.
- Users with a saved PIN enter it to confirm future payments.
- Incorrect PIN input is rejected with customer-readable feedback.
- A user can request a PIN reset.
- A system administrator can approve or reject that reset request.
- After approval, the user can save a replacement PIN.

### 4. Settlement: sender debit and recipient credit

Settlement now performs a full simulated internal transfer.

- The sender’s reserved amount is consumed and their current balance is debited.
- The recipient’s destination account is credited by the same amount.
- A settlement creates one immutable debit ledger entry and one immutable credit ledger entry.
- The recipient balance update is an explicit locked database update, preventing it from being skipped by persistence tracking.
- Replaying settlement for an already settled payment is read-only and does not create a second effect.

Manual proof completed for a ₹500 payment:

- Sender account ending `8902` was debited.
- Recipient account ending `6789` was credited from ₹4,26,000 to ₹4,26,500.
- Oracle ledger showed exactly one ₹500 debit and one ₹500 credit for the same transaction.

### 5. Recipient transaction history (Phase 4)

Payment history now includes both sides of an internal SafePay transfer.

- Senders continue to see their outgoing payment.
- Recipients see the incoming payment in Transactions.
- Recipient dashboard activity includes the incoming payment.
- Incoming rows are labelled “Money received.”
- Dashboard heading was changed from “Recent outgoing payments” to “Recent payments.”

## Customer error-message updates

Technical details, internal references, duplicate identifiers, and database error text have been removed from normal customer-facing errors.

Examples:

- Invalid login: a simple authentication message.
- Invalid beneficiary: clear SafePay-registration/matching message.
- Failed beneficiary creation: prominent error panel without internal codes.
- Payment errors: customer-readable wording instead of raw service/database text.

## Database migrations already present

The following migrations were introduced during the earlier implementation work:

- `V14` — beneficiary destination uniqueness correction.
- `V15` — verified beneficiary to SafePay account linkage.
- `V16` — payment to verified destination-account linkage.
- `V17` — hashed SafePay PIN storage.
- `V18` — SafePay PIN reset status.

No further migration should be created without explicit approval.

## Automated verification completed

Focused backend tests completed successfully:

- `AccountFinancialStateTest`
- `BeneficiaryServiceImplTest`
- `SettlementServiceImplTest`

Result: **39 tests passed**.

These checks cover beneficiary validation, account debit/credit behaviour, and settlement workflow behaviour.

## Full Oracle integration suite status

The full Maven test suite currently does **not** pass. This is not a production startup failure.

The existing Oracle integration tests were written before verified internal beneficiaries became mandatory. Their fixtures create arbitrary or UPI-only beneficiaries, so the new valid security check correctly rejects them before their original test scenario begins.

Required follow-up:

- Update the Oracle test fixtures to create a recipient user and active recipient account.
- Create each fixture beneficiary with matching recipient account number, name, bank, IFSC, and destination-account link.
- Update transaction-history tests to expect both sent and received payments after Phase 4.
- Rerun the full integration suite.

## Manual Phase 5 verification checklist

1. Add a valid internal beneficiary with exact SafePay account details — should succeed.
2. Add an unknown account — should be rejected.
3. Use a correct account number with wrong name, bank, or IFSC — should be rejected.
4. Send a low-risk ₹500 payment — sender decreases by ₹500; recipient increases by ₹500.
5. Confirm both users see the payment; recipient sees “Money received.”
6. Create a protected payment and cancel it before expiry — sender funds are released and recipient is not credited.
7. Refresh/reopen a settled payment repeatedly — no duplicate debit or credit.
8. In SQL Developer, confirm each settled transfer has exactly two ledger entries: equal debit and credit amounts.

## Run instructions

### Backend

From `C:\Users\Shreya Ojha\Desktop\Safepay_Aditya\backend`:

```powershell
java -jar .\target\safepay-0.0.1-SNAPSHOT.jar --spring.flyway.locations=classpath:db/migration,classpath:db/showcase --safepay.protection-scheduler.enabled=true --safepay.settlement-processor.enabled=true --safepay.notification-dispatcher.enabled=false --management.health.mail.enabled=false
```

Health check:

```powershell
curl.exe -s http://localhost:8080/actuator/health
```

Expected response contains `"status":"UP"`.

### Frontend

From `C:\Users\Shreya Ojha\Desktop\Safepay_Aditya\frontend\SafePayJet`:

```powershell
npx.cmd ojet serve --server-port=8001 --server-only --livereload=false --watch-files=false
```

Open `http://localhost:8001`.

## Important operating notes

- Use **PowerShell**, not Command Prompt, for PowerShell environment-variable commands.
- If port 8001 is occupied, stop the process using that port before starting the frontend.
- Use Java 21 for this clone; Java 17 produces a `release version 21 not supported` build error.
- Do not include Oracle passwords in logs, screenshots, commits, or messages.
