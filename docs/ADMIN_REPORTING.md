# Admin reporting backend

Read-only, current database snapshot reports; no transaction processing changes.
Review and manually install `Database/12_admin_reporting_views.sql` before using
the endpoints. It creates two read-only aggregate views over TRANSACTION_DB and
does not alter rows, tables or transaction constraints. It works with either
legacy or migrated risk/state values. Existing 05 views target the older schema
and remain untouched. No SQL was executed as part of implementation.

## Endpoints

- GET /api/admin/reports/transactions/summary — all-time totals.
- GET /api/admin/reports/transactions/daily?from=2026-09-01&to=2026-09-13
  — inclusive creation-date range, ascending order, maximum 366 days.

Use the existing ADMIN login session cookie. CUSTOMER receives 403; anonymous
receives 401. No caller ID or role query parameter grants access. GETs have no
request body and require no CSRF header. No reporting write endpoints exist.

Summary fields: totalTransactions, settledTransactions, protectedTransactions,
cancelledTransactions, rejectedTransactions, hardHolds, highRiskTransactions,
totalAmount, settledAmount. Daily rows contain date and the same summary object.
Counts are integers; amounts use BigDecimal and represent simulated INR volumes,
not account balances or revenue. Total amount includes all transaction states.

Status counts use the current STATE. HIGH and VERY_HIGH tiers count as high risk;
legacy HARD_HOLD risk-tier values are included for compatibility without relabeling
rows. High-risk counts overlap status counts. Status subtotals need not equal total:
CREATED, AUTHORIZED, RELEASED and legacy RISK_ASSESSED also contribute to total.

Daily rows group by stored CREATED_AT calendar date (Oracle TIMESTAMP has no
timezone). They are not historical counts of settlement/cancellation events on
that date; later state changes update the reported counts for the creation date.
Days without transactions are omitted. Empty summary returns zeros; empty daily
results return []. Missing views/database failures return a generic 503, not zeros
or internal Oracle error details. Invalid/missing/reversed/oversized dates return 400.

## Manual verification after reviewed view installation

1. Log in as an existing ADMIN in Postman and retain its session cookie.
2. GET the summary endpoint: expect 200 and the nine aggregate fields above.
3. GET the daily endpoint with a valid date range: expect 200 and dated DTO rows.
4. Repeat with a CUSTOMER session: expect 403 for both endpoints.
5. Repeat without cookies: expect 401. Invalid date ranges as ADMIN should give 400.
6. Compare with SELECT * FROM vw_sp_tx_report_totals and the date-filtered
   vw_sp_tx_report_daily view manually. No test data needs to be changed.

Automated tests use MockMvc/mocked repository and ResultSet; they do not verify
Oracle view execution. No Oracle-backed tests are run for this task.
