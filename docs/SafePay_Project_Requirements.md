# SafePay — Project Requirements and Build Specification

## 1. Project Overview

**SafePay** is a simulated banking transaction protection system. It evaluates a payment before simulated settlement and decides whether to settle immediately, place it in a temporary protection window, or require enhanced verification.

**USP:** Give the customer or an authorized bank employee a controlled opportunity to cancel or verify a risky payment before it becomes settled.

> **Scope:** This project does not reverse real UPI/IMPS/bank transfers. It simulates the pre-settlement transaction layer.

---

## 2. Core Flow

```text
Customer / Maker
      |
      v
Create Payment
      |
      v
Validate + Authenticate
      |
      v
Risk Engine
      |
      +---------------------+----------------------+
      |                     |                      |
     LOW                MEDIUM / HIGH          VERY HIGH
      |                     |                      |
      v                     v                      v
   SETTLED              PROTECTED              HARD HOLD
                            |                      |
                       +----+----+             CHECKER
                       |         |             APPROVAL
                    CANCEL     RELEASE             |
                       |         |                 v
                       v         v              SETTLED
                   CANCELLED  SETTLED
```

---

## 3. Roles

### Customer

- Login and view account
- Add/update/deactivate beneficiaries
- Create payments
- See risk explanation
- Cancel or release eligible protected payments
- View transaction history
- Configure safety settings
- Raise a post-settlement dispute

### Maker

Used for corporate banking mode.

- Create high-value payments
- Enter purpose/reference
- Submit controlled payments for approval

### Checker

- View pending approvals
- Review transaction and risk factors
- Approve/reject payments
- Must not approve their own transaction

### Admin

- Manage users
- Manage risk factors
- Manage protection rules
- Monitor transactions
- Review approvals, disputes and audit logs
- View reports and dashboards

---

## 4. Transaction State Machine

```text
CREATED
   |
AUTHORIZED
   |
RISK_ASSESSED
   |
   +--------------------+------------------+
   |                    |                  |
SETTLED             PROTECTED          HARD_HOLD
                        |                  |
                   +----+----+          CHECKER
                   |         |             |
                CANCEL    RELEASE      +---+---+
                   |         |          |       |
                   v         v       APPROVE  REJECT
               CANCELLED   SETTLED      |       |
                                         v       v
                                      SETTLED  REJECTED
```

### Rules

- `SETTLED` cannot become `CANCELLED`.
- Cancellation is valid only while the transaction is `PROTECTED` and the protection period has not expired.
- The backend, not the frontend timer, is the authority on expiry.
- A maker cannot approve their own controlled transaction.
- Financial transactions should not be physically deleted.

---

## 5. Risk Engine

Use a **rule-based scoring engine** first. Store risk factors and weights in Oracle so an admin can modify them.

| Risk Factor | Example Condition | Weight |
|---|---|---:|
| New beneficiary | Recently added beneficiary | 30 |
| High amount | Above configured threshold | 25 |
| First transaction | No previous successful payment | 20 |
| Unusual amount | Much higher than customer's normal value | 15 |
| New device | Device not previously seen | 20 |
| Unusual location | Different from normal pattern | 15 |
| Unusual time | Unusual transaction time | 5 |

### Suggested tiers

| Score | Tier | Action |
|---:|---|---|
| 0–30 | LOW | Instant settlement |
| 31–60 | MEDIUM | 10-second protection |
| 61–85 | HIGH | 60-second protection |
| 86+ | VERY_HIGH | Hard hold + verification |

These are project configuration values, not fixed banking rules.

---

## 6. Risk Explanation

For protected or held transactions, show the actual reasons.

Example:

```text
Risk Score: 75
Tier: HIGH

Reasons:
- New beneficiary (+30)
- High amount (+25)
- First payment (+20)
```

The customer should see **why** the transaction was protected.

---

## 7. Protection Window

For `MEDIUM` and `HIGH` transactions:

