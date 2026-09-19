# SafePay V1 — Data Seed Complete Guide

## 1. Purpose and authority

This guide is the operational and team-reference companion to the approved Flyway seed migration:

`backend/src/main/resources/db/migration/V12__safepay_v1_showcase_seed.sql`

V12 creates one persistent, coherent SafePay V1 showcase dataset after the canonical V1–V11 schema and reference data. It is intended for local launch, management demonstration, manual API verification and later phase-wise API test guides. It is not production data and it does not represent real bank customers or routable payment instructions.

The governing authority order remains:

1. the user's latest explicit decision;
2. `UPDATED_Decision_Register.md`;
3. the approved implementation plan and operative agent contract;
4. `dbsetup_cum_backend_master_guide_contract.md`;
5. phase guides and source code;
6. older planning material.

This document explains the current approved V12. It does not authorize any schema or backend change.

## 2. Current approval and verification status

| Item | Status |
|---|---|
| V12 file contents | Approved by the user for the current baseline |
| V1–V11 modification | None |
| Static dataset reconciliation | Completed while authoring V12 |
| V12 migration-local acceptance checks | Included |
| Actual Oracle/Flyway execution of V12 | Not yet verified |
| Backend launch with the seeded schema | Pending the separate launch guide and user-run verification |
| Live Gmail OTP delivery | Fixed-recipient routing is implemented and unit-verified; Gmail App Password setup and live receipt verification remain manual |

Approval of the SQL file does not mean that Oracle has applied it. The authoritative runtime proof will be a successful Flyway row for version 12 plus the read-only verification queries in this guide.

## 3. Explicitly deferred decisions — do not implement from this guide

The following ideas are retained for later review but are not present in V12, V1–V11 or the current backend contract:

| Deferred item | Current position |
|---|---|
| Customer address | Possible later registration field. No column, seed value, DTO or validation is added now. |
| PAN-style KYC | Possible simulated format validation only. A PAN value must not be stored. No seed value or database field is added now. |
| Two presentation personas | The team may present only `CUSTOMER` and a combined “admin” persona later. The canonical database authorities remain `CUSTOMER`, `RISK_OFFICER`, `SYSTEM_ADMIN`, `AUDITOR`. |
| Combined admin authority | If separately approved, one administrative user can receive three `USER_ROLE` rows for `SYSTEM_ADMIN`, `RISK_OFFICER` and `AUDITOR`; the physical role model and authorization checks must not be silently collapsed. |
| Other similar changes | Require a new explicit decision, impact review and forward-only migration/implementation plan. |

V12 deliberately seeds three separate staff users so every authority boundary can be tested independently today.

## 4. Dataset design principles

- All names, contact details, account numbers, UPI handles and payment narratives are synthetic.
- Customer emails look realistic for presentation, but may coincidentally belong to real people. They must never receive SafePay mail.
- No field contains the word prohibited by the approved naming requirement.
- Passwords are stored only as unique BCrypt cost-12 hashes. Plaintext credentials appear only in this controlled local guide.
- All account numbers fit the schema and intentionally vary in length to resemble common Indian-bank presentation styles; they are not asserted to be real or routable.
- IFSC-shaped values satisfy SafePay's canonical 11-character check: four uppercase letters, `0`, then six uppercase letters/digits.
- INR is the only seeded currency.
- Every positive customer opening balance has a balanced immutable two-line ledger origin.
- Every settled payment has one balanced immutable two-line settlement posting.
- No raw OTP is stored.
- No `PENDING` OTP is seeded, preventing a reusable known verification secret.
- No active `PROTECTED` countdown is seeded, preventing the scheduler from immediately changing showcase state on startup.
- Runtime security sessions and idempotency records are not premanufactured.

## 5. One-run Flyway execution model

V12 is a normal versioned migration. No batch file, manual block-by-block SQL execution or extra table-creation script is required.

```text
Application startup
  -> Flyway connects as SAFEPAY_OWNER
  -> validates V1–V11 checksums and naming
  -> confirms V12 is pending
  -> executes the whole V12 file in order
  -> V12 acceptance checks pass
  -> Flyway records version 12 as successful
  -> JPA connects as SAFEPAY_APP and validates mappings
```

Credential separation is mandatory:

| Credential | Permitted purpose |
|---|---|
| `SAFEPAY_DB_OWNER_USERNAME` / `SAFEPAY_DB_OWNER_PASSWORD` | Flyway DDL, reference data and controlled V12 fixture creation |
| `SAFEPAY_DB_APP_USERNAME` / `SAFEPAY_DB_APP_PASSWORD` | Restricted application runtime operations only |

