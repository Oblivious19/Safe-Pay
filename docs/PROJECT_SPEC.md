# SafePay — Project Specification

## 1. Project Overview

**Project Name:** SafePay  
**Project Type:** Simulated Banking Web Application  
**Primary Goal:** Build a risk-adaptive payment protection and transaction-control platform.

SafePay evaluates a payment before simulated settlement and applies the appropriate level of friction:

- **Low risk:** instant release
- **Medium risk:** stronger confirmation / short protection
- **High risk:** temporary protection window
- **Very high risk:** hard hold and additional verification / Maker-Checker

### Core principle

> **Fast when safe. Careful when necessary.**

### Important scope statement

SafePay is a **simulation** of a banking transaction-control layer. It does not move real money, integrate with live UPI/IMPS/NEFT/RTGS rails, or reverse real settled transactions.

---

# 2. Problem Statement

Digital payments are fast and convenient, but customers and business users can still make accidental or unusual/high-value payment decisions.

Examples:

- Wrong beneficiary selected
- Newly added beneficiary used for a large transfer
- Unusually large amount
- Payment from an unfamiliar device or location
- Corporate payment requiring a second authorized person

Existing banking/payment ecosystems already use authentication, beneficiary verification, fraud monitoring, transaction controls, and dispute/recovery processes.

Therefore, SafePay does **not** claim to invent these controls.

The project focuses on:

> **Using transaction-risk information to determine the appropriate amount of friction or additional authorization before a payment is released.**

---

# 3. Product Concept

The central flow is:

```text
PAYMENT
   ↓
AUTHENTICATION
   ↓
RISK ASSESSMENT
   ↓
POLICY DECISION
   ↓
RELEASE / PROTECT / HOLD
```

The key distinction is:

### Risk Engine

Answers:

> **How risky is this transaction?**

### Policy Engine

Answers:

> **What should we do about that risk?**

This separation should be maintained throughout the implementation.

---

# 4. Adaptive Friction

SafePay should **not delay every transaction**.

The application should keep ordinary payments fast and only introduce additional friction when a transaction warrants it.

| Example | Expected Behaviour |
|---|---|
| ₹500 to regular beneficiary | Instant settlement |
| ₹25,000 unusual payment | Strong confirmation / short protection |
| ₹4,50,000 to new beneficiary | Protection window |
| ₹10,00,000 + unusual context | Hard hold / verification |

The exact risk thresholds and protection durations are **project configuration values**.

They are not presented as universal banking rules.

---

# 5. User Roles

## Customer

Can:

- Login
- View account information
- View balance
- Manage beneficiaries
- Create payments
- See risk explanations
- Cancel eligible protected payments
- Release/continue eligible protected payments
- View transaction history
- Configure safety preferences
- Raise simulated post-settlement disputes

## Maker

Corporate user who can:

- Create high-value payments
- Enter payment purpose/reference
- Submit controlled payments for approval

## Checker

Authorized corporate/bank user who can:

- View pending approvals
- Review payment information
- Review risk factors
- Approve payments
- Reject payments
- Add comments

### Maker-Checker rule

> A Maker must not approve their own payment.

## Admin

Can:

- Manage users
- Manage risk factors
- Manage protection rules
- Monitor transactions
- View high-risk transactions
- Review approvals
- Manage disputes
- View audit logs
- View reports

---

# 6. Transaction State Machine

## Main states

```text
CREATED
AUTHORIZED
RISK_ASSESSED
PROTECTED
HARD_HOLD
PENDING_APPROVAL
CANCELLED
RELEASED
SETTLED
REJECTED
DISPUTED
```

## Main flow

```text
CREATED
   ↓
AUTHORIZED
   ↓
RISK_ASSESSED
   │
   ├── LOW ─────────────→ SETTLED
   │
   ├── MEDIUM / HIGH ──→ PROTECTED
   │                         ├── CANCELLED
   │                         └── SETTLED
   │
   └── VERY HIGH ──────→ HARD_HOLD
                              ↓
                       PENDING_APPROVAL
                          ├── SETTLED
                          └── REJECTED
```

After settlement:

```text
SETTLED
   ↓
DISPUTED
   ↓
UNDER_REVIEW
   ├── RESOLVED
   └── REJECTED
```

## State rules

- `SETTLED` cannot be cancelled.
- Cancellation is allowed only when status is `PROTECTED` and the protection window is still active.
- Backend/server time is authoritative for protection expiry.
- Frontend countdown is visual only.
- Maker and Checker must be different users.
- Completed financial history should not be physically deleted.

---

# 7. Risk Engine

