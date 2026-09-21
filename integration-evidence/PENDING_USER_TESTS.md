# Pending user-run tests

User decision, 21 September 2026: leave OTP completion and scheduler-dependent acceptance pending; both protection and settlement schedulers are intentionally disabled. Use agreed defaults for routine frontend work and do not interrupt the user for further test choices.

These items are not reported as passed. No backend code, environment values, database objects or grants were changed to force these outcomes.

| Pending check | Why it remains | What to verify later |
|---|---|---|
| OTP email issue/resend/verification | User will test with the real inbox | Correct recipient routing; valid code; one controlled wrong code; resend cooldown, expiry and limits; reload/recovery; no code persistence |
| OTP to Risk Officer review | Requires successful OTP | Payment enters PENDING_RISK_REVIEW; exact review ID/round and transaction evidence appear |
| Risk Officer live approve/reject/reverification/note | No new verified review was created; existing seeded reviews were deliberately left untouched | Use a designated new demo review; validate required reason versus internal note, stale decisions, next round and reservation effects; never approve a large settlement without agreeing its data impact |
| Protection expiry | Protection scheduler intentionally disabled | With approved scheduler settings, a new protected payment follows the backend deadline; browser never releases it itself |
| Released-to-settled flow and new ledger posting | Settlement processor intentionally disabled | Test transaction 1558 (reference SP-F4765F0E10C54E2AAFD4E812D1263256, customer reference SP-UI-20260921-LOW) is a 1.00 INR browser test. It is RELEASED and reserved; verify SETTLED, balanced ledger lines and exact funds after an approved scheduler run |
| Scheduler backlog impact | Enabling schedulers may also advance pre-existing records | Review the original baseline and pending records first, including existing customer-2468 protected/review reservations. Do not treat all resulting differences as frontend-test changes |
| Live registration success | Restricted live identities were the five designated users | Use an approved disposable identity. Registration returns a customer ID and does not provision an account |
| Live admin status/role changes and session revocation | Access-changing mutations were not needed to verify read screens; offline controls/confirmation tests passed | Agree the target and restore plan first; verify permission/last-admin safeguards and session invalidation without locking out required users |
| Actual expiry, multiple browser tabs and reconnect recovery | Controlled expiry/race/unknown-response injection passed offline; basic refresh/logout and original-key replay passed live | In a designated user run, check cross-tab logout/identity changes, original-key recovery after reload and no duplicate debit |
| OTP/review and scheduler event updates | The prerequisite live mutations are deferred | Confirm notification/event refresh when these new events are exercised; Risk Officer STOMP connection and customer REST notification flows already passed |

Completed live checks, resource IDs and before/after evidence are recorded in LIVE_API_TESTS.md, LIVE_BROWSER_TESTS.md, TEST_RESULTS.md and DATA_CHANGES.md. This list is part of the final handoff; deferred checks remain visible until the user verifies them.