Relevant application settings already enforce:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.clean-disabled=true
spring.flyway.baseline-on-migrate=false
spring.flyway.out-of-order=false
spring.flyway.validate-on-migrate=true
spring.flyway.validate-migration-naming=true
```

Do not run V12 directly in SQL Developer after Flyway has applied it. Do not change V12 after successful application. Any approved correction must be a forward-only V13 or later migration.

## 6. Prerequisites before first application

1. Use a disposable local/development SafePay schema, never a production or shared banking schema.
2. Confirm V1–V11 are present and successful in Flyway history.
3. Confirm no row for version 12 exists yet.
4. Confirm the four canonical V11 roles and the active `AMOUNT_ONLY_V1` policy exist.
5. Confirm the V12 marker identifiers do not already exist:
   - `priya.nair@gmail.com`;
   - `SAFEPAY_OUTBOUND_CLEARING`;
   - `SAFEPAY_OPENING_BALANCE_CONTROL`;
   - transaction references beginning `SPV1-SHOWCASE-`.
6. Preserve a schema backup/snapshot before the first run if the environment contains work worth retaining.
7. Keep OTP email delivery disabled.
8. Keep the settlement processor disabled until its clearing-account ID is resolved and deliberately configured.

The current safe defaults are:

```properties
safepay.settlement-processor.enabled=false
safepay.otp.email.enabled=false
```

## 7. V12 insertion and dependency order

V12 resolves generated IDs by stable natural markers instead of assuming sequence numbers.

| Order | Seeded object | Dependency reason |
|---:|---|---|
| 1 | Preflight contract checks | Stops before writes if V11 roles, risk policy or uniqueness markers conflict. |
| 2 | 30 customer and 3 staff `APP_USER` rows | Parent identities for ownership and actors. |
| 3 | 33 `USER_ROLE` rows | Resolves role IDs from canonical V11 data. |
| 4 | Two ownerless internal `ACCOUNT` rows | Required contra and clearing destinations. |
| 5 | 30 customer `ACCOUNT` rows | Owned source accounts for payments. |
| 6 | 29 opening-balance `LEDGER_POSTING` rows and 58 entries | Creates an auditable origin for every positive opening balance. |
| 7 | 30 `BENEFICIARY` rows | Owned destinations used by transaction scenarios. |
| 8 | 25 `PAYMENT_TRANSACTION` rows | Core state-machine scenarios. |
| 9 | 22 `TRANSACTION_RISK_FACTOR` rows | Immutable amount factors for all risk-assessed payments. |
| 10 | Six terminal `PAYMENT_OTP_CHALLENGE` rows | Historical verification evidence only. |
| 11 | Five `RISK_REVIEW` rows | Pending and terminal officer workflows. |
| 12 | Nine settlement postings and 18 entries | Double-entry evidence for the nine settled payments. |
| 13 | Two `TRANSACTION_EXCEPTION` rows | Resolved and manual-review failure paths. |
| 14 | Twelve `AUDIT_LOG` rows | Immutable lifecycle evidence. |
| 15 | Ten `APP_NOTIFICATION` rows | Pending, delivered and failed in-app delivery examples. |
| 16 | Acceptance checks | Aborts V12 if counts or financial reconciliations differ. |

## 8. Credentials and personas

### 8.1 Customer login catalogue

All users are initially `ACTIVE`, have `failed_login_count = 0`, `security_version = 0` and exactly one `CUSTOMER` assignment.

| # | Customer | Email / login | Local showcase password |
|---:|---|---|---|
| 101 | Priya Nair | `priya.nair@gmail.com` | `SafePay@PNair101` |
| 102 | Arjun Menon | `arjun.menon@gmail.com` | `SafePay@AMenon102` |
| 103 | Kavya Sharma | `kavya.sharma@gmail.com` | `SafePay@KSharma103` |
| 104 | Rohan Mehta | `rohan.mehta@gmail.com` | `SafePay@RMehta104` |
| 105 | Ananya Iyer | `ananya.iyer@gmail.com` | `SafePay@AIyer105` |
| 106 | Vivek Reddy | `vivek.reddy@gmail.com` | `SafePay@VReddy106` |
| 107 | Sneha Kulkarni | `sneha.kulkarni@gmail.com` | `SafePay@SKulkarni107` |
| 108 | Rahul Verma | `rahul.verma@gmail.com` | `SafePay@RVerma108` |
| 109 | Ishita Banerjee | `ishita.banerjee@gmail.com` | `SafePay@IBanerjee109` |
| 110 | Karthik Subramanian | `karthik.subramanian@gmail.com` | `SafePay@KSubramanian110` |
| 111 | Neha Gupta | `neha.gupta@gmail.com` | `SafePay@NGupta111` |
| 112 | Aditya Joshi | `aditya.joshi@gmail.com` | `SafePay@AJoshi112` |
| 113 | Pooja Deshmukh | `pooja.deshmukh@gmail.com` | `SafePay@PDeshmukh113` |
| 114 | Sanjay Patel | `sanjay.patel@gmail.com` | `SafePay@SPatel114` |
| 115 | Divya Rao | `divya.rao@gmail.com` | `SafePay@DRao115` |
| 116 | Manish Agarwal | `manish.agarwal@gmail.com` | `SafePay@MAgarwal116` |
| 117 | Aisha Khan | `aisha.khan@gmail.com` | `SafePay@AKhan117` |
| 118 | Nikhil Chawla | `nikhil.chawla@gmail.com` | `SafePay@NChawla118` |
| 119 | Meera Pillai | `meera.pillai@gmail.com` | `SafePay@MPillai119` |
| 120 | Varun Kapoor | `varun.kapoor@gmail.com` | `SafePay@VKapoor120` |
| 121 | Ritu Singh | `ritu.singh@gmail.com` | `SafePay@RSingh121` |
| 122 | Harish Gowda | `harish.gowda@gmail.com` | `SafePay@HGowda122` |
| 123 | Tanvi Shah | `tanvi.shah@gmail.com` | `SafePay@TShah123` |
| 124 | Suresh Yadav | `suresh.yadav@gmail.com` | `SafePay@SYadav124` |
| 125 | Nandini Bose | `nandini.bose@gmail.com` | `SafePay@NBose125` |
| 126 | Akash Mishra | `akash.mishra@gmail.com` | `SafePay@AMishra126` |
| 127 | Lakshmi Narayanan | `lakshmi.narayanan@gmail.com` | `SafePay@LNarayanan127` |
| 128 | Mohit Sethi | `mohit.sethi@gmail.com` | `SafePay@MSethi128` |
| 129 | Shruti Das | `shruti.das@gmail.com` | `SafePay@SDas129` |
| 130 | Dev Malhotra | `dev.malhotra@gmail.com` | `SafePay@DMalhotra130` |

These passwords are local prototype credentials. Never reuse them outside the disposable SafePay environment.

### 8.2 Staff personas

| Persona | Email / login | Password | Current canonical authority | Intended checks |
|---|---|---|---|---|
| Rhea Malhotra | `rhea.malhotra@gmail.com` | `SafePay@RMalhotra131` | `RISK_OFFICER` | Pending-review queue, approve, reject, request re-verification; no system-admin/auditor authority. |
| Vikram Bhat | `vikram.bhat@gmail.com` | `SafePay@VBhat132` | `SYSTEM_ADMIN` | Administrative user security controls; no Risk Officer decision authority. |
| Anjali Thomas | `anjali.thomas@gmail.com` | `SafePay@AThomas133` | `AUDITOR` | Global sanitized audit search; no payment mutation authority. |

The possible future combined-admin presentation is deferred. These three accounts are the correct current security test fixtures.

## 9. Customer account catalogue

`Current` is the post-settlement account balance inserted by V12. `Reserved` is locked for nonterminal pre-settlement payments but is still included in `Current`; spendable funds are `Current - Reserved`.

| # | Bank | Synthetic account | IFSC-shaped value | Type | Opening | Settled debit | Current | Reserved | Status |
|---:|---|---|---|---|---:|---:|---:|---:|---|
| 101 | HDFC Bank | `50100123456789` | `HDFC0000001` | SAVINGS | ₹350,000.00 | ₹0.00 | ₹350,000.00 | ₹150,000.00 | ACTIVE |
| 102 | State Bank of India | `12345678901` | `SBIN0000456` | SAVINGS | ₹120,000.00 | ₹2,500.00 | ₹117,500.00 | ₹0.00 | ACTIVE |
| 103 | ICICI Bank | `100123456789` | `ICIC0001234` | SAVINGS | ₹200,000.00 | ₹0.00 | ₹200,000.00 | ₹0.00 | ACTIVE |
| 104 | Axis Bank | `911010123456789` | `UTIB0000123` | CURRENT | ₹500,000.00 | ₹75,000.00 | ₹425,000.00 | ₹0.00 | ACTIVE |
| 105 | Kotak Mahindra Bank | `12345678901234` | `KKBK0000456` | SAVINGS | ₹300,000.00 | ₹0.00 | ₹300,000.00 | ₹110,000.00 | ACTIVE |
| 106 | Punjab National Bank | `0153001234567890` | `PUNB0015300` | SAVINGS | ₹275,000.00 | ₹0.00 | ₹275,000.00 | ₹0.00 | ACTIVE |
| 107 | Bank of Baroda | `20123456789012` | `BARB0MUMBAI` | SAVINGS | ₹180,000.00 | ₹0.00 | ₹180,000.00 | ₹0.00 | ACTIVE |
| 108 | Canara Bank | `1234567890123` | `CNRB0001234` | SAVINGS | ₹250,000.00 | ₹0.00 | ₹250,000.00 | ₹0.00 | ACTIVE |
| 109 | Union Bank of India | `123456789012345` | `UBIN0531234` | CURRENT | ₹400,000.00 | ₹125,000.00 | ₹275,000.00 | ₹0.00 | ACTIVE |
| 110 | Federal Bank | `40123456789012` | `FDRL0001234` | SAVINGS | ₹260,000.00 | ₹0.00 | ₹260,000.00 | ₹120,000.00 | ACTIVE |
| 111 | IDFC FIRST Bank | `501234567890` | `IDFB0040101` | SAVINGS | ₹100,000.00 | ₹5,000.01 | ₹94,999.99 | ₹0.00 | ACTIVE |
| 112 | YES Bank | `601234567890123` | `YESB0000123` | CURRENT | ₹150,000.00 | ₹25,000.01 | ₹124,999.99 | ₹0.00 | ACTIVE |
| 113 | IndusInd Bank | `70123456789012` | `INDB0000123` | SAVINGS | ₹60,000.00 | ₹5,000.00 | ₹55,000.00 | ₹0.00 | ACTIVE |
| 114 | Bank of India | `801234567890123` | `BKID0000123` | CURRENT | ₹90,000.00 | ₹25,000.00 | ₹65,000.00 | ₹0.00 | ACTIVE |
| 115 | Indian Bank | `90123456789` | `IDIB000A123` | SAVINGS | ₹250,000.00 | ₹100,000.00 | ₹150,000.00 | ₹0.00 | ACTIVE |
| 116 | HDFC Bank | `50100123456790` | `HDFC0000001` | SAVINGS | ₹200,000.00 | ₹0.00 | ₹200,000.00 | ₹100,000.01 | ACTIVE |
| 117 | State Bank of India | `12345678902` | `SBIN0000456` | SAVINGS | ₹75,000.00 | ₹0.00 | ₹75,000.00 | ₹0.00 | ACTIVE |
| 118 | ICICI Bank | `100123456790` | `ICIC0001234` | SAVINGS | ₹85,000.00 | ₹0.00 | ₹85,000.00 | ₹0.00 | ACTIVE |
| 119 | Axis Bank | `911010123456790` | `UTIB0000123` | CURRENT | ₹500.00 | ₹0.00 | ₹500.00 | ₹0.00 | ACTIVE |
| 120 | Kotak Mahindra Bank | `12345678901235` | `KKBK0000456` | SAVINGS | ₹50,000.00 | ₹0.00 | ₹50,000.00 | ₹0.00 | ACTIVE |
| 121 | Punjab National Bank | `0153001234567891` | `PUNB0015300` | SAVINGS | ₹65,000.00 | ₹0.00 | ₹65,000.00 | ₹0.00 | ACTIVE |
| 122 | Bank of Baroda | `20123456789013` | `BARB0MUMBAI` | SAVINGS | ₹150,000.00 | ₹0.00 | ₹150,000.00 | ₹0.00 | ACTIVE |
| 123 | Canara Bank | `1234567890124` | `CNRB0001234` | SAVINGS | ₹100,000.00 | ₹0.00 | ₹100,000.00 | ₹4,500.00 | ACTIVE |
| 124 | Union Bank of India | `123456789012346` | `UBIN0531234` | CURRENT | ₹175,000.00 | ₹0.00 | ₹175,000.00 | ₹60,000.00 | ACTIVE |
| 125 | Federal Bank | `40123456789013` | `FDRL0001234` | SAVINGS | ₹0.00 | ₹0.00 | ₹0.00 | ₹0.00 | ACTIVE |
| 126 | IDFC FIRST Bank | `501234567891` | `IDFB0040101` | SAVINGS | ₹10,000.00 | ₹0.00 | ₹10,000.00 | ₹0.00 | INACTIVE |
| 127 | YES Bank | `601234567890124` | `YESB0000123` | CURRENT | ₹500,000.00 | ₹0.00 | ₹500,000.00 | ₹0.00 | ACTIVE |
| 128 | IndusInd Bank | `70123456789013` | `INDB0000123` | SAVINGS | ₹1.00 | ₹1.00 | ₹0.00 | ₹0.00 | ACTIVE |
| 129 | Bank of India | `801234567890124` | `BKID0000123` | SAVINGS | ₹30,000.00 | ₹0.00 | ₹30,000.00 | ₹0.00 | ACTIVE |
| 130 | Indian Bank | `90123456790` | `IDIB000A123` | CURRENT | ₹999,999.99 | ₹0.00 | ₹999,999.99 | ₹0.00 | ACTIVE |

### Internal accounts

| Account | Type | Owner | Current balance | Meaning |
|---|---|---|---:|---|
| `SAFEPAY_OUTBOUND_CLEARING` | `OUTBOUND_CLEARING` | none | ₹362,501.02 | Simulated outbound side of nine settled payments. |
| `SAFEPAY_OPENING_BALANCE_CONTROL` | `OPENING_BALANCE_CONTROL` | none | ₹0.00 account snapshot | Contra account used to create balanced customer opening histories; its ledger net is intentionally negative. |

After V12 is applied, resolve the generated clearing ID rather than guessing it:

```sql
SELECT account_id
FROM ACCOUNT
WHERE account_number = 'SAFEPAY_OUTBOUND_CLEARING';
```

That result is the later value for `SAFEPAY_OUTBOUND_CLEARING_ACCOUNT_ID` when the settlement processor is deliberately enabled.

## 10. Bank-format rationale

SafePay validates schema shape, not bank connectivity. The seed varies lengths to prove the backend does not make a false one-length assumption.

| Reference point | Seed usage |
|---|---|
| RBI describes IFSC as 11 characters: first four bank characters, fifth character `0`, last six branch characters | Every bank-account beneficiary and customer bank account uses this SafePay-compatible shape. |
| SBI guidance commonly presents 11-digit account numbers | SBI-shaped synthetic values use 11 digits. |
| HDFC guidance commonly presents 14-digit account numbers | HDFC-shaped synthetic values use 14 digits. |
| Axis material commonly presents 15-digit account numbers | Axis-shaped synthetic values use 15 digits. |
| PNB material commonly presents 16-digit account numbers | PNB-shaped synthetic values use 16 digits. |
| Canara material commonly presents 13-digit account numbers | Canara-shaped synthetic values use 13 digits. |
| Union Bank material commonly presents 15-digit account numbers | Union-shaped synthetic values use 15 digits. |
| Federal material commonly presents 14-digit account numbers | Federal-shaped synthetic values use 14 digits. |
| IDFC FIRST notes Indian account numbers may vary broadly | The full dataset intentionally spans multiple valid lengths. |

Official reference links used during seed design:

- RBI IFSC FAQ: <https://systemhealth.rbi.org.in/Scripts/FAQView.aspx_Id%3D60%281%29.html>
- HDFC account-number guidance: <https://www.hdfcbank.com/personal/resources/learning-centre/digital-banking/know-how-to-find-bank-account-number-hindi>
- SBI internet-banking FAQ: <https://sbi.co.in/hi/web/faq-s/faq-internet-banking>
- Axis auto-debit form: <https://www.axisbank.com/docs/default-source/download-document/personal/cards/auto-debit-form.pdf?sfvrsn=2>
- PNB form: <https://www.pnbindia.in/hi/document/NRIservices/PNB_1068_atmform.pdf>
- Canara remittance proforma: <https://canarabank.com/UploadedFiles/Pdf/proforma-swift-rem-usd10k.pdf>
- Union Bank circular: <https://www.unionbankofindia.co.in/pdf/staff-circular08221.pdf>
- Federal Bank statement guidance: <https://www.federalbank.co.in/en/how-to-download-federal-bank-account-statement>
- IDFC FIRST overview: <https://www.idfcfirstbank.com/finfirst-blogs/savings-account/how-to-find-bank-account-number/amp>

These sources inform presentation length only. None validates any seeded number as a real account.

## 11. Beneficiary catalogue

Every customer owns one beneficiary. The chain intentionally mixes `BANK_ACCOUNT` and `UPI`. All are `ACTIVE` except Shruti Das's `Dev Disabled` beneficiary.

| Owner # | Beneficiary | Method | Destination | Relationship | Status |
|---:|---|---|---|---|---|
| 101 | Arjun Menon | BANK_ACCOUNT | SBI `12345678901` | FAMILY | ACTIVE |
| 102 | Kavya Sharma | UPI | `kavya.sharma@okicici` | FRIEND | ACTIVE |
| 103 | Rohan Mehta | BANK_ACCOUNT | Axis `911010123456789` | LANDLORD | ACTIVE |
| 104 | Ananya Iyer | UPI | `ananya.iyer@okhdfcbank` | COLLEAGUE | ACTIVE |
| 105 | Vivek Reddy | BANK_ACCOUNT | PNB `0153001234567890` | FAMILY | ACTIVE |
| 106 | Sneha Kulkarni | UPI | `sneha.kulkarni@okaxis` | FRIEND | ACTIVE |
| 107 | Rahul Verma | BANK_ACCOUNT | Canara `1234567890123` | FAMILY | ACTIVE |
| 108 | Ishita Banerjee | UPI | `ishita.banerjee@okicici` | FRIEND | ACTIVE |
| 109 | Karthik Subramanian | BANK_ACCOUNT | Federal `40123456789012` | VENDOR | ACTIVE |
| 110 | Neha Gupta | UPI | `neha.gupta@okhdfcbank` | FAMILY | ACTIVE |
| 111 | Aditya Joshi | BANK_ACCOUNT | YES `601234567890123` | FAMILY | ACTIVE |
| 112 | Pooja Deshmukh | UPI | `pooja.deshmukh@okaxis` | FRIEND | ACTIVE |
| 113 | Sanjay Patel | BANK_ACCOUNT | BOI `801234567890123` | VENDOR | ACTIVE |
| 114 | Divya Rao | UPI | `divya.rao@okicici` | COLLEAGUE | ACTIVE |
| 115 | Manish Agarwal | BANK_ACCOUNT | HDFC `50100123456790` | FAMILY | ACTIVE |
| 116 | Aisha Khan | UPI | `aisha.khan@okhdfcbank` | FRIEND | ACTIVE |
| 117 | Nikhil Chawla | BANK_ACCOUNT | ICICI `100123456790` | INSTITUTION | ACTIVE |
| 118 | Meera Pillai | UPI | `meera.pillai@okaxis` | FRIEND | ACTIVE |
| 119 | Varun Kapoor | BANK_ACCOUNT | Kotak `12345678901235` | FAMILY | ACTIVE |
| 120 | Ritu Singh | UPI | `ritu.singh@okicici` | FRIEND | ACTIVE |
| 121 | Harish Gowda | BANK_ACCOUNT | BOB `20123456789013` | LANDLORD | ACTIVE |
| 122 | Tanvi Shah | UPI | `tanvi.shah@okhdfcbank` | COLLEAGUE | ACTIVE |
| 123 | Suresh Yadav | BANK_ACCOUNT | Union `123456789012346` | VENDOR | ACTIVE |
| 124 | Nandini Bose | UPI | `nandini.bose@okaxis` | FAMILY | ACTIVE |
| 125 | Akash Mishra | BANK_ACCOUNT | IDFC `501234567891` | FAMILY | ACTIVE |
| 126 | Lakshmi Narayanan | UPI | `lakshmi.narayanan@okicici` | COLLEAGUE | ACTIVE |
| 127 | Mohit Sethi | BANK_ACCOUNT | IndusInd `70123456789013` | FAMILY | ACTIVE |
| 128 | Shruti Das | UPI | `shruti.das@okhdfcbank` | FRIEND | ACTIVE |
| 129 | Dev Malhotra | BANK_ACCOUNT | Indian Bank `90123456790` | VENDOR | DISABLED |
| 130 | Priya Nair | UPI | `priya.nair@okaxis` | FRIEND | ACTIVE |

The disabled-beneficiary case deliberately has no persisted transaction. The API must reject it before transaction creation.

## 12. Transaction and risk scenario matrix

The canonical active policy is amount-only:

| Amount | Expected tier | Protection / verification action |
|---:|---|---|
| ₹1.00–₹5,000.00 | LOW | Immediate release path |
| ₹5,000.01–₹25,000.00 | MEDIUM | 10-second protection window |
| ₹25,000.01–₹100,000.00 | HIGH | 60-second protection window |
| ₹100,000.01 and above | VERY_HIGH | OTP then independent Risk Officer review |

| Ref | Customer | Amount | Tier | Seeded state | Reserved | Purpose / value of scenario |
|---|---|---:|---|---|---:|---|
| 101 | Priya | ₹150,000.00 | VERY_HIGH | PENDING_RISK_REVIEW | ₹150,000.00 | Verified OTP and live pending officer decision. |
| 102 | Arjun | ₹2,500.00 | LOW | SETTLED | ₹0.00 | Normal low-risk settlement. |
| 103 | Kavya | ₹15,000.00 | MEDIUM | CANCELLED | ₹0.00 | Customer Undo before settlement. |
| 104 | Rohan | ₹75,000.00 | HIGH | SETTLED | ₹0.00 | High-risk protection completed then settled. |
| 105 | Ananya | ₹110,000.00 | VERY_HIGH | VERIFICATION_REQUIRED | ₹110,000.00 | Clean initial OTP-issue target; no challenge is seeded. |
| 106 | Vivek | ₹125,000.00 | VERY_HIGH | CANCELLED | ₹0.00 | Verified OTP and cancelled review history. |
| 107 | Sneha | ₹130,000.00 | VERY_HIGH | CANCELLED | ₹0.00 | OTP attempts exhausted and challenge locked. |
| 108 | Rahul | ₹140,000.00 | VERY_HIGH | CANCELLED | ₹0.00 | Risk Officer rejection. |
| 109 | Ishita | ₹125,000.00 | VERY_HIGH | SETTLED | ₹0.00 | Full OTP, review approval and settlement path. |
| 110 | Karthik | ₹120,000.00 | VERY_HIGH | VERIFICATION_REQUIRED | ₹120,000.00 | Re-verification requested; old challenge cancelled. |
| 111 | Neha | ₹5,000.01 | MEDIUM | SETTLED | ₹0.00 | Exact MEDIUM lower boundary. |
| 112 | Aditya | ₹25,000.01 | HIGH | SETTLED | ₹0.00 | Exact HIGH lower boundary. |
| 113 | Pooja | ₹5,000.00 | LOW | SETTLED | ₹0.00 | Exact LOW upper boundary. |
| 114 | Sanjay | ₹25,000.00 | MEDIUM | SETTLED | ₹0.00 | Exact MEDIUM upper boundary. |
| 115 | Divya | ₹100,000.00 | HIGH | SETTLED | ₹0.00 | Exact HIGH upper boundary. |
| 116 | Manish | ₹100,000.01 | VERY_HIGH | VERIFICATION_REQUIRED | ₹100,000.01 | Exact VERY_HIGH lower boundary and clean OTP target. |
| 117 | Aisha | ₹4,000.00 | not assessed | CREATED | ₹0.00 | Earliest state-machine starting point. |
| 118 | Nikhil | ₹8,000.00 | not assessed | AUTHORIZED | ₹0.00 | Authorized but not risk-assessed state. |
| 119 | Meera | ₹1,000.00 | LOW | FAILED | ₹0.00 | Insufficient available balance, with resolved exception. |
| 120 | Varun | ₹3,000.00 | not assessed | CANCELLED | ₹0.00 | Cancellation before risk assessment. |
| 121 | Ritu | ₹15,000.00 | MEDIUM | CANCELLED | ₹0.00 | Medium cancellation history. |
| 122 | Harish | ₹75,000.00 | HIGH | CANCELLED | ₹0.00 | High cancellation history. |
| 123 | Tanvi | ₹4,500.00 | LOW | RELEASED | ₹4,500.00 | Eligible for processor settlement when explicitly enabled. |
| 124 | Suresh | ₹60,000.00 | HIGH | RELEASED | ₹60,000.00 | Retry exhaustion retained for `MANUAL_REVIEW`; must not auto-retry. |
| 128 | Mohit | ₹1.00 | LOW | SETTLED | ₹0.00 | Minimum permitted payment amount. |

References are stored as `SPV1-SHOWCASE-<number>`. Customers 125–127, 129 and 130 remain useful clean/edge fixtures without a seeded payment.

## 13. OTP and Risk Review evidence

### Terminal OTP histories

| Ref | Status | Attempts | Meaning |
|---|---|---:|---|
| 101 | VERIFIED | 1 | Verification succeeded before pending review. |
| 106 | VERIFIED | 1 | Verification succeeded before the later cancelled review. |
| 107 | LOCKED | 3 | Approved maximum failed attempts exhausted. |
| 108 | VERIFIED | 1 | Verification succeeded before officer rejection. |
| 109 | VERIFIED | 1 | Verification succeeded before approval and settlement. |
| 110 | CANCELLED | 0 | Old challenge invalidated by re-verification request. |

Each hash uses the prototype `SHA-256$<base64-salt>$<base64-digest>` representation. No seeded code can be recovered from the script, and no terminal challenge is reusable.

Transactions 105 and 116 deliberately have no OTP row and are the clean end-to-end issue targets.

### Risk Review histories

| Ref | Round | Status | Decision role/state meaning |
|---|---:|---|---|
| 101 | 1 | PENDING | Assigned to Rhea; ready for one officer decision. |
| 106 | 1 | CANCELLED | Customer cancellation closed the open review path. |
| 108 | 1 | REJECTED | Officer rejected after independent confirmation concerns. |
| 109 | 1 | APPROVED | Officer approved; transaction later settled. |
| 110 | 1 | REVERIFICATION_REQUESTED | Fresh customer OTP is required before a new review round. |

## 14. Ledger, balances and settlement evidence

### Opening-balance design

For every positive customer opening balance, V12 inserts:

```text
LEDGER_POSTING type OPENING_BALANCE, initially PENDING
  line 1: DEBIT  SAFEPAY_OPENING_BALANCE_CONTROL
  line 2: CREDIT customer account