1. Set status to `PROTECTED`.
2. Store `PROTECTION_START`.
3. Store `PROTECTION_END`.
4. Show a countdown in the UI.
5. Allow cancel while active.
6. Allow release while active.
7. Backend checks the real expiry time.
8. After expiry, use the selected project rule.

Recommended student-project behavior:

```text
PROTECTED -> CANCELLED
PROTECTED -> SETTLED
PROTECTED + expiry -> SETTLED
```

For `VERY_HIGH`:

```text
VERY_HIGH
   |
HARD_HOLD
   |
CHECKER
  / \
APPROVE REJECT
  |       |
SETTLED REJECTED
```

---

## 8. Customer Safety Settings

Example:

```text
Protect payments above: Rs. 50,000
Protection duration: 30 seconds
Protect all new beneficiaries: Yes
Additional verification above: Rs. 5,00,000
```

Bank-mandated controls must take precedence over customer preferences.

---

## 9. Maker–Checker

### Maker

```text
Create high-value payment
        |
        v
Risk evaluation
        |
        v
HARD_HOLD / PENDING_APPROVAL
```

### Checker

```text
View pending approvals
        |
        v
Review amount + beneficiary + risk factors
        |
        +---- Approve ---> SETTLED
        |
        +---- Reject ----> REJECTED
```

Requirements:

- Maker and checker must be different users.
- Rejection requires a comment.
- Approval records checker and timestamp.
- All approval actions are audited.

---

## 10. Post-Settlement Dispute

SafePay must clearly separate **Undo** from **Dispute**.

### Before settlement

```text
PROTECTED -> CANCELLED
```

### After settlement

```text
SETTLED -> DISPUTED -> UNDER_REVIEW -> RESOLVED / REJECTED
```

This is a simulated bank workflow. It does not execute a real chargeback or recovery.

---

# 11. Database Tables

Create these Oracle tables:

1. `ROLES`
2. `USERS`
3. `ACCOUNTS`
4. `BENEFICIARIES`
5. `TRANSACTIONS`
6. `RISK_FACTORS`
7. `TRANSACTION_RISK_FACTORS`
8. `PROTECTION_RULES`
9. `USER_SAFETY_SETTINGS`
10. `APPROVALS`
11. `DISPUTES`
12. `AUDIT_LOG`

## Key table structures

### ROLES

```text
ROLE_ID PK
ROLE_NAME
DESCRIPTION
```

### USERS

```text
USER_ID PK
ROLE_ID FK
USER_NAME
EMAIL
PHONE
PASSWORD_HASH
STATUS
CREATED_AT
```

### ACCOUNTS

```text
ACCOUNT_ID PK
USER_ID FK
ACCOUNT_NUMBER
ACCOUNT_TYPE
BALANCE
STATUS
CREATED_AT
```

### BENEFICIARIES

```text
BENEFICIARY_ID PK
USER_ID FK
BENEFICIARY_NAME
ACCOUNT_NUMBER
BANK_CODE
STATUS
ADDED_AT
```

### TRANSACTIONS

```text
TRANSACTION_ID PK
SENDER_ACCOUNT_ID FK
BENEFICIARY_ID FK
AMOUNT
TRANSACTION_TYPE
RISK_SCORE
RISK_TIER
STATUS
PROTECTION_SECONDS
PROTECTION_START
PROTECTION_END
CREATED_BY
CREATED_AT
UPDATED_AT
```

### RISK_FACTORS

```text
RISK_FACTOR_ID PK
FACTOR_CODE
FACTOR_NAME
DESCRIPTION
WEIGHT
ACTIVE_FLAG
```

### TRANSACTION_RISK_FACTORS

```text
TRANSACTION_ID FK
RISK_FACTOR_ID FK
SCORE_ADDED
DETECTED_VALUE
```

### PROTECTION_RULES

```text
RULE_ID PK
RISK_TIER
MIN_SCORE
MAX_SCORE
PROTECTION_SECONDS
ACTION
ACTIVE_FLAG
```

### USER_SAFETY_SETTINGS

