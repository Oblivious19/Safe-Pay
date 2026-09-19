# — - SafePa Risk Ada ive Pa ent Protection y pt ym 

# S em yst 

#### Complete Project Blueprint 

## 1. Problem Statement 

Digital payment rails (UPI, IMPS, NEFT, RTGS) are optimized for speed, not reversibility. Two gaps exist today: 

" - " - No differentiated protection. Existing cooling off rules (e.g., new beneficiary holds on NEFT/RTGS) 

apply the _same_ fixed delay to every transaction regardless of actual risk. A ₹500 payment to a new beneficiary gets the same treatment as a ₹5,00,000 payment to a new beneficiary — either both are needlessly delayed, or neither is protected enough. 

- No orchestrated decision layer. Banks already have authentication, fraud monitoring, and post 

— transaction dispute/recovery processes but nothing that takes the _signals_ these systems already produce (new beneficiary, unusual amount, new device, odd hour) and turns them into a single, 

explainable, real-time decision: _release instantly, protect briefly, or hold for approval._ 

The result: users who make an honest mistake (wrong account, fat-fingered amount) have no way to catch it before the money is gone, and corporate users have no lightweight way to add a second set of eyes to unusually large payments without a full manual approval process for _everything_ . 

## 2. Our Solution 

- ' SafePay is not a fraud detection system and not a payment rail. It s a policy/orchestration layer that 

sits between "payment authorized" and "payment settled," and answers one question per transaction: 

_How much friction does this specific payment deserve before it becomes irreversible?_ 

<mark>�</mark> 

Payment → Risk Signals → Risk Score → Policy Decision → Release / Protect / Hold 

LOW risk → instant settlement (no friction) 

MEDIUM risk → short protection window (e.g. 10s) — user can Cancel or Release 

HIGH risk → longer protection window (e.g. 60s) — user can Cancel or Release 

VERY HIGH risk → hard hold — routed to a maker-checker approval queue instead of a timer 