Start with a **transparent rule-based engine**.

Do not make AI/ML the core MVP.

## Initial risk factors

| Factor | Example Weight |
|---|---:|
| New beneficiary | +30 |
| High amount | +25 |
| First transaction | +20 |
| Unusual amount | +15 |
| New device | +20 |
| Unusual location | +15 |
| Unusual time | +5 |

## Suggested risk tiers

| Score | Risk Tier |
|---:|---|
| 0–30 | LOW |
| 31–60 | MEDIUM |
| 61–85 | HIGH |
| 86+ | VERY_HIGH |

## Example

```text
New beneficiary       +30
High amount           +25
First transaction     +20
--------------------------------
Risk Score              75
Risk Tier               HIGH
```

The system should record **which factors contributed to the score**.

---

# 8. Policy Engine

The Policy Engine converts risk into an action.

Example:

```text
LOW
  ↓
INSTANT_SETTLEMENT

MEDIUM
  ↓
CONFIRM / SHORT_PROTECTION

HIGH
  ↓
PROTECTION_WINDOW

VERY_HIGH
  ↓
HARD_HOLD
```

The policy should be configurable through database-backed rules.

---

# 9. Protection Window

For eligible transactions:

```text
RISK_ASSESSED
      ↓
PROTECTED
      ↓
PROTECTION_START
      ↓
PROTECTION_END
```

Customer UI:

```text
PAYMENT PROTECTED

Amount: ₹4,50,000

Why?
• New beneficiary
• First payment
• High amount

Remaining: 00:42

[CANCEL]
[CONTINUE]
```

Backend must validate:

```text
status = PROTECTED
AND current server time < protection_end
AND user is authorized
```

Then:

```text
CANCEL → CANCELLED
CONTINUE / RELEASE → SETTLED
```

Protection expiry behaviour must be defined by project policy.

---

# 10. Very High Risk / Maker-Checker

Example:

```text
₹10,00,000
+ New beneficiary
+ New device
+ Unusual location
```

Result:

```text
VERY_HIGH
   ↓
HARD_HOLD
   ↓
PENDING_APPROVAL
```

Checker reviews:

```text
Transaction
Amount
Maker
Beneficiary
Risk Score
Risk Factors
Purpose
```

Then:

```text
APPROVE → SETTLED
REJECT  → REJECTED
```

---

# 11. Retail vs Corporate Mode

## Retail

Main problem:

> **Customer catches an accidental payment before settlement.**

Flow:

```text
Risky payment
   ↓
PROTECTED
   ↓
CANCEL / CONTINUE
```

## Corporate

Main problem:

> **High-value payment requires controlled authorization.**

Flow:

```text
Maker
  ↓
Payment
  ↓
Risk / Policy
  ↓
HOLD
  ↓
Checker
  ↓
APPROVE / REJECT
```

---

# 12. Post-Settlement Dispute

SafePay must distinguish **Undo** from **Dispute**.

## Before settlement

```text
PROTECTED → CANCELLED
```

## After settlement

```text
SETTLED → DISPUTED
```

A dispute is a simulated workflow for tracking a customer complaint.

It does not perform real recovery or chargeback processing.

---

# 13. Database Design

## Core tables

```text
ROLES
USERS
BANKS
ACCOUNTS
BENEFICIARIES

TRANSACTIONS
TRANSACTION_CONTEXT

RISK_FACTORS
TRANSACTION_RISK_FACTORS
PROTECTION_RULES
USER_SAFETY_SETTINGS

APPROVALS
DISPUTES
AUDIT_LOG
```

## Important design rule

Separate **risk** from **transaction status**.

Example:

```text
RISK_TIER = HIGH
STATUS = PROTECTED
```

Later:

```text
RISK_TIER = HIGH
STATUS = CANCELLED
```

Both are valid.

---

# 14. Important Transaction Fields

Recommended fields:

```text
TRANSACTION_ID
TRANSACTION_REFERENCE
SENDER_ACCOUNT_ID
BENEFICIARY_ID

AMOUNT
CURRENCY

TRANSACTION_TYPE
CHANNEL
PURPOSE

STATUS
STATUS_REASON
ERROR_CODE

CREATED_AT
AUTHORIZED_AT
PROTECTION_START
PROTECTION_END
SETTLED_AT

RISK_SCORE
RISK_TIER

PROTECTION_REQUIRED
PROTECTION_SECONDS

CREATED_BY
```

## Why these matter

### TRANSACTION_REFERENCE

A human-searchable payment reference.

### STATUS_REASON

Explains why a transaction failed or changed state.

### ERROR_CODE