then finalize posting to POSTED
```

Nandini's zero-balance account has no zero-value posting because V6 requires positive amounts. There are 29 opening postings and 58 opening entries.

### Settlement design

For every seeded `SETTLED` payment, V12 inserts:

```text
LEDGER_POSTING type PAYMENT_SETTLEMENT, initially PENDING
  line 1: DEBIT  source customer account
  line 2: CREDIT SAFEPAY_OUTBOUND_CLEARING
then finalize posting to POSTED
```

Nine settlement postings cover refs 102, 104, 109, 111, 112, 113, 114, 115 and 128. Their total is ₹362,501.02, exactly matching the outbound-clearing account's current balance and credit entries.

### Financial totals contributed by V12

| Measure | Expected |
|---|---:|
| Opening postings | 29 |
| Settlement postings | 9 |
| Total postings | 38 |
| Ledger entries | 76 |
| Settled amount / clearing credits | ₹362,501.02 |
| Live reservations | ₹544,500.01 |

The live reservation total is refs 101 + 105 + 110 + 116 + 123 + 124. A reserved amount is not yet a ledger debit and not yet settlement.

## 15. Exceptions, audit and notifications

### Exceptions

| Ref | Status | Retryable | Retries | Meaning |
|---|---|---|---:|---|
| 119 | RESOLVED | N | 0 | Definitive insufficient-available-balance failure. |
| 124 | MANUAL_REVIEW | Y | 3 | Automatic settlement attempts exhausted with no partial posting. |

The V1 retry delays remain 5 seconds, 30 seconds and 1 minute; after exhaustion, the row is `MANUAL_REVIEW`.

### Audit evidence

Twelve immutable rows cover OTP verification/denial, review pending/approved/rejected/re-verification, cancellation, release, failure, settlement and manual-review escalation. Each has a deterministic V12 event reference, a correlation ID and canonical action/outcome vocabulary.

### Notification evidence

Ten rows cover:

- one `PENDING` notification for the dispatcher;
- delivered/read settlement and cancellation examples;
- delivered/unread OTP, review and failure examples;
- one terminal `FAILED` publication example for ref 123.

Notifications remain durable even if real-time STOMP delivery fails. REST is authoritative.

## 16. OTP email routing safety

The stored identity email must remain the realistic customer email used for login and presentation. The separately approved development behavior is to redirect every actual OTP email to:

`aditya.rrr30@gmail.com`

That redirect occurs only inside the backend's server-controlled OTP recipient-routing and email-delivery boundary, never by replacing `APP_USER.email` and never from a client-supplied request field.

The fixed-recipient override is now implemented. It is activated only through the controlled showcase environment contract:

```text
SAFEPAY_OTP_EMAIL_ENABLED=true
SAFEPAY_OTP_EMAIL_ROUTING_MODE=FIXED_OVERRIDE
SAFEPAY_OTP_EMAIL_RECIPIENT_OVERRIDE=aditya.rrr30@gmail.com
```

`FIXED_OVERRIDE` fails application startup if the override is absent or invalid. `STORED_USER` remains an explicit future mode for genuine per-customer delivery; it is not the approved showcase mode. Logs and responses continue to show only a safe masked destination and never expose the raw OTP or Gmail App Password.

## 17. V1–V12 constraint and trigger traceability

| Migration | Contract used by V12 | Seed evidence |
|---|---|---|
| V1 | `APP_USER`, canonical role vocabulary, lowercase/unique email, E.164 mobile, BCrypt-compatible hash, user status/version fields, many-to-many `USER_ROLE`, `AUTH_SESSION` | 33 active users; unique lowercase emails/mobiles/hashes; one role each. Sessions deliberately absent. |
| V2 | Owned customer accounts, ownerless system-account types, INR/balance/reservation checks, IFSC shape, beneficiary method-specific nullability and ownership uniqueness | 30 owned accounts, two ownerless system accounts, 30 mixed BANK_ACCOUNT/UPI beneficiaries, one disabled beneficiary. |
| V3 | Risk policy, four bands, protection policy | V12 preflight requires the one active four-band `AMOUNT_ONLY_V1` policy. |
| V4 | Payment state columns, minimum ₹1, INR, ownership relationships, immutable amount risk factors, idempotency records | 25 coherent payment histories and 22 amount factors; no manufactured idempotency row. |
| V5 | OTP purpose/channel/status/lifecycle, one pending challenge, Maker-Checker foundation and terminal locks | Six terminal OTP histories; no pending OTP; review rows use the hardened canonical model. |
| V6 | PENDING-to-POSTED posting lifecycle, exactly two balanced lines, immutable ledger entries, exception vocabulary | 38 balanced postings, 76 immutable entries, two exception paths. |
| V7 | Immutable audit evidence, protected notification content and delivery/read lifecycle | 12 audit rows and 10 notification rows; no trigger is disabled. |
| V8 | Dashboard, risk, approval, ledger and reservation views | The seed supplies representative rows to all five views. |
| V9 | Canonical `RISK_REVIEW` vocabulary and terminal guard | Pending, cancelled, rejected, approved and re-verification examples. |
| V10 | Ownership, state/risk/reservation, OTP/review and lifecycle hardening | V12 constructs every tuple to satisfy the reconciled invariants. |
| V11 | Exact four roles and active canonical risk/protection reference data | Preflight resolves rather than duplicates V11 rows. |
| V12 | One coherent showcase dataset plus local acceptance checks | The current guide's subject. |

### Trigger-safe write order

- Ledger postings are inserted as `PENDING`; entries are then inserted; only then is the posting finalized to `POSTED`.
- No update/delete is attempted against immutable ledger entries or audit logs.
- OTP and Risk Review rows are inserted directly in terminal historical shape; later mutation of terminal rows remains prohibited.
- Notification content is created once; only permitted delivery/read lifecycle fields differ by scenario.
- No constraint or trigger is disabled for seeding.

## 18. Deliberately unseeded runtime data

| Omitted data | Reason |
|---|---|
| `AUTH_SESSION` | Refresh-token hashes, expiry and device state must be produced by a real login. |
| `IDEMPOTENCY_RECORD` | Must represent an actual HTTP mutation and request fingerprint, not a fabricated replay. |
| Pending OTP challenge | Would imply a reusable verification secret that is not available to the tester. |
| Active `PROTECTED` timer | The scheduler could immediately release it based on database time, making the seed nondeterministic. |
| User safety settings | No approved table exists in V1–V11; this guide does not invent one. |
| Address and PAN | Explicitly deferred; PAN must not be persisted if simulated KYC is later approved. |
| Real bank branch identity | The schema stores `bank_name` and IFSC, not a separate branch column. |
| Real external rail submission | SafePay stops at simulated outbound settlement and makes no NPCI/RBI reversal claim. |

## 19. Scenario selection for later API guides

This section identifies reusable fixtures; it is not the phase-wise API test guide.

| Test objective | Recommended fixture |
|---|---|
| Standard customer login and owned data | Priya (101) |
| LOW/MEDIUM/HIGH/VERY_HIGH exact boundaries | 113/111/112/115/116 |
| Minimum ₹1 payment | Mohit (128 history) or another active funded user for a fresh call |
| Zero available balance | Nandini (125) |
| Inactive source account | Akash (126) |
| Disabled beneficiary rejection | Shruti (129) |
| Insufficient balance failure | Meera (119 history) |
| Customer cancellation / Undo | Kavya, Ritu or Harish history |
| Fresh OTP issue | Ananya 105 or Manish 116, only after safe email override exists |
| OTP attempt exhaustion | Sneha 107 history |
| Pending Risk Officer decision | Priya 101 with Rhea officer |
| Approved/rejected/re-verification review | Ishita 109 / Rahul 108 / Karthik 110 |
| Released settlement candidate | Tanvi 123 |
| Settlement manual review | Suresh 124 |
| Customer ownership isolation | Use any two distinct customer JWTs, such as Priya and Arjun |
| Admin role separation | Rhea vs Vikram vs Anjali |
| Audit read access | Anjali; customer sees only owned safe timeline |
| Notification delivery/read states | Priya pending, Arjun delivered/read, Tanvi failed |

For mutation tests, prefer creating new rows through the API. Do not repurpose immutable historical rows by direct SQL updates.

## 20. V12 migration-local acceptance checks

V12 raises Oracle application errors and aborts on mismatch:

| Error | Guard |
|---|---|
| `-20300` | Four canonical roles are required. |
| `-20301` | One active four-band `AMOUNT_ONLY_V1` policy is required. |
| `-20302` | No seed marker may already exist. |
| `-20303` | Each seeded user's canonical role must resolve exactly once. |
| `-20304` | Account current/reserved arithmetic must be valid. |
| `-20310` | Exactly 30 customer personas were inserted. |
| `-20311` | Exactly three staff personas were inserted. |
| `-20312` | Exactly two active ownerless INR system accounts exist. |
| `-20313` | Exactly 25 showcase transactions exist. |
| `-20314` | Every seeded posting has two balanced debit/credit lines. |
| `-20315` | Every customer account current balance reconciles to ledger net. |
| `-20316` | Every account reservation reconciles to live reserved transactions. |
| `-20317` | No pending seeded OTP exists and all hashes use the approved representation. |
| `-20318` | Clearing current balance equals its settlement credit entries. |

Because Flyway runs the migration transactionally where Oracle semantics permit, any raised error prevents a successful version-12 history record. Investigate the first Oracle error; do not bypass the check.

## 21. Read-only post-migration verification

Run these after the application has applied V12. They do not mutate data.

### Flyway proof

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM "flyway_schema_history"
WHERE version = '12';
```