The system is built as a simulated transaction engine with its own internal ledger and state machine — it does not claim to intercept or reverse a live UPI/IMPS/NEFT transaction on the real payment rails (which is neither technically possible nor within a student/portfolio project's authority). This is the honest and correct scope, and it's what makes the project technically credible. 

## 3. Feature List 

#### A. Core Transaction Engine 

Create payment (beneficiary, amount, remarks) 

Simulated authentication step (PIN/OTP mock) 

Transaction state machine: `CREATED → AUTHORIZED → PROTECTED → CANCELLED / RELEASED → SETTLED → DISPUTED →` 

```
RECOVERED
```

Server-authoritative countdown timer (never trust client clock) 

- Auto release job when protection window expires 

#### B. Risk Engine 

- Rule based, weighted risk scoring (explainable, not a black box) 

Signals: beneficiary age, amount vs. historical average, new device, new location, unusual time of day, 

transaction velocity 

Risk level classification: LOW / MEDIUM / HIGH / VERY_HIGH 

Per-transaction risk explanation ("Protected because: new beneficiary + amount 9x your average") 

#### - C. Customer Facing Features 

Add/manage beneficiaries (with cooling-off metadata) 

Initiate payment with live risk feedback before confirming 

Protection window UI: countdown + Cancel / Release buttons 

Configurable personal protection preferences (protect above ₹X, always protect new beneficiaries, 

custom window length) 

Transaction history with status and risk explanation 

Raise a dispute for a settled transaction 

#### - D. Enterprise / Maker Checker Mode 

- Role based accounts: Maker, Checker, Admin 

High-value or VERY_HIGH-risk transactions routed to a checker queue instead of a timer 

Checker approves/rejects with remarks 

Full audit trail of who created, approved, or rejected a transaction 

#### - E. Admin / Bank Ops Features 

Configure risk weights and thresholds per risk level 

Configure default protection window durations 

- View all transactions, override/force release, view audit logs 

Dashboard: transactions by status, average protection time, cancellation rate, dispute rate 

#### - F. Dispute & Recovery (post settlement) 

Raise dispute → status tracking (Open → In Review → Resolved/Rejected) 

- Simulated recovery workflow, kept clearly separate from the pre settlement protection layer 

#### G. Auth & Security 

- JWT based authentication, Spring Security 

- Role based access control (CUSTOMER, MAKER, CHECKER, ADMIN) 

Audit logging on every state transition 

#### H. Noti cations fi 

In-app + simulated email/SMS on: beneficiary added, payment protected, window expiring, released, held for approval, dispute update 

## 4. Complete Project Flow (Start to End) 

Register / Login — user signs up, gets JWT token. 

Add beneficiary — system records <mark>`added_at`</mark> timestamp (used later for the "new beneficiary" risk signal). — Initiate payment user selects beneficiary, enters amount, remarks. 

— Authenticate simulated PIN/OTP step confirms intent. 

— Risk engine runs computes signals (beneficiary age, amount deviation, device/location, time of day) → 

weighted score → risk level. 

##### Policy decision applied: 

LOW → transaction moves straight to <mark>`SETTLED`</mark> . 

MEDIUM/HIGH → transaction enters <mark>`PROTECTED`</mark> with a <mark>`protected_until`</mark> timestamp; frontend shows a live 

— - countdown with Cancel/Release actions. Server is the source of truth a scheduled job also auto 

transitions expired windows to <mark>`RELEASED → SETTLED`</mark> even if the user never touches the UI. 

VERY_HIGH (or an enterprise rule like "> ₹10L requires checker") → transaction enters <mark>`PROTECTED`</mark> with 

<mark>`requires_checker = true`</mark> and is pushed to the checker's approval queue instead of a timer. 

Checker review (if applicable) — checker approves (→ <mark>`RELEASED → SETTLED`</mark> ) or rejects (→ <mark>`CANCELLED`</mark> ), with 

remarks logged. 

##### Notifications fire at each transition. 

- — Post settlement if the user later realizes an error, they raise a dispute, which is handled as a separate 

- workflow ( <mark>`SETTLED → DISPUTED → RECOVERED/REJECTED`</mark> ), clearly distinct from the pre settlement protection flow. 

Admin/ops can view every transaction, its full risk-signal breakdown, and the audit trail at any point. 

## 5. Data Model 

#### Entities & Ke Fields y 

```
app_user
```

Column Type Notes - id NUMBER (PK) Oracle sequence generated name VARCHAR2 email VARCHAR2 (unique) phone VARCHAR2 password_hashVARCHAR2 BCrypt role VARCHAR2 CUSTOMER / MAKER / CHECKER / ADMIN created_at TIMESTAMP 

###### **<mark>`account`</mark>** 

Column Type Notes id NUMBER (PK) user_id NUMBER (FK → app_user) account_number VARCHAR2 balance NUMBER(18,2) avg_txn_amount NUMBER(18,2) rolling average, used as a risk signal created_at TIMESTAMP 

```
beneficiary
```

Column 

Type 

Notes 

id NUMBER (PK) 

owner_account_id NUMBER (FK → account) beneficiary_name VARCHAR2 

beneficiary_account_number VARCHAR2 

ifsc_code VARCHAR2 added_at TIMESTAMP drives "new beneficiary" risk signal is_trusted CHAR(1) Y/N, set after N successful payments txn_count NUMBER 

```
transaction
```

Column 

Type 

Notes 

id 

NUMBER (PK) 

NUMBER (FK → from_account_id account) NUMBER (FK → beneficiary_id beneficiary) amount NUMBER(18,2) CREATED / AUTHORIZED // AUTHORIZED / AUTHORIZED // PROTECTED / CANCELLED status VARCHAR2 / RELEASED / SETTLED / DISPUTED / RELEASED / SETTLED / DISPUTED // SETTLED / DISPUTED / SETTLED / DISPUTED // DISPUTED / DISPUTED // RECOVERED risk_score NUMBER(5,2) risk_level VARCHAR2 LOW / MEDIUM / HIGH / VERY_HIGH — protection_window_seconds NUMBER nullable null for LOW - protected_until TIMESTAMP nullable, server authoritative expiry requires_checker CHAR(1) Y/N device_id VARCHAR2 location VARCHAR2 remarks VARCHAR2 created_at / updated_at TIMESTAMP **<mark>`risk_signal_log`</mark>** (audit trail / explainability) 

CREATED / AUTHORIZED // AUTHORIZED / AUTHORIZED // PROTECTED / CANCELLED / RELEASED / SETTLED / DISPUTED / RELEASED / SETTLED / DISPUTED // SETTLED / DISPUTED / SETTLED / DISPUTED // DISPUTED / DISPUTED // RECOVERED 

Column 

Type 

Notes 

id NUMBER (PK) 

transaction_id NUMBER (FK) signal_name VARCHAR2 e.g. NEW_BENEFICIARY, AMOUNT_DEVIATION signal_value VARCHAR2 weight NUMBER(5,2) 

contribution_scoreNUMBER(5,2) 

evaluated_at TIMESTAMP 

**<mark>`protection_policy`</mark>** (admin-configurable) 

Column Type Notes id NUMBER (PK) risk_level VARCHAR2 default_window_seconds NUMBER requires_checker CHAR(1) min_score / max_score NUMBER(5,2) thresholds 

###### **<mark>`user_preference`</mark>** 

Column Type Notes id NUMBER (PK) user_id NUMBER (FK) protect_above_amount NUMBER(18,2) protect_new_beneficiary CHAR(1) 

custom_window_seconds NUMBER nullable confirm_above_amount NUMBER(18,2) 

```
maker_checker_approval
```

Column 

Type 

Notes 

id 

NUMBER (PK) 

transaction_idNUMBER (FK) 

maker_id NUMBER (FK → app_user) checker_id NUMBER (FK → app_user, nullable) status VARCHAR2 PENDING / APPROVED / REJECTED remarks VARCHAR2 decided_at TIMESTAMP 

###### **<mark>`dispute`</mark>** 

Column Type Notes id NUMBER (PK) transaction_id NUMBER (FK) raised_by NUMBER (FK → app_user) reason VARCHAR2 status VARCHAR2 OPEN / IN_REVIEW / RESOLVED / REJECTED 

created_at / resolved_at TIMESTAMP 

**<mark>`notification`</mark>** | id, user_id, transaction_id, type, message, channel, sent_at | 

**<mark>`audit_log`</mark>** | id, entity_type, entity_id, action, performed_by, timestamp, details | 

#### Relationships 

<mark>�</mark> 

app_user (1) ── (*) account ── * account (1) ( ) beneficiary ── * account (1) ( ) transaction [from_account] beneficiary (1) ── (*) transaction ── * transaction (1) ( ) risk_signal_log ── transaction (1) (0..1) maker_checker_approval ── transaction (1) (0..1) dispute app_user (1) ── (1) user_preference 

## 6. API Endpoints 

#### Auth 

<mark>�</mark> 

POST   /api/auth/register POST   /api/auth/login POST   /api/auth/refresh 

#### Accounts 

<mark>�</mark> 

GET    /api/accounts/{id} GET    /api/accounts/{id}/balance 

#### Bene ciaries fi 

<mark>�</mark> 

POST   /api/beneficiaries GET    /api/beneficiaries?accountId={id} DELETE /api/beneficiaries/{id} 

#### Transactions (core engine) 

<mark>�</mark> 

POST   /api/transactions/initiate            → runs risk engine, returns decision + window POST   /api/transactions/{id}/authenticate    → simulated OTP/PIN step 

GET    /api/transactions/{id} 

GET    /api/transactions?accountId={id}&status={status} 

POST   /api/transactions/{id}/cancel 

POST   /api/transactions/{id}/release         → manual early release, optional - GET    /api/transactions/{id}/risk explanation 

#### Maker-Checker 

<mark>�</mark> 

GET    /api/approvals/pending?checkerId={id} POST   /api/approvals/{id}/approve POST   /api/approvals/{id}/reject 

#### Preferences 

<mark>�</mark> 

GET    /api/preferences/{userId} PUT    /api/preferences/{userId} 

#### Disputes 

### <mark>�</mark> 

POST   /api/disputes GET    /api/disputes/{id} PUT    /api/disputes/{id}/status 

#### Admin 

### <mark>�</mark> 

GET    /api/admin/policies PUT    /api/admin/policies/{riskLevel} - GET    /api/admin/audit logs GET    /api/admin/dashboard/stats 

#### Noti cations fi 

<mark>�</mark> 

GET    /api/notifications?userId={id} 

#### - Real time 

<mark>�</mark> WS     /ws/transactions/{id}/status → pushes live countdown + state changes (STOMP over WebSocket) 

## 7. Tech Stack Ma in pp g 

|Layer|Technology|Notes|
|---|---|---|
|Backend<br>framework|Spring Boot 3.x<br>(Java17)|Spring MVC controllers run on the ServletAPIunder the hood—covers<br>your "servlets" requirement natively; no need for raw<br>`HttpServlet`<br>classes unless you want one foraspecifcsimulatedwebhook.|
|Security|Spring Security +<br>JWT|Role-based access (CUSTOMER/MAKER/CHECKER/ADMIN)|
|Persistence|Spring Data JPA<br>(Hibernate) +<br>Oracle DB|Use<br>`@GeneratedValue(strategy = GenerationType.SEQUENCE)` with Oracle<br>sequences;<br>`@Entity`classes mapdirectly to thetablesabove|



Layer Technology Notes Periodic job scans <mark>`PROTECTED`</mark> transactions where <u>`protected_until <`</u> - — Scheduling Spring **<mark>`@Scheduled`</mark>** `now()` and auto transitions them this is what makes the server (not the browser) authoritative over the timer - Spring ' Real time Pushes countdown/status updates to the frontend so the UI doesn t WebSocket updates have to poll (STOMP) - - Module based UI ( <mark>`oj-module`</mark> ), Knockout driven data binding, `oj-c-` Frontend Oracle JET (ojet) `button` <u>/</u> <mark>`oj-progress-status`</mark> type components for the countdown + Cancel/Release UI; REST calls via <mark>`fetch`</mark> / <mark>`oj-rest-dataprovider`</mark> Maven (backend), - Build tools ojet cli (frontend scaffolding)<sup>the risk-scoring function—pure logic, easyto</sup> Testing JUnit 5 + Mockito<sup>Especially important</sup> -<sup>for</sup> unit test with table driven test cases Spring Boot Simplifies deployment; Oracle DB connection via Oracle JDBC driver Deployment embedded Tomcat ( <mark>`ojdbc11`</mark> ) 

#### One critical implementation note 

Never let the frontend own the countdown as the source of truth. The server stores **<mark>`protected_until`</mark>** ; the Oracle JET UI just displays <mark>`protected_until - now()`</mark> and re-syncs on each WebSocket tick. The actual - <mark>`PROTECTED → RELEASED`</mark> transition happens server side (via the scheduled job), so even if a user closes the tab or manipulates their local clock, the outcome is correct. 

## Su sted Build Order gge 

Data model + JPA entities + Oracle schema (DDL/sequences) 

Auth (register/login/JWT) 

Beneficiary CRUD 

Transaction initiate + risk engine (pure logic, unit-testable first) 

State machine + scheduled expiry job 

WebSocket countdown push + Oracle JET protection-window UI Maker-checker flow 

Preferences + admin policy config 

Dispute workflow 

Notifications + audit logging + dashboard 

