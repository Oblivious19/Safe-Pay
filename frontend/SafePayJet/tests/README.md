# Frontend verification

Run `npm test` from this frontend directory. The active suite consists of:

| Script | Tests | Purpose |
|---|---:|---|
| test:recovery | 12 | Exact payment recovery metadata, identity isolation, expiry and fail-closed storage |
| test:setup | 3 | Installed pinned dependencies and local browser bundles |
| test:contracts | 12 | Exact numbers, API request shapes, retries, errors and canonical states |
| test:ui | 23 | Actual view models/templates with JET CSP evaluation and isolated canonical API fixtures, auth concurrency and OTP/review recovery |
| test:evidence | 4 | Read-only snapshot parsing, precision and change reporting |

These 54 checks are offline. `fixtures.cjs` and `mock-ui-server.cjs` never connect to Oracle or the backend. Start the visual fixture preview with `node tests/mock-ui-server.cjs`, then open http://localhost:8001/dashboard. The banner identifies fixture mode. The normal app at port 8000 does not load these replacements.

Other test files copied with Ruchi's original frontend remain as historical source references. They target the earlier API contracts and are not part of `npm test`; no passing claim is made for them. The original copied frontend was backed up before adaptation.

Live acceptance, user-deferred checks and executed results are recorded in `../../../integration-evidence/TEST_RESULTS.md`. The approved owner snapshot SQL was executed before and after live tests; both 19-table exports validated. Offline parser tests are reported separately from those real Oracle results.