Expected: one successful row for `V12__safepay_v1_showcase_seed.sql`.

### Identity and role counts

```sql
SELECT r.role_code, COUNT(*) AS assigned_users
FROM APP_USER u
JOIN USER_ROLE ur ON ur.user_id = u.user_id
JOIN APP_ROLE r ON r.role_id = ur.role_id
WHERE u.mobile_number BETWEEN '+919000000101' AND '+919000000133'
GROUP BY r.role_code
ORDER BY r.role_code;
```

Expected: `CUSTOMER=30`, and one each for `RISK_OFFICER`, `SYSTEM_ADMIN`, `AUDITOR`.

### Seed contribution counts

```sql
SELECT 'CUSTOMER_ACCOUNTS' item, COUNT(*) value
FROM ACCOUNT a
JOIN APP_USER u ON u.user_id = a.owner_user_id
WHERE u.mobile_number BETWEEN '+919000000101' AND '+919000000130'
UNION ALL
SELECT 'BENEFICIARIES', COUNT(*)
FROM BENEFICIARY b
JOIN APP_USER u ON u.user_id = b.owner_user_id
WHERE u.mobile_number BETWEEN '+919000000101' AND '+919000000130'
UNION ALL
SELECT 'TRANSACTIONS', COUNT(*) FROM PAYMENT_TRANSACTION
WHERE transaction_reference LIKE 'SPV1-SHOWCASE-%'
UNION ALL
SELECT 'RISK_FACTORS', COUNT(*) FROM TRANSACTION_RISK_FACTOR rf
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = rf.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
UNION ALL
SELECT 'OTP_HISTORY', COUNT(*) FROM PAYMENT_OTP_CHALLENGE o
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = o.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
UNION ALL
SELECT 'RISK_REVIEWS', COUNT(*) FROM RISK_REVIEW r
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = r.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
UNION ALL
SELECT 'EXCEPTIONS', COUNT(*) FROM TRANSACTION_EXCEPTION e
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = e.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
UNION ALL
SELECT 'AUDIT_ROWS', COUNT(*) FROM AUDIT_LOG
WHERE event_reference LIKE 'SPV1-AUD-%'
UNION ALL
SELECT 'NOTIFICATIONS', COUNT(*) FROM APP_NOTIFICATION
WHERE notification_reference LIKE 'SPV1-NOT-%';
```

