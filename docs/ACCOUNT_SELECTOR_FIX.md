# Account selector correction - 16 September 2026

Root cause: the Oracle JET CSP evaluator configured in src/ts/root.ts does not
expose a global String constructor. Inline dropdown callbacks called String(id),
so rendering an account/beneficiary option threw an expression-evaluation error.
The caption could render while the fetched account options could not.

Five callbacks in dashboard, beneficiaries, send-money and admin templates now
call id.toString(), retaining existing string-valued selection and account scoping.
No backend, database, risk policy or security-global changes were made.

Validation: five new tests use the installed JET evaluator and actual full selector
binding expressions. They reproduce the old failure and check empty, single and
multiple account lists and beneficiary lists. Full frontend suite: 140 passed.
TypeScript and JET build passed. The same four built HTML templates were delivered
to web/js/views so the already-running development server serves the correction.

Reload the browser (Ctrl+F5) and check Dashboard, Beneficiaries and Send Money.
No backend restart, SQL migration, account recreation or balance change is needed.
