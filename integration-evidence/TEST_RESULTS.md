# SafePay-New integration verification

Checkpoint: 21 September 2026. **54 offline tests passed; final build passed; 93/93 logged live API checks passed. Live browser acceptance passed for the exercised flows. Explicit user deferrals remain.**

## Executed checks

| Check | Result | Evidence / limits |
|---|---|---|
| Backend health | UP | Final public GET http://localhost:8080/actuator/health returned UP; frontend localhost:8000 returned HTTP 200 |
| Immutable backend baseline | PASS | 502 captured files compared by SHA-256; zero changed or missing |
| TypeScript/build | PASS | npm run build completed successfully; includes TypeScript compilation |
| Payment recovery tests | 12/12 PASS | Identity/expiry/storage isolation, exact payload/key, secret exclusion |
| Installed foundation tests | 3/3 PASS | Pinned JET/TypeScript/font dependencies, lossless JSON and local STOMP bundle |
| API contract tests | 12/12 PASS | IDs/money, category boundary, methods/payloads, one 401 retry, 403/no retry and uncertain network outcomes |
| UI/service integration fixtures | 23/23 PASS | Actual view models/templates with JET CSP evaluation; all role screens and key mutation/recovery branches |
| Snapshot comparison tests | 4/4 PASS | Exact numbers, complete counts/markers, primary keys, Unicode/whitespace chunks and missing-chunk rejection |
| Browser verification | PASS for exercised fixture and live flows | Desktop/mobile fixture coverage plus real customer/Admin/Risk/Auditor browser checks; LIVE_BROWSER_TESTS.md |
| First user-run SQL export | REJECTED as baseline | End marker and 19 counts exist, but text wrapped inside PAYMENT_TRANSACTION JSON values |
| Revised V2 SQL on Oracle | PASS | Before 02:11:50 and after 02:37:17 +05:30; both snapshots validated all 19 table counts and exact chunks |
| Read-only snapshot runner | PASS | Existing JDK/JDBC driver; two owner sessions; read-only transactions; rollback/close; secrets passed in memory |
| Live API checks | 93/93 PASS | All five identities; refresh/logout, all role reads, ownership/role denial, payment validation/replay/conflict/protected cancellation/insufficient funds |
| Live browser checks | PASS within scope | Customer payment 1558 RELEASED; beneficiary 1751 ends DISABLED; notification read/navigation; all staff read screens and STOMP connected |
| Snapshot reconciliation | PASS | 1474 included-field differences / 107 rows; 100 inserted, 7 updated, zero deleted; all current balances unchanged; all test sessions revoked |
| OTP, live review decisions and new settlement | DEFERRED | User will test OTP; schedulers intentionally disabled; no OTP/review/ledger rows changed |
| Registration and admin access mutation acceptance | DEFERRED | Disposable registration identity and approved access-change target/restore plan still required |

The complete npm test run ended with zero failed, cancelled or skipped tests across the five suites. After the final shared staff-filter fix, all 23 affected UI/service integration tests were rerun successfully and the build passed again. Older copied test files are not included in this result; see frontend/SafePayJet/tests/README.md. Historical backend suite counts were not rerun and are not current integration evidence.

## Browser coverage and findings

- Supplied 220.97-second recording inspected locally at multiple sampled frames, including the existing payment sheet; its visual language guided adaptation.
- Customer send flow exercised through fixture instruction creation and confirmation. Creation remains separate from authorization.
- Payment sheet/confirmation focus and dismissal checked using native dialogs.
- Profile shows exact current/reserved/available values and bank details.
- Beneficiary bank/UPI field switching works; mobile heading overflow and selector styling were corrected and visually rechecked.
- Transactions list/detail and risk/audit evidence render after correcting forbidden ambient Math references in templates. Pagination and countdown calculations now live in the view model.
- Dashboard recent-payment keyboard navigation was corrected: JET CSP does not accept an inline conditional function body in the binding. Enter now opens the intended payment.
- Admin user detail and confirmation cancellation checked with fixtures. Customer-only header menus are absent for staff.
- Risk Officer queue/detail and Auditor ledger navigation checked with fixtures. Automated template coverage includes every configured staff tab.
- Mobile transaction document width was 375px within a 390px viewport, with no visible alert; corrected beneficiary heading content fits its card.