Expected values: accounts 30, beneficiaries 30, transactions 25, factors 22, OTP 6, reviews 5, exceptions 2, audit 12, notifications 10.

### Transaction state distribution

```sql
SELECT state, risk_tier, COUNT(*) AS row_count, SUM(amount) AS total_amount
FROM PAYMENT_TRANSACTION
WHERE transaction_reference LIKE 'SPV1-SHOWCASE-%'
GROUP BY state, risk_tier
ORDER BY state, risk_tier;
```

### Account-to-ledger reconciliation

```sql
SELECT a.account_number,
       a.current_balance,
       NVL(SUM(CASE e.entry_type
                 WHEN 'CREDIT' THEN e.amount
                 WHEN 'DEBIT' THEN -e.amount
               END), 0) AS ledger_net
FROM ACCOUNT a
JOIN APP_USER u ON u.user_id = a.owner_user_id
LEFT JOIN LEDGER_ENTRY e ON e.account_id = a.account_id
WHERE u.mobile_number BETWEEN '+919000000101' AND '+919000000130'
GROUP BY a.account_number, a.current_balance
HAVING a.current_balance <>
       NVL(SUM(CASE e.entry_type
                 WHEN 'CREDIT' THEN e.amount
                 WHEN 'DEBIT' THEN -e.amount
               END), 0);
```