```text
SETTING_ID PK
USER_ID FK
AMOUNT_THRESHOLD
PROTECTION_SECONDS
NEW_BENEFICIARY_PROTECTION
ADDITIONAL_VERIFICATION
```

### APPROVALS

```text
APPROVAL_ID PK
TRANSACTION_ID FK
MAKER_ID FK
CHECKER_ID FK
ACTION
COMMENTS
CREATED_AT
ACTION_AT
```

### DISPUTES

```text
DISPUTE_ID PK
TRANSACTION_ID FK
USER_ID FK
DISPUTE_TYPE
DESCRIPTION
STATUS
CREATED_AT
RESOLUTION_DATE
RESOLUTION_REMARKS
```

### AUDIT_LOG

```text
AUDIT_ID PK
USER_ID FK
TRANSACTION_ID FK
ACTION
OLD_STATUS
NEW_STATUS
TIMESTAMP
REMARKS
```

---

## 12. Oracle Requirements

Use Oracle sequences for generated IDs.

Recommended sequences:

```text
SEQ_ROLE_ID
SEQ_USER_ID
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

Use:

- Primary keys
- Foreign keys
- `NOT NULL`
- `UNIQUE`
- Suitable `CHECK` constraints

Do not physically delete completed transaction history.

---

# 13. Complex Reporting Views

Create at least four useful Oracle views.

### `VW_TRANSACTION_DASHBOARD`

Show:

```text
TRANSACTION_ID
CUSTOMER_NAME
BENEFICIARY_NAME
AMOUNT
RISK_SCORE
RISK_TIER
STATUS
PROTECTION_SECONDS
CREATED_AT
```

### `VW_RISK_SUMMARY`

Show:

```text
RISK_TIER
TRANSACTION_COUNT
TOTAL_AMOUNT
AVERAGE_AMOUNT
```

### `VW_PENDING_APPROVALS`

Show:

```text
TRANSACTION_ID
MAKER
AMOUNT
RISK_SCORE
RISK_TIER
STATUS
CREATED_AT
```

### `VW_CUSTOMER_PROTECTION_ANALYSIS`

Show:

```text
CUSTOMER_NAME
TOTAL_TRANSACTIONS
PROTECTED_COUNT
CANCELLED_COUNT
SETTLED_COUNT
HIGH_RISK_COUNT
TOTAL_AMOUNT
```

Use joins, `CASE` expressions and aggregate functions to demonstrate complex reporting.

---

# 14. Backend Requirements

Recommended stack:

```text
Java 17+
Spring Boot
Spring Web
Spring Data JPA
Maven
Oracle Database
```

Suggested package structure:

```text
com.safepay
|
+-- controller
+-- service
+-- repository
+-- entity
+-- dto
+-- exception
+-- config
```

Core services:

```text
AuthService
BeneficiaryService
TransactionService
RiskEngineService
ProtectionService
ApprovalService
DisputeService
AuditService
```

---

# 15. Payment Business Logic

## Create payment

```text
Receive request
   |
Validate account
   |
Validate beneficiary
   |
Validate amount/balance
   |
Authenticate
   |
Create transaction
   |
Calculate risk
   |
Save risk factors
   |
Determine action
   |
Save state
   |
Write audit log
```

## Cancel

Backend must verify:

```text
transaction exists
AND status = PROTECTED
AND current time < protection_end
AND user is authorized
```

Then:

```text
PROTECTED -> CANCELLED
```

## Release

Backend checks:

```text
status = PROTECTED
AND user is authorized
```

Then:

```text
PROTECTED -> SETTLED
```

## Approve

Backend checks:

```text
status = HARD_HOLD
AND checker role is valid
AND checker_id != maker_id
```

Then approve/reject and audit.

---

# 16. REST APIs

## Authentication

```text
POST /api/auth/login
```

## Beneficiaries

```text
POST   /api/beneficiaries
GET    /api/beneficiaries
GET    /api/beneficiaries/{id}
PUT    /api/beneficiaries/{id}
DELETE /api/beneficiaries/{id}
```

For banking-style behavior, DELETE can mean deactivation rather than physical deletion.

## Transactions

```text
POST /api/transactions
GET  /api/transactions
GET  /api/transactions/{id}

