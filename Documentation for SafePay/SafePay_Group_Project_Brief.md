# **SAFEPAY** 

### **Group Project Brief & Build Direction** 

Risk-Adaptive Payment Protection System 

##### **Fast when safe. Careful when necessary.** 

###### PROJECT POSITIONING 

SafePay is a simulated banking transaction-control layer. It does not claim to reverse real UPI/IMPS transactions. It evaluates payment risk and applies the appropriate amount of friction before simulated settlement. 

## **1. Project Overview** 

SafePay is a simulated banking payment-protection system designed around one core question: How much friction should a payment receive before it is released? The system evaluates a transaction, assigns a risk level, and then chooses one of three broad actions: release, protect, or hold. 

## **2. Why We Are Building It** 

Digital payments are designed for speed, but customers and corporate users can still make mistakes or authorize unusual/high-value transactions. Examples include selecting a newly added beneficiary, sending an unusually large amount, or creating a corporate payment that needs a second person's approval. 

Existing banking systems already use authentication, beneficiary verification, fraud monitoring and posttransaction dispute/recovery processes. Therefore, the project should not claim those individual checks are new inventions. 

## **3. Our Focus / USP** 

Our interesting contribution is the orchestration layer: SafePay turns transaction risk signals into a configurable control decision. 

###### **Payment → Risk Signals → Risk Score → Policy Decision → Release / Protect / Hold** 

The project is best presented as a risk-adaptive transaction-control workflow rather than as a new fraud detection system. 

## **4. Simple End-to-End Flow** 

Customer / Maker 

↓ 

Create Payment ↓ Authenticate 

↓ 

SafePay Risk Check 

↓ 

Risk Level? 

- **LOW → Instant Settlement** 

- **MEDIUM / HIGH → Protection / Confirmation → Cancel or Release** 

- **VERY HIGH → Hard Hold → Checker Verification → Approve or Reject** 

↓ 

SETTLED / CANCELLED / REJECTED 

↓ If SETTLED: customer may raise a simulated DISPUTE 

## **5. Adaptive Friction** 

A major product decision is that SafePay should not make every payment slow. Low-risk payments should remain fast. Additional friction appears only when the transaction deserves it. 

|**Example**<br>|**Suggested Experience**|
|---|---|
|₹500 to regular benefciary|Instant settlement<br>|
|₹25,000 unusualpayment<br>|Strongconfrmation / shortprotection|
|₹4,50,000 to new benefciary|Protection window<br>|
|₹10,00,000 + unusual device/location|Manual verifcation / hold|



## **6. Risk Engine** 

Start with a transparent, rule-based engine. Do not make AI/ML the main feature in the first version. 

|**Risk Signal**|**Example Weight**|
|---|---|
|New benefciary|+30|
|High amount|+25|
|First transaction|+20|
|Unusual amount|+15|
|New device|+20|
|Unusual location|+15|
|Unusual time|+5|



|**Risk Score**|**Tier**|**Project Action**|
|---|---|---|
|0–30|LOW|Instant settlement|
|31–60|MEDIUM|Short protection / stronger<br>confrmation|
|61–85|HIGH|Protection window<br>|
|86+|VERY HIGH|Hard hold + verifcation|



The weights and thresholds are project configuration values, not claims about real banking rules. 

## **7. Retail and Corporate Modes** 

##### **Retail** 

Goal: help a customer catch a mistake before settlement. 

Example: High-risk payment → Protected → Customer chooses Cancel or Release. 

##### **Corporate** 

Goal: control high-value payments through Maker-Checker approval. 

Example: Maker creates ₹25 lakh vendor payment → Hold → Checker reviews → Approve / Reject. 

## **8. Transaction State Machine** 

CREATED → AUTHORIZED → RISK_ASSESSED 

LOW → SETTLED 

MEDIUM/HIGH → PROTECTED → CANCELLED or SETTLED 

VERY HIGH → HARD_HOLD → PENDING_APPROVAL → APPROVED/REJECTED 

SETTLED → DISPUTED → UNDER_REVIEW → RESOLVED / REJECTED 

## **9. Core Database Model** 

|**Table**|**Purpose**|
|---|---|
|ROLES|Role master data|
|USERS|Customers,Makers,Checkers,Admins|
|ACCOUNTS|Simulated bank accounts and balances|
|BENEFICIARIES|Saved recipients|
|TRANSACTIONS|Mainpayment and state table<br>|
|RISK_FACTORS|Confgurable risk rules/weights|
|TRANSACTION_RISK_FACTORS|Whya transaction received its score|
|PROTECTION_RULES|Maps risk tier to action/protection duration|
|USER_SAFETY_SETTINGS|Customer safety preferences|
|APPROVALS|Maker-Checker records<br>|
|DISPUTES|Post-settlement dispute workfow|
|AUDIT_LOG|Historyof important actions/state changes|



## **10. Important Oracle Requirements** 