Expected: no rows.

### Reservation reconciliation

```sql
SELECT a.account_number,
       a.reserved_amount,
       NVL(SUM(CASE WHEN t.state IN
           ('PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','RELEASED')
           THEN t.reserved_amount ELSE 0 END), 0) AS transaction_reservations
FROM ACCOUNT a
JOIN APP_USER u ON u.user_id = a.owner_user_id
LEFT JOIN PAYMENT_TRANSACTION t ON t.source_account_id = a.account_id
WHERE u.mobile_number BETWEEN '+919000000101' AND '+919000000130'
GROUP BY a.account_number, a.reserved_amount
HAVING a.reserved_amount <>
       NVL(SUM(CASE WHEN t.state IN
           ('PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','RELEASED')
           THEN t.reserved_amount ELSE 0 END), 0);
```

Expected: no rows.

### Posting balance and clearing total

```sql
SELECT p.posting_reference,
       p.amount,
       COUNT(e.ledger_entry_id) AS entry_count,
       SUM(CASE WHEN e.entry_type = 'DEBIT' THEN e.amount ELSE 0 END) AS debit_total,
       SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE 0 END) AS credit_total
FROM LEDGER_POSTING p
JOIN LEDGER_ENTRY e ON e.posting_id = p.posting_id
WHERE p.posting_reference LIKE 'SPV1-%'
GROUP BY p.posting_reference, p.amount
HAVING COUNT(e.ledger_entry_id) <> 2
    OR SUM(CASE WHEN e.entry_type = 'DEBIT' THEN e.amount ELSE 0 END) <> p.amount
    OR SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE 0 END) <> p.amount;

SELECT a.account_number, a.current_balance,
       SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE 0 END) AS credited
FROM ACCOUNT a
LEFT JOIN LEDGER_ENTRY e ON e.account_id = a.account_id
WHERE a.account_number = 'SAFEPAY_OUTBOUND_CLEARING'
GROUP BY a.account_number, a.current_balance;
```

