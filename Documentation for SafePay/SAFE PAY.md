Confidential - Oracle Restricted \Employees Only 

## Project: SafePay 

Most instant bank-transfer systems, such as UPI and IMPS, make a payment difficult or impossible for the sender to reverse after it is submitted. Users may lose money because of: 

- Selecting the wrong beneficiary 

- Entering the wrong amount 

- Sending money twice 

- Making a payment under pressure from a scammer 

- Paying a newly added or suspicious beneficiary 

- Initiating a large payment from an unfamiliar device or location 

SafePay proposes a smart pre-settlement protection window. After the user authorizes a transaction, a risk engine evaluates it before final settlement. Depending on the risk, SafePay may settle it immediately, pause it temporarily with an Undo option, or hold it for additional verification. 

Example: 

|**Risk tier**|**Example**|**Proposed Action**|
|---|---|---|
|Low|₹500 to a regular grocery store|Settle immediately|
|Medium|₹25,000 to an existing contact, but with an<br>unusual amount|Pause for 10 seconds|
|High|₹4,50,000 to a benefciary added 15 minutes ago|<sup>Pause for 60 seconds and warn</sup><br>the user|
|Very High|₹10,00,000 from an unknown device and location|<sup>Hard hold and additional</sup><br>verifcation|



Proposed transaction states: 

##### CREATED → AUTHORIZED → PROTECTED → CANCELLED or RELEASED → SETTLED 

This is initially a college-level banking simulation built using Core Java, Spring Boot, REST APIs, and a database. Do not assume that real UPI or IMPS payments can be paused without support from banks, payment service providers, NPCI, and regulators. 

Provide a clear and practical analysis covering the following areas. 

##### **SafePay: Practical Project Solution** 

SafePay is a **pre-settlement payment-safety layer** that evaluates a payment after authorization but before simulated final settlement. It gives users a short, risk-based chance to stop accidental or suspicious payments. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

For this college project, SafePay should be built as a **banking-payment simulator** , not as a claim that real UPI/IMPS transactions can be paused. Real implementation would require payment-rail, bank, PSP, NPCI, and regulatory support. 

----------------------------------------------------------------------------------------------------------------- 

#### 1. Problem Statement 

Instant bank transfers are designed for speed and finality. Once a user submits a UPI or IMPS transaction, the amount may be debited and credited within seconds, leaving little or no opportunity to correct a wrong beneficiary, wrong amount, duplicate payment, or scam-induced payment. 

This affects retail customers, senior citizens, first-time digital-payment users, and corporate payment teams. Common causes include selecting a similarly named beneficiary, typing an incorrect amount, retrying a payment after a network delay, paying a recently added beneficiary, and being pressured by a fraudster impersonating a bank, police officer, employer, or family member. 

Post-settlement complaint and dispute processes are limited: funds may already be withdrawn, the recipient may refuse to return them, and recovery often depends on the receiving bank and recipient cooperation. This causes financial loss, customer anxiety, complaints, operational cost, and reduced trust in digital payments. 

SafePay addresses this gap by applying risk-based controls before settlement: safe transactions settle immediately, suspicious transactions receive a brief Undo window or additional verification, and all decisions are logged for audit. 

##### **Example** 