POST /api/transactions/{id}/cancel
POST /api/transactions/{id}/release
```

## Approvals

```text
GET  /api/approvals/pending
POST /api/approvals/{id}/approve
POST /api/approvals/{id}/reject
```

## Disputes

```text
POST /api/disputes
GET  /api/disputes
GET  /api/disputes/{id}
PUT  /api/disputes/{id}
```

## Admin

```text
GET /api/admin/dashboard
GET /api/admin/risk-summary
GET /api/admin/audit-logs
```

---

# 17. Frontend Requirements

## Customer pages

### Login

```text
Username / Email
Password
[ Login ]
```

### Dashboard

```text
Balance
[ Send Money ]
[ Beneficiaries ]

Recent Transactions
```

### Send Money

```text
From Account
Beneficiary
Amount
Purpose / Reference

[ Continue ]
```

### Protection screen

```text
PAYMENT PROTECTED

Rs. 4,50,000

Why?
- New beneficiary
- First payment
- High amount

Remaining: 00:42

[ CANCEL PAYMENT ]   [ RELEASE PAYMENT ]
```

### Transaction history

Show:

```text
SETTLED
PROTECTED
CANCELLED
HARD HOLD
REJECTED
DISPUTED
```

## Admin pages

```text
Dashboard
Transactions
High-Risk Transactions
Pending Approvals
Users
Beneficiaries
Risk Rules
Protection Rules
Disputes
Audit Logs
Reports
```

---

# 18. Admin Dashboard

Show:

```text
Total Transactions
Total Transaction Value
Protected Transactions
High-Risk Transactions
Very-High-Risk Transactions
Cancelled Transactions
Pending Approvals
Disputed Transactions
```

Recommended charts:

- Transactions by risk tier
- Transactions by status
- Daily volume
- Protected vs settled vs cancelled
- High-risk transaction value

Prefer Oracle views as the source for reporting data.

---

# 19. CRUD Requirements

Demonstrate CRUD for:

### Beneficiaries

Create / Read / Update / Deactivate

### Risk Factors

Create / Read / Update / Deactivate

### Protection Rules

Create / Read / Update / Deactivate

### Users

Create / Read / Update as allowed by role

### Transactions

Use status-based actions rather than physical deletion.

---

# 20. Audit Requirements

Audit at least:

```text
LOGIN
CREATE_BENEFICIARY
CREATE_TRANSACTION
AUTHORIZE_TRANSACTION
RISK_ASSESSED
PROTECT_TRANSACTION
CANCEL_TRANSACTION
RELEASE_TRANSACTION
SETTLE_TRANSACTION
APPROVE_TRANSACTION
REJECT_TRANSACTION
CREATE_DISPUTE
UPDATE_DISPUTE
```

Record:

```text
WHO
WHAT
WHEN
OLD STATE
NEW STATE
TRANSACTION
REMARKS
```

---

# 21. Demo Scenarios

## Scenario A — Low Risk

```text
Amount: Rs. 500
Beneficiary: Existing
History: Frequent

LOW
  |
SETTLED
```

## Scenario B — Medium Risk

```text
Amount: Rs. 25,000
Beneficiary: Existing
Amount is unusual

MEDIUM
  |
10-second PROTECTED
  |
CANCEL / RELEASE
```

## Scenario C — High Risk

```text
Amount: Rs. 4,50,000
Beneficiary: New
First payment: Yes
Amount unusually high: Yes

HIGH
  |
60-second PROTECTED
  |
CANCEL / RELEASE
```

## Scenario D — Very High Risk

```text
Amount: Rs. 10,00,000
Beneficiary: New
Device: New
Location: Unusual

VERY_HIGH
  |
HARD_HOLD
  |
CHECKER
  |