Expected: first query returns no rows; second reports ₹362,501.02 for both current balance and credits.

### OTP and review safety

```sql
SELECT t.transaction_reference, o.status, o.attempt_count,
       CASE WHEN o.otp_hash LIKE 'SHA-256$%$%' THEN 'SAFE_SHAPE' ELSE 'CHECK' END hash_shape
FROM PAYMENT_OTP_CHALLENGE o
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = o.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
ORDER BY t.transaction_reference;

SELECT t.transaction_reference, r.review_round, r.status
FROM RISK_REVIEW r
JOIN PAYMENT_TRANSACTION t ON t.transaction_id = r.transaction_id
WHERE t.transaction_reference LIKE 'SPV1-SHOWCASE-%'
ORDER BY t.transaction_reference, r.review_round;
```

Expected: six terminal OTPs, no `PENDING`, safe hash shape for all, and five review rows.

### Analytical-view smoke checks

```sql
SELECT * FROM VW_TRANSACTION_DASHBOARD FETCH FIRST 20 ROWS ONLY;
SELECT * FROM VW_RISK_SUMMARY FETCH FIRST 20 ROWS ONLY;
SELECT * FROM VW_PENDING_APPROVALS FETCH FIRST 20 ROWS ONLY;
SELECT * FROM VW_LEDGER_RECONCILIATION FETCH FIRST 20 ROWS ONLY;
SELECT * FROM VW_RESERVATION_RECONCILIATION FETCH FIRST 20 ROWS ONLY;
```

