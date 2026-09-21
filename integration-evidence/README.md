# SafePay-New integration evidence

All application changes are confined to SafePay-New/frontend/SafePayJet. The original SafePay and SafePay-ruchi-frontend trees were read-only. The copied backend, environment configuration, migrations, objects and grants remain unchanged; 502 captured backend file hashes matched at final verification.

## Delivery checkpoint

All four canonical role frontends are implemented in the supplied visual style. 54 offline tests passed, the final affected UI suite passed again, and the final TypeScript/build passed. All 93 logged live API outcomes matched expectations. Real browser checks covered customer flows and every staff workspace. Full OTP/review/settlement acceptance remains explicitly deferred by the user.

The new 1.00 browser payment 1558 is RELEASED and reserved while the settlement processor is disabled. All current balances are unchanged. The API protected-payment tests ended cancelled, the test-only beneficiary 1751 ends disabled, and every test-created session is revoked.

## Evidence index

- IMPLEMENTATION_STATUS.md: full screen inventory, contracts and acceptance checkpoint.
- TEST_RESULTS.md: test totals, findings, fixes and limits.
- LIVE_API_TESTS.md: 93 executed requests with expected HTTP results and correlation references.
- LIVE_BROWSER_TESTS.md: actual browser actions and observed outcomes.
- DATA_CHANGE_SUMMARY.md: table counts, funds and attribution summary.
- DATA_CHANGES.md: exact initial/end values for 1474 included-field differences across 107 rows.
- PENDING_USER_TESTS.txt: plain text checklist to run when available.
- PENDING_USER_TESTS.md: reasons and detailed acceptance criteria for deferred tests.
- ORACLE_ACCESS.md: completed read-only snapshot access and limits.
- baseline-before-v2.txt / baseline-after-v2.txt: valid private local snapshots; ignored by Git.
- capture-safe-baseline.sql / SnapshotRunner.java: reviewed read-only capture tools.
- compare-snapshots.cjs / finalize-evidence.cjs: offline validation, comparison and scoped attribution.
- backend-baseline-hashes.json: SHA-256 baseline for 502 captured backend files.
- frontend-before.zip: original copied frontend backup.
- video-frames/: offline reference frames from the supplied recording.

## Run the frontend

From PowerShell:

~~~powershell
Set-Location -LiteralPath 'C:\Users\Aditya Rao\Downloads\Training\Project\SafePay-New\frontend\SafePayJet'
npm.cmd test
npm.cmd run build
npm.cmd run serve -- --build=false
~~~

The real frontend is http://localhost:8000 and uses the running backend at http://localhost:8080. Final health returned UP. LiveReload and file watchers are disabled; rebuild and refresh after source edits. Do not start another backend against the same demo schema.

For isolated visual checks, node tests/mock-ui-server.cjs serves fixture data at localhost:8001. That preview is separate from live acceptance and was stopped after testing.

## Preserve the evidence

No schema cleanup was performed. Before rerunning any mutation script, agree its new data scope and create a separate baseline; these scripts create additional demo payment/audit/session records. Existing before/after exports are immutable evidence for this run. Snapshot comparison excludes secrets, hashes, token/key material and LOB contents, and cannot prove their equality.

OTP emails, live officer decisions, protection expiry and settlement processing remain user-run acceptance. Do not enable schedulers automatically: existing released/protected records also form a backlog.