Use Oracle sequences for generated IDs, primary/foreign keys, NOT NULL/UNIQUE/CHECK constraints, sample data, and reporting views. 

Recommended sequences: SEQ_USER_ID, SEQ_ACCOUNT_ID, SEQ_BENEFICIARY_ID, SEQ_TRANSACTION_ID, SEQ_RISK_FACTOR_ID, SEQ_RULE_ID, SEQ_APPROVAL_ID, SEQ_DISPUTE_ID, SEQ_AUDIT_ID. 

## **11. Reporting / Complex Views** 

|**View**|**Purpose**|
|---|---|
|VW_TRANSACTION_DASHBOARD|Admin transaction monitoring|
|VW_RISK_SUMMARY|Risk-tier counts,amounts and averages|
|VW_PENDING_APPROVALS|Maker-Checkerqueue|
|VW_CUSTOMER_PROTECTION_ANALYSIS|Customer-level safetyanalytics|



## **12. Main Features** 

|**Area**|**Features**<br>|
|---|---|
|Customer|Login, accounts, benefciaries, payment, protection screen,<br>history,safetysettings,disputes|
|Admin|Dashboard, monitoring, rules, approvals, disputes, audit<br>logs,reports|
|Corporate|Maker creation and Checker approval|
|Backend|Risk engine, transaction state machine, protection logic,<br>authorization|
|Database|Oracle tables,sequences,constraints and complex views|



## **13. Expected Challenges** 

**Existing risk checks are not novel:** Do not claim novelty in individual signals. Focus on orchestration and policydriven control. 

**Payment delay hurts user experience:** Use adaptive friction; keep normal payments instant. 

**Real payment rails cannot simply be paused by our app:** Position the project as a simulated transaction engine and explain that a real implementation would require integration inside the bank's authorization/paymentprocessing architecture. 

**Timer manipulation:** The frontend timer is visual only. Backend validates PROTECTION_END. 

**Too many features:** Build the MVP first. Add advanced features only after the core flow is stable. 

**AI/ML complexity:** Use a transparent rule-based engine now; treat ML as future work. 

## **14. What We Should NOT Claim** 

- “We invented fraud detection.” 

- “Banks cannot detect these transactions.” 

- “SafePay can reverse any UPI/IMPS payment.” 

- “Our 60-second timer works on live UPI.” 

- “SafePay completely prevents online fraud.” 

**Preferred wording:** “SafePay is a simulated risk-adaptive pre-settlement transaction-control layer.” 

## **15. MVP — What We Must Get Working First** 

###### **The first successful demo should prove one complete story:** 

1. Login 

2. Select/create beneficiary 

3. Create payment 

4. Risk calculation 

5. Low-risk instant settlement 

6. High-risk protection with Cancel / Release 

7. Very-high-risk hold with Checker approval 

8. Transaction history 

9. Basic Admin Dashboard 

## **16. Best Demonstration Scenarios** 

|**Scenario**|**Expected Result**|
|---|---|
|₹500 regular benefciary|LOW → SETTLED|
|<br>₹25,000 unusual existing-benefciary payment|MEDIUM → Protected / confrmation|
|₹4,50,000 new benefciary+ frstpayment|HIGH → Protected → Cancelled or Settled|
|<br>₹10,00,000 + new device/location|VERY HIGH → Hard Hold → Checker → Approved/Rejected|



## **17. Team Workstreams** 

|**Workstream**|**Ownership**|
|---|---|
|Oracle / Database|ER diagram, tables, sequences, constraints, views, sample<br>data|
|Java / Backend|Transaction API, risk engine, state machine, protection and<br>approval logic|
|Frontend|Customer dashboard, payment fow, protection screen,<br>history|
|Admin / QA|Admin dashboard, Maker-Checker, disputes, audit, test<br>cases and demo data|



## **18. Recommended Build Order** 

10. Finalize requirements and state transitions 

11. Finalize ER diagram 

12. Create Oracle tables + sequences + constraints 

13. Insert sample data 

14. Build basic payment API 

15. Build rule-based risk engine 

16. Implement transaction state machine 

17. Implement protection / cancel / release 

18. Implement Maker-Checker 

19. Build customer UI 

20. Build Admin UI 

21. Create Oracle reporting views 

22. Add audit logs and disputes 

23. Run end-to-end tests 

24. Prepare final demonstration 

## **19. Team Decisions Required Before Coding** 

- Frontend technology 

- Java/Spring Boot version 

- MVP risk factors 

- Risk thresholds 

- Protection expiry behavior 

- Transactions requiring Maker-Checker 

- Required Oracle views 

- Final demo scenarios 

- MVP vs future enhancements 

- Module owner for each workstream 

## **20. Final Project Positioning** 

SafePay is a simulated banking transaction-control platform that uses transaction risk signals to determine the appropriate amount of friction before settlement — instant release for normal payments, temporary protection for selected transactions, and additional authorization for high-risk payments. 

#### **FAST WHEN SAFE. CAREFUL WHEN NECESSARY.** 