|**Scenario**|**Norma**|**l instant-payment app**|**SafePay**|||
|---|---|---|---|---|---|
|User sends ₹4<br>|,50,000<br> <br>Transa|ction is typically|Transaction is|authorized, clas|sifed|
|to a benefciar<br>added 15 minu<br>ago|y<br>tes<br>proces<br>may dis<br>scam a|sed immediately. The user<br>cover the mistake or<br>fter settlement.|<br>High Risk, pau<br>shown a clear<br>tap**Undo**befo|sed for 60 secon<br>warning. The us<br>re release.|ds, and<br>er can|
|User presses U<br>------------------<br>2. Possible|ndo<br>May on<br>compla<br>-------------------<br>solutions|ly be able to fle a<br>int after settlement.<br>---------------------------------|Transaction m<br>simulated sett<br>-------------------|oves to CANCEL<br>lement occurs.<br>--------------------|LED; no<br>----|
|**Solution**|**How it works**<br>**problem**<br>**solved**|**/**<br>**Advantages Limitation**|**s UX impact**|**Complexity /**<br>**dependency**|**MVP?**|
|Final<br>confrmation|Show<br>benefciary<br>name,<br>account/UPI<br>ID, amount,|Simple;<br>prevents<br>obvious<br>mistakes<br>Users can<br>ignore<br>warnings|One extra<br>confrmation|Low; no<br>external<br>dependency|Yes|



----------------------------------------------------------------------------------------------------------------- 

#### 2. Possible solutions 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

|**Solution**<br>New-<br>benefciary<br>cooling period|**How it works /**<br>**problem**<br>**solved**<br>and warnings<br>before<br>authorization<br>Restrict or<br>warn on high-<br>value<br>payments to<br>newly added<br>benefciaries|**Advantages **<br>Reduces<br>common<br>fraud pattern|**Limitations**<br>Can<br>inconvenien<br>ce genuine<br>urgent<br>payments|**UX impact**<br>Moderate<br>friction|**Complexity /**<br>**dependency**<br>Low–medium;<br>banking policy<br>dependent in<br>real life|**MVP?**<br>Yes|
|---|---|---|---|---|---|---|
|User-defned<br>limits|Let users set<br>daily, per-<br>transaction,<br>and new-<br>benefciary<br>limits|Personal<br>control; clear<br>safety<br>boundary|<br>Users may<br>set unsafe<br>limits|Low|Low|Yes|
|Risk-based<br>delayed<br>settlement|Apply a timer<br>based on risk<br>tier|Creates time<br>to detect<br>mistakes/sca<br>ms|Not<br>possible on<br>actual<br>instant rails<br>without<br>partner<br>support|Minimal for<br>low risk;<br>friction for<br>high risk|Medium;<br>external<br>payment<br>dependency in<br>production|<br>Yes,<br>simulat<br>ed|
|Undo window|Permit<br>cancellation<br>while<br>protected|Directly<br>solves<br>accidental<br>send before<br>settlement|Must<br>handle<br>timer race<br>conditions|Good and<br>understanda<br>ble|Medium|Yes|
|OTP/biometric<br>step-up|<br>Require<br>OTP/biometric/<br>PIN for very<br>risky payments|Stronger user<br>verifcation|<br>OTP can be<br>socially<br>engineered|Friction only<br>for risky<br>cases|<br>Medium;<br>biometric/prov<br>der<br>dependency|i<br>Yes,<br>simulat<br>ed OTP|
||Score||||||
|Benefciary<br>trust score|benefciary<br>based on age,<br>history,<br>success rate,<br>fags|Adds context<br>to risk<br>decisions|<br>Cannot<br>prove a<br>benefciary<br>is legitimate|Low visible<br>impact|Medium|Yes,<br>basic<br>rules|
|Rule-based<br>fraud|Use<br>deterministic|Explainable,<br>testable,|Cannot<br>discover|Low|Medium|Yes|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

||**How it works /**||||**Complexity /**||
|---|---|---|---|---|---|---|
|**Solution**|**problem**<br>**solved**|**Advantages **|**Limitations**|**UX impact**|<br>**dependency**|**MVP?**|
|detection|Java rules|reliable<br>fallback|new fraud<br>patterns<br>well||||
||Learn unusual||||||
|AI anomaly<br>detection|amount, time,<br>device,<br>location,<br>recipient|Personalized<br>detection|Needs data,<br>validation,<br>governance|<br>Low visible<br>impact|High;<br>privacy/model<br>governance|Future|
||patterns||||||
|Maker-|One corporate<br>user creates,|Prevents<br>unauthorized|<br>Requires<br>|Added<br>||Future /|
|Checker|another<br>approves|corporate<br>payments|roles/workf<br>ow|approval<br>delay|Medium|optional|
||Hold funds<br>hh|Strong|Not<br>equivalent||||
|Escrow/<br>protected<br>payment|troug an<br>approved<br>intermediary<br>until<br>conditions met|protection<br>for<br>goods/servic<br>es|to<br>UPI/IMPS;<br>legal and<br>commercial<br>complexity|<br>Moderate|High;<br>legal/bank<br>dependency|Future|



##### **Recommended combination** 

Use these for the MVP: 

1. Confirmation screen with clear beneficiary and amount warning. 

2. User-defined limits and new-beneficiary risk rules. 

3. Deterministic Java rule engine. 

4. Risk tiers with simulated protection timers. 

5. Undo/cancel during the timer. 

6. Extra OTP verification for Very High Risk. 

7. Duplicate-payment prevention, audit logging, and status tracking. 

This is practical, demonstrable, and avoids relying on an unproven AI model or unsupported real-time payment-rail control. 

----------------------------------------------------------------------------------------------------------------- 

#### 3. Recommended SafePay solution 

A user creates and authorizes a payment. SafePay calculates its risk and chooses one of four actions: 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

**Risk tier Example Action** Low ₹500 to a regular merchant Release and settle immediately Medium<sup>₹25,000 to an existing contact, unusual</sup> Protect for 10 seconds; Undo available amount ₹4,50,000 to a recently added High Protect for 60 seconds with warning beneficiary Hard hold; require OTP/extra verification or Very High<sup>₹10,00,000 from unknown</sup> device/location manual review 

##### **Key terms** 

|**Term**|**Meaning in SafePay**|
|---|---|
|Authorization|User confrms the payment using password/PIN in the simulator. It is<br>approved to enter risk evaluation, not yet settled.|
|Protection window|Timed period in PROTECTED state during which the user may cancel.|
|Cancellation|Stops a transaction before settlement.|
|Release|System permits a protected transaction to proceed after timer expiry or<br>verifcation.|
|Settlement|Final simulated debit/credit entry is completed.|
|Refund|A new, separate payment returning money after settlement.|
|Chargeback/dispute|<sup>Formal post-settlement complaint process. It does not guarantee return</sup><br>of funds.|



SafePay prevents loss **before settlement** . Once a transaction is settled, SafePay cannot silently reverse it; it can only create a refund request or dispute record in the simulation. 

##### **Problem-to-feature mapping** 

|**Problem**|**SafePay feature**|
|---|---|
|Wrong benefciary|Benefciary confrmation, trust score, protection timer|
|Wrong amount|Amount confrmation, anomaly rule, custom transaction limit|
|Duplicate payment|Idempotency key, duplicate detector|
|Scam pressure|High-risk warning, timer, extra verifcation|
|New benefciary|Benefciary age rule and cooling period|
|Unknown device/location|Device/location risk rule and hard hold|
|Corporate fraud<br>---------------------------------|Maker-Checker and approval limits<br>--------------------------------------------------------------------------------|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

#### 4. Required features 

##### **Essential MVP features** 

|**Feature**|**What it does / why needed **|**User**|**Priority**|<sup>**Important edge**</sup><br>**cases**|
|---|---|---|---|---|
|Registration and<br>secure login|Creates authenticated user<br>access|Customer, admin|MVP|Password hashing,<br>account lockout|
|Benefciary<br>management|Add, view, deactivate<br>benefciaries|Customer|MVP|Benefciary age,<br>duplicate UPI ID|
|Create and||||Invalid amount,<br>|
|authorize|Captures intended payment|Customer|MVP|insuficient|
|transaction||||simulated balance|
|||||Rules unavailable;|
|Risk evaluation|Calculates score and tier|System|MVP|default to safer<br>action|
|Risk tiers|Low/Medium/High/Very<br>High action mapping|System|MVP|Boundary scores|
|Dynamic<br>protection timer|Applies 0/10/60 seconds or<br>hold|System|MVP|Restart during timer|
|Undo/cancel|Cancels only before<br>release/settlement|Customer|MVP|Cancel at expiry<br>boundary|
|Additional<br>verifcation|OTP for Very High Risk|Customer|MVP|Expired/failed OTP|
|Automatic<br>release|Releases eligible<br>transactions after timer|System|MVP|Multiple scheduler<br>instances|
|Status tracking|Shows current lifecycle and<br>history|Customer/admin|MVP|Never expose<br>sensitive risk<br>internals|
|Risk<br>explanations|Explains warnings in simple<br>language|Customer|MVP|Avoid accusing<br>benefciary of fraud|
|Notifcations|In-app/email simulated<br>alerts|Customer|MVP|Delivery failure must<br>not block payment|
|Safety<br>thresholds|Confgure limit preferences|Customer|MVP|User cannot bypass<br>mandatory bank<br>rules|
|Audit logs|Records all immutable<br>actions|Admin/auditor|MVP|Logs must not be<br>editable|
|Duplicate|Stops repeated requests|Customer/system|MVP|Same amount to|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

|**Feature**|**What it**|**does / why neede**|**d User**|**Priority**<sup>**Important**</sup><br>**cases**|<sup>**edge**</sup>|
|---|---|---|---|---|---|
|prevention||||same bene<br>could be v|fciary<br>alid later|
||Control|s||||
|RBAC|custom<br>roles|er/admin/auditor|All|MVP<br>Privilege e|scalation|
|**Corporate featu**|**res**|||||
|**Feature**|**Purpose**||**User**|**Priority Edge cases**||
|Maker-Checker|<sup>Maker cr</sup><br>approves|<sup>eates, Checker</sup><br>/rejects|Corporate users|Future <sup>Same person c</sup><br>approve own t|<sup>annot</sup><br>ransaction|
|Approval limits|<sup>Approver</sup><br>within lim|<sup>s can approve only</sup><br>it|Checker/admin|Future <sup>Limit changes</sup><br>approval|<sup>during</sup>|
|Document<br>verifcation|Attach in<br>documen|voice/supporting<br>t|Maker/checker|Future Malware/fle v|alidation|
|Separation of<br>duties|Enforce i|ndependent roles|Corporate<br>admin|Future Temporary del|egation|
|Multi-level<br>approval|Larger pa<br>multiple|yment requires<br>approvers|Corporate|Future <sup>Approval sequ</sup><br>timeout|<sup>ence and</sup>|
|Corporate audit<br>history|<br>Full decis|ion timeline|Auditor|Future Immutable ret|ention|
|**AI features**||||||
|**Feature**||**Purpose**||**Priority**|**Edge**<br>**cases**|
|Anomaly detect|ion|Detect unusual pa|yment patterns|Future||
|Dynamic risk sc|oring|Combine historica|l signals|Future||
|Unusual-amoun<br>detection|t|Compare against u|ser baseline|MVP as Java rules;<br>AI later||
|Suspicious-ben<br>detection|efciary|Identify unusual/n<br>benefciaries|ew/frequently fai|led<br>MVP rules; AI later||
|Device/location|anomaly|Identify unfamiliar|access context|MVP with<br>simulated data||
|Personalized tim|er|Adapt delay to risk|and history|Future||
|Scam-risk warni|ngs|Explain suspicious|combinations|Future||



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

|**Feature**|**Purpose**|**Priority**|**Edge**<br>**cases**|
|---|---|---|---|
|Explainable AI|Provide plain-language reasons|Future||
|Fraud analyst summary|Summarize signals for reviewer|Future||



----------------------------------------------------------------------------------------------------------------- 

#### 5. Restricted Risk Investigation Agent 

The Risk Investigation Agent should be a **recommendation-only component** , introduced after the deterministic MVP works. 

##### **Workflow** 

1. Spring Boot gathers permitted transaction facts. 

2. The agent receives a minimal, masked risk context. 

3. It identifies suspicious signals and produces: 

   - risk explanation; 

   - confidence score; 

   - recommended action: immediate release, temporary pause, extra verification, or human review. 

4. Java validates the recommendation against fixed policy rules. 

5. Java records the recommendation in an audit log. 

6. Only Java state-transition services may release, cancel, or settle a transaction. 

##### **Permitted data and tools** 

- Transaction amount and timestamp 

- Beneficiary age, trust indicators, and masked identifier 

- Device trust status 

- Coarse location-risk status, not precise location unless consented 

- Customer’s own aggregated payment history 

- Corporate approval status 

##### **Prohibited actions** 

The agent must never transfer money, settle/release/cancel a transaction, edit settings, modify audit logs, bypass state transitions, or access unrelated customer data. 

##### **Controls** 

- Human review for low-confidence Very High Risk recommendations. 

- Hard policy rules override AI recommendations. 

Confidential - Oracle Restricted \Employees Only 



<!-- Start of picture text -->
Oracle JET Web UI<br>|<br>¥v<br>Spring Scheduler — Spring Boot REST API Optional Al Risk Service<br>— —_——_nl—— [NY oo a —= ~<br>< Q , we — Y<br>Spring Security / RBAC Transaction State Service Java Risk Engine Oracle Database<br>Notification Service<br><!-- End of picture text -->

Confidential - Oracle Restricted \Employees Only 

##### **Main backend modules** 

- auth: registration, login, JWT, roles. 

- beneficiary: add, validate, list, deactivate beneficiary. 

- transaction: creation, authorization, state transitions, idempotency. 

- risk: rules, score calculation, protection decision. 

- verification: OTP challenge and validation. 

- notification: in-app and simulated email/SMS notification. 

- audit: append-only event logging. 

- corporate: Maker-Checker workflow, later. 

- ai: isolated recommendation adapter, later. 

##### **Core Java concepts demonstrated** 

**Java concept SafePay use** Classes / PaymentTransaction, Beneficiary, RiskAssessment; private fields and encapsulation validated methods Interfaces RiskRule, RiskEvaluator, NotificationChannel Inheritance BaseAuditEntity; optionally specialized risk-rule classes Enums TransactionState, RiskTier, ActionType, UserRole Collections / Streams Evaluate rules, filter user transaction history, aggregate signals Generics ApiResponse<T>, RiskRule<T>, paginated results InvalidStateTransitionException, DuplicateTransactionException, Custom exceptions VerificationFailedException Java Time API Instant, Duration, OffsetDateTime for timer calculations Strategy pattern Different amount, device, beneficiary, frequency risk rules State pattern Valid state transitions centralized in a transition service Chain of ResponsibilitySequential risk checks add signals to a risk context CompletableFuture Parallel device, amount, beneficiary-history, and frequency checks Thread safety Per-transaction lock plus database optimistic locking Scheduled tasks Release eligible protected transactions Immutable values Java record RiskAssessment(...) Unit testing JUnit tests for rules, boundary timeouts, idempotency, state machine 

##### **Important classes** 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

enum TransactionState { 

CREATED, AUTHORIZED, PROTECTED, CANCELLED, RELEASED, SETTLED, VERIFICATION_REQUIRED, FAILED 

} 

enum RiskTier { 

LOW, MEDIUM, HIGH, VERY_HIGH 

} 

record RiskAssessment( 

int score, 

RiskTier tier, 

Duration protectionWindow, 

boolean requiresVerification, 

List<String> reasons 

) {} 

public interface RiskRule { 

RiskSignal evaluate(RiskContext context); 

} 

public interface RiskAssessmentService { 

RiskAssessment assess(RiskContext context); 

} 

public interface TransactionStateService { 

PaymentTransaction authorize(UUID transactionId); 

PaymentTransaction cancel(UUID transactionId, String idempotencyKey); 

PaymentTransaction release(UUID transactionId, String idempotencyKey); 

PaymentTransaction settle(UUID transactionId); 

} 

##### **REST API endpoints** 

**Method Endpoint Purpose** POST /api/auth/register Register user 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

|**Method**|**Endpoint**|**Purpose**|
|---|---|---|
|POST|/api/auth/login|Login and receive JWT|
|GET/POST|/api/benefciaries|List/add benefciary|
|PATCH|/api/benefciaries/{id}/status|Deactivate benefciary|
|POST|/api/transactions|Create transaction|
|POST|/api/transactions/{id}/authorize|Authorize and assess risk|
|POST|/api/transactions/{id}/cancel|Undo protected transaction|
|POST|/api/transactions/{id}/verify|Submit OTP|
|POST|/api/transactions/{id}/release|Release after valid conditions|
|GET|/api/transactions/{id}|Get status/timeline|
|GET|/api/transactions|Transaction history|
|PATCH|/api/settings/safety|Update user safety limits|
|GET|/api/audit/transactions/{id}|Auditor/admin timeline|



##### **Oracle database entities** 

- USERS 

- ROLES 

- USER_ROLES 

- BENEFICIARIES 

- PAYMENT_TRANSACTIONS 

- RISK_ASSESSMENTS 

- TRANSACTION_EVENTS 

- OTP_CHALLENGES 

- DEVICE_PROFILES 

- SAFETY_SETTINGS 

- IDEMPOTENCY_KEYS 

- NOTIFICATIONS 

- CORPORATE_APPROVALS — future 

Key transaction columns: 

transaction_id, customer_id, beneficiary_id, amount, currency, state, risk_tier, risk_score, protection_expires_at, 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

version, idempotency_key, created_at, authorized_at, 

released_at, settled_at, cancelled_at 

Use Oracle NUMBER(19,2) for money in the database and BigDecimal in Java—never double or float. 

----------------------------------------------------------------------------------------------------------------- 

#### 7. State-machine design 

|**Current state**|**Action**|**Validation**|**Next state**|**Reversible?**|
|---|---|---|---|---|
|CREATED|User<br>authorizes|Valid benefciary,<br>amount, user<br>authenticated|AUTHORIZED|Yes, by<br>abandonment/ca<br>ncel policy|
|AUTHORIZED|Risk is Low|Rule engine<br>completes|RELEASED|No normal user<br>Undo after<br>release|
||Risk is||||
|AUTHORIZED|Medium/Hig<br>h|Timer calculated|PROTECTED|Yes, before expiry|
|AUTHORIZED|Risk is Very<br>High|Step-up required|<sup>VERIFICATION_REQ</sup><br>RED|<sup>UI</sup><br>Yes, cancel<br>allowed|
|||Current server|||
|PROTECTED|User<br>cancels|time <<br>protection_expire<br>s_at|CANCELLED|Final|
|PROTECTED|Timer<br>expires|Not cancelled<br>and optimistic<br>lock succeeds|RELEASED|No|
|VERIFICATION_REQUI|OTP|Valid non-expired|PROTECTED or|Depends on|
|RED|succeeds|OTP|RELEASED|policy|
|VERIFICATION_REQUI<br>RED|OTP<br>fails/max<br>attempts|Policy evaluation|<sup>CANCELLED or</sup><br>FAILED|No|
|RELEASED|Settlement<br>service runs|Not previously<br>settled|SETTLED|No|
|SETTLED|Refund/<br>dispute<br>request|New workfow<br>only|SETTLED remains|No direct<br>cancellation|
|CANCELLED|Any<br>release/settl|Invalid|Remains CANCELLE|D N/A|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

**Current state** 

**Action Validation Next state** 

##### **Reversible?** 

e request 

Invalid transitions include SETTLED → CANCELLED, CANCELLED → RELEASED, and direct CREATED → SETTLED. 

##### **Concurrency and failure handling** 

- **Cancel exactly at expiry:** the database update must require state = 'PROTECTED' and protection_expires_at > current_timestamp. Only one competing update wins. 

- **Duplicate requests:** require an Idempotency-Key header; store request hash and response for that key. 

- **Two release requests:** use optimistic locking with a version column and conditional update. 

- **Server restart:** protection expiry is stored in Oracle DB, not memory. Scheduler resumes by querying expired protected transactions. 

- **AI unavailable:** run deterministic Java rules and use conservative protection policy. 

- **Network failure after authorization:** client polls GET /transactions/{id} using clientgenerated transaction ID/idempotency key. 

- **Cancel after settlement:** reject cancellation; offer simulated refund/dispute request. 

- **Maker equals Checker:** enforce at query and service level: maker_user_id != checker_user_id. 

Use both: 

1. @Transactional database operations; 

2. JPA @Version optimistic locking; 

3. conditional updates such as WHERE state='PROTECTED' AND version=:version; 

4. optional short-lived Java ReentrantLock keyed by transaction ID for same-JVM contention. 

The database remains the final source of truth. Java locks alone are insufficient if multiple application instances run. 

----------------------------------------------------------------------------------------------------------------- 

#### 8. Risk-engine design 

##### **MVP recommendation** 

Use **deterministic Java rules only** for the MVP. 

##### Use a future **hybrid model** : 

- Java rules enforce mandatory controls and final transition decisions. 

- AI provides a recommendation, explanation, and additional risk signal. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- AI never decides or executes settlement. 

##### **Sample score design** 

|**Signal**|**Score example**|
|---|---|
|Amount is more than 3× normal average|+20|
|Amount exceeds user-defned limit|+35|
|Benefciary added less than 24 hours ago|+25|
|No prior successful payment to benefciary|+15|
|Unknown device|+20|
|High-risk/unfamiliar location|+15|
|More than fve payments in ten minutes|+20|
|Corporate amount exceeds approver limit|+40|



Known trusted beneficiary with normal pattern -15 

Suggested tiers: 

- 0–19: Low → immediate release 

- 20–39: Medium → 10-second protection 

- 40–69: High → 60-second protection 

- 70+: Very High → OTP/manual-review path 

##### **Pseudocode** 

score = 0 

signals = [] 

for each riskRule in rules: 

result = riskRule.evaluate(context) 

score += result.points signals.add(result.reason) 

if score >= 70: 

tier = VERY_HIGH 

action = REQUIRE_VERIFICATION 

timer = 0 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

else if score >= 40: 

tier = HIGH 

action = PROTECT 

timer = 60 seconds 

else if score >= 20: 

tier = MEDIUM 

action = PROTECT 

timer = 10 seconds else: 

tier = LOW 

action = RELEASE 

timer = 0 seconds 

return immutable RiskAssessment(score, tier, timer, action, signals) 

---------------------------------------------------------------------------------------------------------------- 

#### 9. Final MVP recommendation 

##### **One-sentence project definition** 

SafePay is a risk-based, pre-settlement payment-protection simulator that gives users a controlled Undo window or extra verification before risky bank transfers are finally settled. 

##### **Concise final problem statement** 

Instant transfers can cause irreversible financial loss when users make mistakes or act under scam pressure; SafePay reduces that risk by detecting suspicious payment patterns before simulated settlement. 

##### **Exact MVP feature list** 

- Oracle JET login, dashboard, beneficiary management, payment form, transaction history, and Undo screen. 

- Spring Boot REST API with JWT and RBAC. 

- Oracle DB persistence with transaction state, audit history, risk assessment, OTP, and idempotency records. 

- Create, authorize, protect, cancel, release, and settle transaction lifecycle. 

- Java rule-based risk engine. 

- Low/Medium/High/Very High risk tiers. 

- 10-second and 60-second protection windows. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- OTP for Very High Risk. 

- Beneficiary age, amount anomaly, device, location, frequency, threshold, and duplicatepayment rules. 

- Scheduled release of eligible payments. 

- Audit logging, notifications, user-friendly warnings. 

- JUnit tests for rules, state transitions, race conditions, and failure cases. 

##### **Postpone** 

- Production UPI/IMPS integration 

- ML model training 

- Autonomous agentic AI 

- Real biometrics 

- Real SMS/email payment integration 

- Escrow 

- Corporate multi-level approvals, unless time permits 

- Multi-bank settlement integration 

##### **Development plan** 

1. Create Oracle DB schema and seed users, beneficiaries, and sample transactions. 

2. Build Spring Boot authentication, RBAC, JPA entities, and CRUD APIs. 

3. Implement transaction state machine and audit events. 

4. Implement idempotency, optimistic locking, and duplicate prevention. 

5. Build deterministic risk rules and risk-assessment API. 

6. Add protection timer, scheduler, cancellation, and OTP verification. 

7. Build Oracle JET screens and connect REST APIs. 

8. Add notifications, status timeline, and clear risk messages. 

9. Write JUnit/integration tests for concurrent cancellation and release. 

10. Add optional AI recommendation adapter only after the core flow is stable. 

##### **Suggested demonstration scenario** 

1. User adds a new beneficiary. 

2. User creates a ₹4,50,000 transaction from an unfamiliar device. 

3. System scores it as Very High Risk. 

4. Oracle JET shows the reasons: new beneficiary, large amount, unknown device. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

5. System requests OTP and then starts a 60-second protection window. 

6. User realizes this was a scam and clicks Undo. 

7. Transaction becomes CANCELLED. 

8. Dashboard shows that no settlement occurred; audit timeline shows every event. 

##### **Five largest technical risks** 

1. Incorrect state transitions causing double settlement. 

2. Timer-expiry and cancel-button race condition. 

3. Duplicate requests due to network retries. 

4. Treating double as money rather than BigDecimal. 

5. Trusting AI output to execute payment actions. 

##### **Five largest banking/regulatory limitations** 

1. Real UPI/IMPS rails may not permit a post-authorization pause. 

2. Real settlement and reversal rules are controlled by banks, PSPs, NPCI, and regulators. 

3. Device/location data needs consent, privacy controls, and secure handling. 

4. OTP/biometric controls require approved identity and authentication systems. 

5. AI fraud decisions may require explainability, fairness testing, human review, and auditability. 

##### **Difference from a standard payment application** 

A standard payment application mainly validates and executes a transfer. SafePay adds a **riskaware decision layer before settlement** , allowing low-risk payments to remain fast while giving risky payments a warning, delay, Undo option, or extra verification. 

----------------------------------------------------------------------------------------------------------------- 

# SafePay detailed User and Admin flow 

SafePay has two primary actors: 

- **User:** manages beneficiaries, initiates payments, responds to warnings, verifies or cancels transactions, and tracks status. 

- **Admin/Risk Officer:** monitors risky activity, reviews hard-held transactions, manages policies and users, and audits system decisions. Admins must not directly edit transaction records or bypass the Java state machine. 

Confidential - Oracle Restricted \Employees Only 



<!-- Start of picture text -->
User creates payment<br>Very High<br>Additional verification<br>Medium or High “_ Review=<br>Low Verification succeeds<br>Approve under policy Fails or user cancels\<br>Protection window Reject<br>Timer expires User selects Undo<br><!-- End of picture text -->

Confidential - Oracle Restricted \Employees Only 

##### **Step 2: Register** 

The user provides: 

- Full name 

- Email or mobile number 

- Password 

- Optional security-question information 

The backend: 

1. Validates required fields. 

2. Checks whether the email/mobile already exists. 

3. Hashes the password with BCrypt. 

4. Creates a CUSTOMER role. 

5. Creates default safety settings. 

6. Writes a registration audit event. 

##### **Step 3: Login** 

The user enters credentials. 

Possible results: 

##### **Condition** 

##### **Result** 

Credentials valid JWT token issued; dashboard opens Password incorrect Generic authentication error Too many failed attempts Account temporarily locked User inactive/blocked Login rejected with support message 

##### **B. User dashboard** 

After login, the dashboard shows: 

- Simulated account balance 

- Recent transactions 

- Pending protection windows 

- Transactions requiring verification 

- Saved beneficiaries 

- Safety settings 

- Notifications 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

A protected transaction should remain highly visible with: 

- Beneficiary 

- Amount 

- Risk level 

- Countdown 

- Risk explanation 

- Undo button 

##### **C. Add a beneficiary** 

##### **Step 1: Open Beneficiaries** 

The user selects **Add Beneficiary** . 

##### **Step 2: Enter details** 

Example fields: 

- Beneficiary name 

- Bank name 

- Account number or simulated UPI ID 

- IFSC, if applicable 

- Nickname 

- Relationship or purpose, optional 

##### **Step 3: Validate beneficiary** 

SafePay checks: 

- Required fields 

- Valid identifier format 

- Duplicate beneficiary 

- Whether the user is trying to add their own account 

- Whether the beneficiary is already deactivated or flagged 

##### **Step 4: Confirm addition** 

The user confirms using password or simulated OTP. 

The system stores: 

- Beneficiary creation time 

- Verification status 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- First-payment status 

- Trust level 

- Created-by user 

- Audit timestamp 

A newly added beneficiary is initially treated as less trusted. 

##### **D. Create a payment** 

##### **Step 1: Select beneficiary** 

The user selects an existing beneficiary. 

SafePay shows: 

- Beneficiary name 

- Masked account or UPI ID 

- Beneficiary age 

- Date of last successful payment 

- “New beneficiary” warning when applicable 

##### **Step 2: Enter payment details** 

The user enters: 

- Amount 

- Payment purpose 

- Optional reference 

- Source account 

##### **Step 3: Initial validation** 

Before creating the transaction, the backend checks: 

- Amount is greater than zero 

- Amount has no more than two decimal places 

- Sufficient simulated balance 

- Beneficiary is active 

- Daily and per-transaction limits 

- Duplicate or similar recent payment 

- User account is active 

If a similar payment was recently created, the UI displays: 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

A payment of ₹25,000 to this beneficiary was submitted two minutes ago. Do you want to review it? 

The MVP should block an exact retry carrying the same idempotency key. 

##### **Step 4: Final confirmation** 

The confirmation screen prominently shows: 

- “You are sending ₹4,50,000” 

- Beneficiary name 

- Masked destination identifier 

- Beneficiary age 

- Applicable warnings 

- Statement that risky payments may be temporarily protected 

The user may choose: 

- **Confirm and Pay** 

- **Go Back** 

- **Cancel** 

##### **E. Authorization and risk evaluation** 

When the user selects **Confirm and Pay** : 

1. The client sends the payment with an Idempotency-Key. 

2. Spring Security verifies the authenticated user. 

3. The backend verifies ownership of the transaction. 

4. The transaction moves from CREATED to AUTHORIZED. 

5. The Java risk engine evaluates the payment. 

6. The assessment and reasons are saved. 

7. SafePay selects a protection action. 

Risk evaluation may consider: 

- Payment amount compared with user history 

- User-defined limits 

- Beneficiary age 

- Previous successful payments 

- Device trust 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- Location anomaly 

- Recent transaction frequency 

- Possible duplicate payment 

##### **F. Risk-specific user journeys** 

##### **Low Risk** 

Example: ₹500 to a frequently used grocery beneficiary. 

Flow: 

1. Risk engine returns LOW. 

2. Transaction moves from AUTHORIZED to RELEASED. 

3. Settlement service performs simulated debit and credit. 

4. Transaction becomes SETTLED. 

5. User receives a success notification and reference number. 

No Undo window is provided because settlement is immediate. 

##### **Medium Risk** 

Example: ₹25,000 to a known beneficiary, but the amount is unusual. 

Flow: 

1. Risk engine returns MEDIUM. 

2. Transaction moves to PROTECTED. 

3. A 10-second timer begins. 

4. The UI shows the reason and Undo button. 

5. If the user does nothing, the scheduler releases the transaction. 

6. The transaction becomes RELEASED, then SETTLED. 

Example message: 

This amount is higher than your usual payments to this beneficiary. SafePay is holding it for 10 seconds. 

##### **High Risk** 

Example: ₹4,50,000 to a beneficiary added 15 minutes ago. 

Flow: 

1. Risk engine returns HIGH. 

2. Transaction moves to PROTECTED. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

3. A 60-second timer begins. 

4. The UI shows a stronger warning. 

5. The user can review beneficiary details and select Undo. 

6. On expiry, the backend conditionally releases the transaction. 

7. After release, settlement is performed. 

Example message: 

This is a large payment to a recently added beneficiary. Confirm that you know the recipient and are not acting under pressure. 

##### **Very High Risk** 

Example: ₹10,00,000 from an unfamiliar device and location. 

Flow: 

1. Risk engine returns VERY_HIGH. 

2. Transaction moves to VERIFICATION_REQUIRED. 

3. The system creates a short-lived OTP challenge. 

4. The user enters the OTP. 

5. The backend verifies OTP validity, expiry, and attempt count. 

Possible outcomes: 

**Outcome Action** OTP succeeds and policy permits Move to PROTECTED with a 60-second timer OTP succeeds but human review is required Remain held and enter admin review queue OTP is incorrect Allow another attempt within policy Maximum attempts reached Move to FAILED or CANCELLED OTP expires User may request a new OTP User selects Cancel Move to CANCELLED System cannot verify safely Keep on hold; never default to settlement 

##### **G. Undo flow** 

The user can select Undo only while the transaction is in an eligible state. 

1. User selects **Undo Payment** . 

2. UI displays a final cancellation confirmation. 

3. User confirms. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

4. Backend receives an idempotent cancellation request. 

5. Backend checks: 

   - user owns the transaction; 

   - state is PROTECTED or eligible verification state; 

   - protection expiry has not passed; 

   - transaction is not released or settled. 

6. The database atomically changes the state to CANCELLED. 

7. An audit event is recorded. 

8. User sees confirmation that no settlement occurred. 

If the timer expires at exactly the same moment, the database decides the winner. The UI must then refresh and show either: 

- “Payment cancelled,” or 

- “The protection window expired and the payment has already been released.” 

It must never show a successful cancellation unless the database committed it. 

##### **H. Transaction tracking** 

The user can open a transaction timeline: 

**Time Event Example details** 

10:00:00 Created Payment entered 

10:00:05 Authorized User confirmed payment 

10:00:06 Risk assessed High Risk, score 58 

10:00:06 Protected 60-second window started 

10:00:35 Cancelled User selected Undo 

Sensitive internal fraud rules should not be exposed. The user receives plain-language reasons rather than internal rule codes. 

##### **I. After settlement** 

For a SETTLED transaction: 

- Undo is unavailable. 

- Cancellation requests are rejected. 

- The user may create a simulated refund request or dispute. 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- The original transaction stays SETTLED. 

- Any refund is represented as a separate transaction. 

This distinction is essential: SafePay’s Undo feature is a pre-settlement cancellation, not a postsettlement reversal. 

### **3. Admin flow** 

##### **A. Admin login and authorization** 

The admin signs in through a separate admin route. 

The backend verifies: 

- Valid credentials 

- ADMIN, RISK_OFFICER, or AUDITOR role 

- Active account 

- Optional simulated multi-factor authentication 

Recommended role separation: 

##### **Role Main permissions** 

System Admin Manage users, roles and non-financial configuration 

Risk Officer Review held transactions and risk indicators 

Auditor Read-only access to transactions and audit history 

Customer Support View limited customer and transaction information 

A single generic admin account is acceptable for an early college demo, but separate roles demonstrate stronger banking security. 

##### **B. Admin dashboard** 

The dashboard displays operational information such as: 

- Number of protected transactions 

- Very High Risk review queue 

- Failed verification cases 

- Cancelled transactions 

- Duplicate requests detected 

- Scheduler or AI-service failures 

- Recent administrative activity 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

The admin must not see full passwords, OTP values, JWTs, or unnecessary personal information. 

##### **C. Review held transaction** 

This flow applies only when policy requires human review. 

##### **Step 1: Open review queue** 

The risk officer sees transactions in VERIFICATION_REQUIRED or a dedicated UNDER_REVIEW state. 

Queue information: 

- Transaction reference 

- Masked customer identifier 

- Amount 

- Masked beneficiary 

- Risk tier and score 

- Hold duration 

- Reason for review 

- Time waiting 

##### **Step 2: Open transaction details** 

The review page shows permitted risk evidence: 

- Amount compared with the user’s normal range 

- Beneficiary age 

- Previous payment count 

- Device trust status 

- Coarse location status 

- Transaction frequency 

- User verification result 

- Rule-engine explanation 

- Optional AI recommendation and confidence 

- Full state and audit timeline 

##### **Step 3: Make a review decision** 

Possible admin actions: 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

|**Action**|**Required conditions**|**Result**|
|---|---|---|
|Approve for<br>protection|Identity verifcation passed and policy<br>allows approval|Starts protection timer|
|Reject/cancel|Evidence or verifcation fails under<br>defned policy|Moves to CANCELLED|
|Request re-<br>verifcation|OTP expired or evidence is insuficient|Returns to verifcation workfow|
|Escalate|Amount or risk exceeds oficer authority|Assigned to higher-authority<br>reviewer|
|Add internal note|Review context required|Note added to append-only<br>audit history|



An admin approval does not directly settle the payment. It only requests the next valid transition through the Java transaction-state service. 

##### **D. Admin transaction monitoring** 

The admin may search transactions using: 

- Transaction ID 

- Date range 

- Customer identifier 

- Beneficiary identifier 

- State 

- Risk tier 

- Amount range 

Admin actions are limited by state: 

##### **Transaction state Admin capability** 

CREATED View; normally no action AUTHORIZED View risk processing status PROTECTED View countdown; cancellation only under an explicit support policy 

VERIFICATION_REQUIRED Review, request verification, approve or reject under policy 

RELEASED View only; cannot cancel SETTLED View; start separate dispute/refund workflow CANCELLED View only 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

##### **Transaction state** 

##### **Admin capability** 

FAILED View failure and permitted retry information 

##### **E. User administration** 

An authorized system admin may: 

- Search users 

- View account status 

- Lock or unlock a user 

- Disable a compromised account 

- Assign permitted internal roles 

- Review failed-login history 

- Force password reset 

- View registered devices 

- Revoke active sessions 

The admin should not: 

- View the user’s password 

- impersonate the user without a controlled support mechanism; 

- change the user’s payment history; 

- increase limits secretly; 

- delete audit events. 

Every administrative change requires an audit record containing: 

- Admin ID 

- Action 

- Target 

- Old and new values 

- Timestamp 

- Reason 

- Request or correlation ID 

##### **F. Risk-policy administration** 

A restricted policy administrator may configure: 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

- Risk-score boundaries 

- Medium- and High-Risk timer duration 

- Maximum payment limits 

- New-beneficiary cooling period 

- OTP attempt limits 

- Review thresholds 

- Mandatory protection rules 

Recommended safety controls: 

1. Validate all policy values. 

2. Prevent zero-second timers for mandatory-risk cases. 

3. Record old and new values. 

4. Require a reason for each change. 

5. Version the policy. 

6. Apply new policies only to new assessments. 

7. Consider Maker-Checker approval for important policy changes. 

For the college MVP, policy values can initially be stored in Oracle DB and updated only by authorized admins. 

##### **G. Audit flow** 

The auditor opens the audit module and can inspect: 

- Login activity 

- Beneficiary changes 

- Transaction creation and authorization 

- Risk-rule results 

- OTP events without exposing the OTP 

- User cancellation attempts 

- Scheduler releases 

- Admin review decisions 

- Policy changes 

- Settlement results 

- AI recommendations and fallback events 

Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

Audit records are append-only. The UI should not provide edit or delete buttons. 

##### **H. Failure-management flow** 

##### **AI unavailable** 

1. System records AI service failure. 

2. Deterministic Java rules run. 

3. Transaction processing continues conservatively. 

4. Admin sees a service warning. 

5. AI unavailability never results in automatic low-risk classification. 

##### **Scheduler or server restart** 

1. Protection deadlines remain stored in Oracle DB. 

2. After restart, the scheduler queries expired PROTECTED transactions. 

3. Each eligible transaction is conditionally released. 

4. Optimistic locking prevents duplicate release. 

5. Admin sees any failed processing attempts. 

##### **Settlement failure** 

1. Transaction must not be marked SETTLED. 

2. Failure details are logged. 

3. The system retries according to policy or moves to FAILED. 

4. Admin investigates the failure. 

5. Retrying uses the original idempotency key to prevent double debit. 

### **4. Recommended Oracle JET screens** 

##### **Customer screens** 

|**Screen**|**Primary actions**|
|---|---|
|Login/Register|Authenticate or create account|
|Dashboard|View balance, alerts and recent payments|
|Benefciaries|Add, view and deactivate benefciaries|
|New Payment|Select benefciary and enter amount|
|Payment Confrmation|Verify benefciary, amount and warnings|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

##### **Screen** 

##### **Primary actions** 

Protection Window View countdown and select Undo Additional Verification Enter OTP or cancel Payment Result View settled, cancelled, held or failed outcome Transaction History Search and inspect transactions Safety Settings Set personal limits and notification preferences Notifications View security and payment alerts 

##### **Admin screens** 

##### **Screen Primary actions** 

Admin Login Secure admin authentication Operations Dashboard View holds, failures and security alerts Risk Review Queue Process Very High Risk cases Transaction Monitor Search and inspect lifecycle User Management Lock, unlock and manage user status Risk Policy Manage rules and timer settings Audit Viewer Read immutable event history System Health View scheduler, notification and AI status 

### **5. Permission rules** 

|**Capability**|**User**|**Risk Oficer**|**System Adm**|**in Auditor**|
|---|---|---|---|---|
|Create payment|Yes|No|No|No|
|Cancel own protected payme|nt Yes|No|No|No|
|Review Very High Risk hold|No|Yes|Optional|Read only|
|Directly settle payment|No|No|No|No|
|Change risk policy|No|No|Yes|Read only|
|Lock user account|No|No|Yes|Read only|
|View audit history|Own limited|history Relevant cases|Yes|Yes|
|Edit or delete audit records|No|No|No|No|
|Cancel settled payment|No|No|No|No|



Confidential - Oracle Restricted \Employees Only 

Confidential - Oracle Restricted \Employees Only 

#### **6. Important business rules** 

1. Only the transaction owner can cancel through the customer flow. 

2. Cancellation is allowed only before release or settlement. 

3. Every state-changing request requires an idempotency key. 

4. Oracle DB is the final source of transaction state. 

5. Java validates every state transition. 

6. AI can recommend but cannot execute actions. 

7. Admin approval cannot bypass verification, limits, or state rules. 

8. A released or settled transaction cannot return to PROTECTED. 

9. Refunds and disputes are separate post-settlement workflows. 

10. All user, system, scheduler, and admin actions are audited. 

For an MVP demonstration, the strongest end-to-end scenario is: a user adds a beneficiary, initiates a large payment, SafePay detects Very High Risk, requests OTP, places the transaction into a protection window, and the user presses Undo while the admin audit dashboard shows the complete decision timeline. 

Confidential - Oracle Restricted \Employees Only 