Use the view column definitions from V8/V9 when interpreting results. Do not treat an analytical view as a mutation surface.

## 22. Repeatability, duplicate prevention and reset policy

### Normal repeatability

- On later application starts, Flyway sees version 12 as applied and does not execute it again.
- V12's marker preflight prevents an accidental first-time run over matching manually inserted showcase data.
- Unique emails, mobiles, account numbers, transaction references, posting references, idempotency identities, audit references and notification deduplication keys provide independent duplicate barriers.

### Never do these

- Do not copy individual V12 blocks into a populated schema.
- Do not delete the version-12 Flyway row to force a rerun.
- Do not edit V12 after it has been applied.
- Do not disable immutable triggers to “clean” rows.
- Do not use `DELETE`/`UPDATE` against ledger entries, posted postings, audit logs, terminal OTPs or terminal reviews.
- Do not enable Flyway clean in a valuable schema.

### Safe reset

Because the dataset intentionally contains immutable financial and audit histories, there is no supported piecemeal cleanup script. For a disposable local environment, the safe reset is an owner-controlled schema rebuild from V1 through the latest approved migration, after confirming the exact schema target and preserving anything required.

`spring.flyway.clean-disabled=true` is intentional. Do not turn it off merely to rerun the showcase.

If a reset is later required, it must use a separately reviewed runbook with the exact local schema name and credentials. This guide does not authorize dropping any schema.

## 23. Known limitations and presentation cautions

- The dataset is realistic-looking but synthetic; emails and UPI handles are not guaranteed unowned.
- SMTP must be enabled only with `FIXED_OVERRIDE` and the approved recipient during showcase testing; `STORED_USER` would contact the stored presentation identities.
- IFSC and account-number shape do not prove a real branch or account.
- The risk engine is the approved deterministic amount-only V1 policy, not a production fraud model.
- Salted SHA-256 OTP storage is the explicitly approved prototype choice, not the recommended production secret design.
- The in-process WebSocket broker and simulated outbound clearing are prototype boundaries.
- There is no active protection timer at startup; create one through the API when testing countdown behavior.
- Seeded timestamps are relative to the migration's `SYSTIMESTAMP`, so exact clock values differ per installation.
- The seed is not a load/stress dataset. Thirty customers are for coherent functional and edge-case coverage.

## 24. Lock-in checklist

Before declaring the seed baseline operationally locked:

- [ ] V1–V11 show successful Flyway history.
- [ ] V12 shows one successful Flyway history row.
- [ ] Application starts with JPA schema validation successful.
- [ ] Identity, account, beneficiary and transaction counts match this guide.
- [ ] Account-to-ledger and reservation mismatch queries return no rows.
- [ ] All seeded postings are exactly balanced.
- [ ] Clearing total is ₹362,501.02.
- [ ] No seeded OTP is `PENDING` and no raw OTP exists.
- [ ] SMTP remains disabled until the override is implemented.
- [ ] Settlement processor remains disabled until the generated clearing ID is configured deliberately.
- [ ] Staff role isolation is verified with Rhea, Vikram and Anjali.
- [ ] No direct mutation or trigger bypass was used.
- [ ] Any later seed/schema correction is planned as V13+ rather than an edit to applied migrations.

## 25. Handoff to the next deliverables

After the user runs and verifies V12, the next contract deliverables are:

1. the step-by-step backend launch/server guide;
2. the phase-by-phase full-suite implementation understanding guide;
3. separate pure API test guides for each backend phase.

Those guides must use generated database IDs discovered at runtime, JWT/RBAC from the start, realistic request bodies and positive, invalid, ownership, constraint, false-negative, concurrency and failure cases. They must not mutate this seed directly or silently expand the approved scope.