Supports business vs technical failure.

### Timestamps

Allow the application to determine whether an action occurred before or after settlement.

---

# 15. Transaction Context

Create a separate context structure/table for signals such as:

```text
CONTEXT_ID
TRANSACTION_ID
DEVICE_ID
DEVICE_KNOWN
IP_ADDRESS
CITY
COUNTRY
LOCATION_KNOWN
CHANNEL
SESSION_ID
```

Example:

```text
DEVICE_KNOWN = N
LOCATION_KNOWN = Y
```

The Risk Engine can then derive:

```text
NEW_DEVICE = TRUE
UNUSUAL_LOCATION = FALSE
```

Keep raw context separate from derived risk decisions where practical.

---

# 16. Banking Failure Handling

The system should distinguish at least:

## Business failure

Examples:

```text
INVALID_PIN
INVALID_BENEFICIARY
LIMIT_EXCEEDED
INSUFFICIENT_FUNDS
```

## Technical failure

Examples:

```text
BANK_TIMEOUT
NETWORK_UNAVAILABLE
PAYMENT_SERVICE_UNAVAILABLE
```

## Policy outcome

Examples:

```text
SAFE_PAY_PROTECTED
ADDITIONAL_APPROVAL_REQUIRED
```

Avoid storing only:

```text
STATUS = FAILED
```

Prefer:

```text
STATUS = FAILED
STATUS_REASON = TECHNICAL_FAILURE
ERROR_CODE = T001
```

The project's failure codes are internal simulation values.

---

# 17. Duplicate Payment / Idempotency

Payment requests can be retried after timeouts.

The application should have a request reference/idempotency key such as:

```text
REQUEST_REFERENCE = REQ-20260828-00125
```

The same request should not create two payments accidentally.

This is an important reliability concept.

---

# 18. Oracle Requirements

Use:

- Primary keys
- Foreign keys
- `NOT NULL`
- `UNIQUE`
- `CHECK` constraints
- Oracle sequences for generated IDs
- Indexes for common queries
- Seed/sample data
- Complex reporting views

## Suggested sequences

```text
SEQ_ROLE_ID
SEQ_USER_ID
SEQ_BANK_ID
SEQ_ACCOUNT_ID
SEQ_BENEFICIARY_ID
SEQ_TRANSACTION_ID
SEQ_RISK_FACTOR_ID
SEQ_RULE_ID
SEQ_SETTING_ID
SEQ_APPROVAL_ID
SEQ_DISPUTE_ID
SEQ_AUDIT_ID
```

---

# 19. Reporting Views

Create at least:

```text
VW_TRANSACTION_DASHBOARD
VW_RISK_SUMMARY
VW_PENDING_APPROVALS
VW_CUSTOMER_PROTECTION_ANALYSIS
```

Use these to power the Admin Dashboard.

Views should demonstrate:

- JOIN
- GROUP BY
- COUNT
- SUM
- AVG
- CASE expressions

---

# 20. Audit Logging

Record important actions such as:

```text
LOGIN
CREATE_TRANSACTION
RISK_ASSESSED
PROTECT_TRANSACTION
CANCEL_TRANSACTION
RELEASE_TRANSACTION
SETTLE_TRANSACTION
APPROVE_TRANSACTION
REJECT_TRANSACTION
CREATE_DISPUTE
```

Each audit event should record enough context to answer:

```text
WHO?
WHAT?
WHEN?
WHICH TRANSACTION?
OLD STATE?
NEW STATE?
WHY?
```

Example:

```text
Transaction: 10003
Action: CANCEL_TRANSACTION
Old Status: PROTECTED
New Status: CANCELLED
User: 1001
Timestamp: 10:35:22
Reason: Customer cancelled during protection window
```

---

# 21. Web Application Screens

## Customer

```text
Login
Dashboard
Send Money
Protection / Payment Result
Transaction History
Beneficiaries
Safety Settings
Disputes
```

## Admin

```text
Dashboard
Transactions
High-Risk Transactions
Pending Approvals
Risk Rules
Protection Rules
Disputes
Audit Logs
Reports
```

---

# 22. Backend Architecture

Recommended:

```text
Web Frontend
      ↓
REST API
      ↓
Spring Boot
      ↓
Controller
      ↓
Service
      ↓
Repository
      ↓
Oracle
```

Services:

```text
AuthService
BeneficiaryService
TransactionService
RiskEngineService
PolicyEngineService
ProtectionService
ApprovalService
DisputeService
AuditService
```

Use `BigDecimal` for monetary calculations.

---

# 23. Security Requirements