An initial plain-Knockout fixture run passed 53 tests but missed two JET-specific expression failures. The harness now loads the installed Oracle JET expression evaluator and mirrors its binding evaluation order. Its stronger run detected the dashboard handler failure, which was fixed before the final 54-test pass. The intermediate failures are superseded by the successful final run, not omitted.

## Other resolved findings

- Account-balance races cannot overwrite a newer selection.
- Optional category rendering handles absent values.
- Route startup guards an initial missing router state.
- Saved payment and officer decisions preserve original targets/keys after unknown outcomes; no new-key blind retry.
- OTP codes and officer internal-note text are excluded from persistent recovery storage.
- Spring Boot owns LiveReload port 35729; frontend LiveReload is disabled.
- The JET file watcher failed when a separate build replaced staging files; serving now uses watchers disabled, followed by explicit rebuild/browser refresh.
- Snapshot comparison uses RISK_REVIEW.APPROVAL_ID and USER_ROLE's composite key.
- Browser fixture audit data was added to the isolated preview bundle; it is not an application/backend change.

## Final live findings and fixes

- The staff "All" options use an undefined bound value. The browser exposed an HTTP 400 because it was serialized as the text "undefined". Shared filter normalization now omits absent values; a regression assertion checks every configured staff tab request. Real Admin and Auditor lists then loaded without alerts.
- Disabled beneficiary cards now hide their Pay link; the real-template test checks active and disabled cases. UPI detail shows IFSC as Not applicable.
- An initial new disabled-beneficiary assertion forgot to enable the Show disabled filter and failed on an absent card. The fixture was corrected to match the actual screen state, then the full 54-test run passed.
- Payment 1554 original-key create and cancel replays returned the same transaction; changed payload/same key returned 409. No duplicate payment or debit was observed.
- MEDIUM 5000.01 and HIGH 25000.01 payments entered PROTECTED and cancelled successfully. Their source account ended with exactly its initial funds.
- High-value category and decimal validation rejected invalid inputs. Insufficient available funds produced FAILED with no reservation.
- Browser test 1558 is RELEASED with 1.00 reserved, not SETTLED. No settlement outcome or new balanced journal is claimed while the processor is disabled.
- Risk Officer browser showed Live updates connected. Actual review/OTP event delivery and scheduler-driven updates remain pending. Notifications REST read/mark-read/payment navigation passed.
- Final browser console inspection returned no warning/error entries. Expected anonymous restore failures around sign-out/startup appear in audit evidence and did not prevent sign-in.
- No rows were deleted; no pre-existing payment, beneficiary, review, OTP, role, policy, exception or ledger rows changed in included snapshot fields.

## Build warnings

JET reports that Sass compilation is skipped because Sass is not installed; this frontend uses CSS. Node reports an fs.existsSync deprecation from the build tooling. The build exits successfully. No unrelated package was installed to suppress either warning.

## Remaining user acceptance

See PENDING_USER_TESTS.txt (plain text checklist) and PENDING_USER_TESTS.md (reasons and acceptance details). These cover real email OTP, OTP-to-review and officer commands, protection expiry and settlement/backlog effects, disposable registration, admin access mutations, plus genuine expiry/multi-tab/reconnect recovery in a controlled user run.

The API ledger covers HTTP outcomes and has no failed rows. The browser ledger and snapshot summary report observed state separately; passing a fixture is not live acceptance. Historical backend suite counts were not rerun. No claim of flawless behavior is made beyond the executed checks.

