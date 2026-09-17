# Validation — 16 September 2026

## Passed

- 46 Java tests: evidence queries (7), model contract (15), review orchestration (7), authentication/security (9), SafePay HTTP client (5), actual embedded HTTP server (1), real local Ollama inference (1), read-only Oracle integration (1).
- 5 frontend checks: exact currency formatting, invalid amounts, safe navigation, safe text rendering and matching HTML hooks.
- The local qwen2.5:1.5b model reviewed synthetic INR 120000 evidence, selected valid evidence IDs and identified missing history. No real customer/payment records were sent in this test.
- The configured Oracle connection successfully started a read-only transaction and executed a zero-row query covering all 17 required evidence columns. No DDL or DML was run.
- Existing SafePay source/configuration files were compared against 217 SHA-256 baseline hashes; none changed.

## What these checks do not establish

- An existing administrator's live browser sign-in and a real held payment were not exercised. Authentication, CSRF, role changes and held-payment flows were tested using controlled HTTP fixtures and H2 evidence records.
- Model output quality is not a validated fraud classifier. Output is restricted to existing evidence IDs and defined review checks. Admin approval continues through the existing SafePay screen.
- Frontend checks and HTTP delivery passed; no visual browser automation was performed.

## Reproduce

Run build-assistant.cmd for the standard Java tests. Run node --test tests/frontend.test.mjs for frontend checks.

Optional Command Prompt environment variables before a build:

    set SAFEPAY_OLLAMA_TEST=true
    set SAFEPAY_ORACLE_TEST=true
    set SAFEPAY_DATABASE_PROPERTIES=C:\Shreya\Safe-Pay\Backend\src\main\resources\application.properties
    build-assistant.cmd

The Oracle test executes only a zero-row SELECT inside a read-only connection. The Ollama test uses invented evidence.