- Never store plaintext passwords.
- Enforce role-based access in the backend.
- Customers can access only their own transactions.
- Admin APIs require appropriate authorization.
- Validate every state transition server-side.
- Do not trust the frontend countdown.
- Use synthetic data only.
- Do not expose full account information unnecessarily.
- Do not connect the project to real payment credentials or payment rails.

---

# 24. MVP

The first release must prove:

```text
LOGIN
  ↓
SELECT BENEFICIARY
  ↓
CREATE PAYMENT
  ↓
RISK ASSESSMENT
  ↓
LOW → SETTLED

HIGH → PROTECTED
          ↓
      CANCEL / CONTINUE

VERY HIGH → HOLD
              ↓
           CHECKER
              ↓
        APPROVE / REJECT
```

MVP includes:

- Authentication
- Accounts
- Beneficiaries
- Payment creation
- Rule-based risk engine
- Policy engine
- Protection
- Cancel/release
- Hard hold
- Maker-Checker
- Transaction history
- Audit log
- Basic Admin Dashboard

---

# 25. Future Scope

Potential future enhancements:

- ML-based risk scoring
- More sophisticated behavioural profiling
- Advanced anomaly detection
- Device fingerprinting
- Real-time notifications
- More advanced customer-specific policies
- Integration with external payment systems, subject to applicable controls
- Advanced recovery/dispute workflows

These should be clearly labelled **future work**.

---

# 26. Main Project Risks / Challenges

## Existing risk checks

The signals we use are already established concepts.

**Response:** Our focus is risk-to-control orchestration, not inventing fraud detection.

## Customer friction

Too much protection could annoy customers.

**Response:** Adaptive friction and instant low-risk payments.

## Real payment integration

A standalone college application cannot simply pause live UPI/IMPS transactions.

**Response:** Simulate the transaction lifecycle and explain that a real deployment would require integration inside a bank's authorization/payment-processing architecture.

## False positives

Legitimate large payments may be protected.

**Response:** Configurable policies and transparent risk explanations.

## Timer manipulation

Frontend timers can be manipulated.

**Response:** Backend controls expiry.

## Scope creep

Too many features can delay the core system.

**Response:** Finish the MVP first.

---

# 27. What We Should NOT Claim

Do not claim:

- “We invented fraud detection.”
- “Banks cannot detect these risks.”
- “SafePay can reverse any UPI/IMPS payment.”
- “The 60-second timer works on live UPI.”
- “SafePay prevents all fraud.”

Preferred wording:

> **SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.**

---

# 28. Recommended Development Order

```text
1. Freeze requirements
2. Finalize ER diagram
3. Finalize state machine
4. Create Oracle tables
5. Create sequences and constraints
6. Insert synthetic data
7. Build Spring Boot project
8. Connect Oracle
9. Build payment API
10. Build Risk Engine
11. Build Policy Engine
12. Implement protection
13. Implement cancel/release
14. Implement Maker-Checker
15. Build customer UI
16. Build admin UI
17. Build Oracle reporting views
18. Add audit and disputes
19. Test state transitions
20. Prepare final demo
```

---

# 29. First Technical Milestone

Before building the complete web UI, the backend must demonstrate:

### Request

```http
POST /api/transactions
```

### Input

```json
{
  "senderAccountId": 5001,
  "beneficiaryId": 2002,
  "amount": 450000
}
```

### Example output

```json
{
  "transactionId": 10001,
  "amount": 450000,
  "riskScore": 75,
  "riskTier": "HIGH",
  "status": "PROTECTED",
  "protectionSeconds": 60
}
```

Then:

```http
POST /api/transactions/10001/cancel
```

Example:

```json
{
  "transactionId": 10001,
  "previousStatus": "PROTECTED",
  "status": "CANCELLED"
}
```

This is the core proof that SafePay works.

---

# 30. Definition of Done

The MVP is complete when this entire flow works without manually modifying the database:

```text
User Login
   ↓
Select Beneficiary
   ↓
Create Payment
   ↓
Risk Assessment
   ↓
Policy Decision
   ↓
Release / Protect / Hold
   ↓
Cancel / Release / Approve / Reject
   ↓
Transaction State Updated
   ↓
Audit Record Created
   ↓
Admin Dashboard Updated
```

All invalid state transitions must be rejected by the backend.

---

# 31. Final Project Positioning

> **SafePay is a simulated banking transaction-control platform that uses transaction risk signals to determine the appropriate amount of friction before settlement — instant release for normal payments, temporary protection for selected transactions, and additional authorization for high-risk payments.**

### Tagline

**Fast when safe. Careful when necessary.**