APPROVE / REJECT
```

---

# 22. Test Cases

| Test | Expected Result |
|---|---|
| Low-risk payment | Immediate settlement |
| Medium-risk payment | Protected |
| High-risk payment | Protected |
| Very-high-risk payment | Hard hold |
| Cancel during protection | `CANCELLED` |
| Release during protection | `SETTLED` |
| Cancel after expiry | Rejected |
| Cancel settled transaction | Rejected |
| Maker approves own transaction | Rejected |
| Checker approves | `SETTLED` |
| Checker rejects | `REJECTED` |
| Customer disputes settled payment | Dispute created |
| Admin changes risk rule | Later payments use updated rule |
| Unauthorized admin API access | Denied |

---

# 23. Security Requirements

- Never store passwords in plaintext.
- Enforce role-based authorization in the backend.
- Customers can access only their own transactions.
- Admin APIs require admin permissions.
- Use `BigDecimal` for money in Java.
- Use Oracle `NUMBER` for monetary amounts.
- Never trust the frontend for authorization or timer expiry.
- Validate every transaction state transition server-side.

---

# 24. Build Order

## Phase 1 — Database

```text
ER Diagram
   ->
Tables
   ->
Sequences
   ->
Constraints
   ->
Sample Data
```

## Phase 2 — Backend foundation

```text
Spring Boot
   ->
Oracle connection
   ->
Entities
   ->
Repositories
   ->
CRUD APIs
```

## Phase 3 — Payment

```text
Create transaction
   ->
Validate
   ->
Save
   ->
Settle
```

## Phase 4 — Risk Engine

```text
Calculate score
   ->
Save risk factors
   ->
Determine tier
```

## Phase 5 — Protection Engine

```text
MEDIUM/HIGH
   ->
PROTECTED
   ->
Countdown
   ->
Cancel / Release
   ->
Expiry
```

## Phase 6 — Corporate controls

```text
HARD_HOLD
   ->
Checker
   ->
Approve / Reject
```

## Phase 7 — Frontend

```text
Login
   ->
Dashboard
   ->
Send Money
   ->
Protection screen
   ->
History
```

## Phase 8 — Admin

```text
Dashboard
   ->
Approvals
   ->
Rules
   ->
Disputes
   ->
Audit
```

## Phase 9 — Reporting

```text
Oracle Views
   ->
REST APIs
   ->
Dashboard
```

## Phase 10 — Testing

```text
Run demo scenarios
   ->
Fix state transitions
   ->
Load sample data
   ->
Prepare presentation
```

---

# 25. MVP

Build this first:

```text
LOGIN
  |
SELECT BENEFICIARY
  |
SEND MONEY
  |
RISK ENGINE
  |
  +---- LOW ------> SETTLED
  |
  +---- HIGH -----> PROTECTED
                         |
                    CANCEL / RELEASE
                         |
                  CANCELLED / SETTLED
                         |
                  TRANSACTION HISTORY
                         |
                  ADMIN DASHBOARD
```

After the MVP is stable, add:

```text
Maker-Checker
Very High Risk Hard Hold
Disputes
Audit Logs
Safety Settings
Advanced Reporting
```

---

# 26. Definition of Done

The project is ready for the main demonstration when this works without manually editing the database:

```text
Login
  ->
Create/select beneficiary
  ->
Create payment
  ->
Risk calculation
  ->
Protection/hold decision
  ->
Cancel/release OR checker approval
  ->
Transaction status update
  ->
Audit record
  ->
Admin dashboard update
```

Also ensure:

- Oracle sequences are used for IDs.
- At least four useful complex views exist.
- CRUD is demonstrated.
- Maker-checker works.
- Dispute flow works.
- Backend validates all transaction states.
- No real money movement occurs.

---

# 27. Final Project Statement

**SafePay is a simulated banking transaction protection platform that evaluates payment risk before settlement and dynamically chooses between instant settlement, a temporary protection window, or enhanced verification, supported by Oracle transaction state, sequences, reporting views, maker-checker controls, audit logging and dispute management.**
