# Live browser acceptance

Real frontend: http://localhost:8000. Real backend: http://localhost:8080. This is the designated demo schema, not the port-8001 fixture preview. No real banking rail is connected.

Baseline: baseline-before-v2.txt, captured 2026-09-21 02:11:50 +05:30, validated across all 19 tables before sign-in.

| Actor | Check | Result / resource |
|---|---|---|
| 2468 | Email/password browser sign-in | PASS; dashboard shows Priya Nair and the observed account funds |
| 2468 | Real dashboard and session restoration on navigation | PASS; current 350000.00, existing reserved 180000.00, available 170000.00 |
| 2468 | Recipient and payment-sheet review | PASS; existing beneficiary 1158; exact amount 1.00; reference SP-UI-20260921-LOW |
| 2468 | Save instruction | PASS; transaction 1558 CREATED; no authorization combined with create |
| 2468 | Explicit authorization and authoritative outcome | PASS; transaction 1558 RELEASED / LOW, reserved 1.00; settlement is pending because the processor is disabled |
| 2468 | Transaction detail, risk explanation and timeline | PASS; PAYMENT_CREATED and PAYMENT_RELEASED evidence; no authorize/cancel action after release |
| 2468 | Notification mark-read and related payment link | PASS; new payment notification marked read and View payment opened transaction 1558 |
| 2468 | Profile and current funds | PASS; current 350000.00, reserved 180001.00, available 169999.00 |
| 2468 | UPI beneficiary create, disable, reactivate, disable | PASS; new beneficiary 1751 SafePay Integration Demo ends DISABLED; existing beneficiary 1158 left active |
| 2468 | Logout | PASS; login screen returned |

Passwords, cookies, bearer tokens and OTP codes are omitted. A browser automation input timeout was recovered using the supported accessibility input API before submitting any instruction; it did not cause a duplicate request.
## Staff and final verification

| Actor | Check | Result |
|---|---|---|
| 2499 | Login and overview | PASS; stats and state counts from real backend |
| 2499 | Users, default All filters and email search | PASS after shared absent-filter fix; Priya returned as the single matching row |
| 2499 | User details | PASS; user 2468, CUSTOMER, ACTIVE; access controls present, no access mutation submitted |
| 2499 | Account list/detail/funds | PASS; account 1626 agrees with customer funds: 350000.00 / 180001.00 / 169999.00 |
| 2499 | Operational failures | PASS; three pre-existing failure records rendered without changing them |
| 2498 | Login, queue and realtime | PASS; Live updates connected; existing review 98 / payment 1051 shown |
| 2498 | Review evidence and payment timeline | PASS; pending round 1, null legacy category renders Not specified; required-reason controls gated; no decision/note submitted |
| 2500 | Login and all eight audit tabs | PASS; audit trail, payments, reviews, ledger, ledger checks, reservation checks, exceptions and policies finished loading without visible alerts |
| 2500 | Browser payment evidence | PASS; payment 1558 RELEASED, amount/reservation 1.00, LOW band evidence and two payment events; no mutation controls |
| Staff | Logout and final browser logs | PASS; each session logged out; final warning/error log collection empty |
| Final | Serving rebuilt application | PASS; final frontend build successful; browser left at /login, signed out |

Live Admin initially exposed the All-filter HTTP 400. The fix was built and all staff pages above were checked again. Rapid tab navigation was also exercised; its intermediate loading states were not counted as passes. Final checks waited for loading to end and inspected actual records.

The last two beneficiary display refinements (disabled Pay visibility and UPI IFSC fallback) passed real-template/TypeScript build checks; the live beneficiary CRUD/status flow itself was exercised before that final display rebuild. Desktop/mobile visual sampling against the supplied recording was performed in the isolated preview, as recorded in TEST_RESULTS.md.
