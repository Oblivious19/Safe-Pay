# SafePay Admin Assistant — isolated AI integration

This folder adds an on-demand admin review assistant alongside the existing SafePay backend and frontend. It does not modify their files, payment endpoints, risk rules, admin approval, account balances or database schema.

## What is included

- A separate Java 17 / Spring Boot service and responsive review UI at **http://localhost:8081/assistant/**.
- Sign-in using the existing SafePay ADMIN email/password or mobile/password. SafePay remains the authentication authority; role/status/session access is checked again for every protected read and before returning a generated review.
- The current HARD_HOLD requests, refreshed every 30 seconds while the assistant page is active.
- Fixed, parameterized, read-only Oracle queries for a customer's multi-account payment history, recipient history, recent request counts and amount comparisons.
- Local Ollama assistance that selects verified observations and review checks. Java computes money and statistics. The model cannot supply arbitrary figures, fraud percentages, SQL, approval instructions or payment actions.
- Honest evidence-only mode when Ollama is unavailable or its answer fails validation.
- Downloadable JSON reports containing evidence, review time, transaction version and an evidence fingerprint.
- A link to your **existing** SafePay Administration screen for approval. No approval or rejection endpoint exists in this module.

This is the Admin Assistant feature. It does not install a background autonomous agent, change payment tiers, or automatically approve payments.

## Start it

### 1. Start existing SafePay as usual

Run your existing backend and frontend from C:\Shreya\Safe-Pay. The assistant expects the backend at http://127.0.0.1:8080 and links to the UI at http://localhost:8000/admin.

Use the Oracle-backed version of SafePay. The assistant reads the connection settings from ..\Backend\src\main\resources\application.properties. It does not connect to SafePay's private in-memory local-profile H2 database.

### 2. Set up local Ollama once

Ollama is installed on this computer, and qwen2.5:1.5b has been downloaded. Real local inference passed using synthetic payment evidence on 16 September 2026. You can skip the installation steps below on this computer; they are included for another machine.

1. Install Ollama for Windows from https://ollama.com/download/windows.
2. In a new terminal, run:

    ollama pull qwen2.5:1.5b

3. Check that it is available:

    ollama list

The chosen local model download is approximately 986 MB according to its model page. Ollama itself needs additional disk space. Keep Ollama running; if its service is not already running, use **ollama serve** in another terminal. Do not start a second service if port 11434 is already in use.

For a strictly local Ollama setup, configure OLLAMA_NO_CLOUD=1 for the Ollama process. This assistant accepts only a loopback Ollama URL and rejects model names containing "cloud"; it sends only derived, anonymous evidence text to that endpoint.

### 3. Run the assistant

    cd /d C:\Shreya\Safe-Pay\AI-Integration
    start-assistant.cmd

Open **http://localhost:8081/assistant/**. Keep this terminal open. Java 17 or later is required. The packaged JAR is included in dist, so Maven is not required to run it.

### 4. Use it

1. Sign in with an existing ACTIVE ADMIN account. The assistant has its own session; signing out here does not sign you out of the existing SafePay browser tab.
2. Select a pending payment. Verified evidence loads immediately without requiring a model.
3. Click **Generate AI review**. The first local inference may take longer while the model loads. Requests have a configured timeout; an unavailable model leaves the database evidence usable.
4. Inspect the suggested focus and review checks alongside all evidence. Missing or limited history is explicitly reported.
5. Optionally download the report.
6. Click **Continue review in SafePay**. Sign in there if needed, then use the existing Review request / Approve and settle action.

If the payment is approved elsewhere during analysis, the assistant discards the stale result and asks you to refresh. A downloaded report remains a dated snapshot, not proof of current state.

## Configuration (optional environment variables)

| Variable | Default | Purpose |
|---|---|---|
| ASSISTANT_PORT | 8081 | Separate assistant HTTP port |
| SAFEPAY_BACKEND_URL | http://127.0.0.1:8080 | Existing backend; loopback HTTP only |
| SAFEPAY_UI_URL | http://localhost:8000/admin | Link to the existing admin UI |
| SAFEPAY_DATABASE_PROPERTIES | ../Backend/src/main/resources/application.properties | Read only the datasource URL/user/password settings |
| ASSISTANT_DB_URL | Original configuration | Override assistant JDBC URL if needed |
| ASSISTANT_DB_USERNAME | Original configuration | Override database user |
| ASSISTANT_DB_PASSWORD | Original configuration | Override database password |
| OLLAMA_BASE_URL | http://127.0.0.1:11434 | Local Ollama endpoint |
| OLLAMA_MODEL | qwen2.5:1.5b | Installed local model tag |
| OLLAMA_TIMEOUT_SECONDS | 90 | Model response timeout, bounded to 5–180 seconds |

Example in Command Prompt:

    set OLLAMA_MODEL=qwen2.5:1.5b
    set ASSISTANT_PORT=8081
    start-assistant.cmd

Existing application.properties values can contain Spring environment placeholders. The same corresponding environment variables must be available to the assistant process. Database credentials are read at request time and are not copied into this module or its JAR.

## Evidence definitions

- **Scope:** all accounts owned by the customer identified by the authenticated SafePay pending-payment response. The client cannot select a different owner.
- **30-day history:** SETTLED rows with settlement time in the 30 days ending at the current payment's creation time. Later settlements are excluded to prevent future information entering the comparison.
- **Recipient history:** previous settled transfers to the same bank account number and IFSC across that customer's accounts, even when separate beneficiary records exist for different source accounts.
- **Five-minute request count:** all payment requests created in that period, including the current request. It is not presented as a count of successful transfers.
- **Daily total:** settled transfers in the preceding 24 hours, excluding the current held payment.
- **Limited history:** fewer than five comparable settled payments. This is a display rule, not a validated fraud model or a new risk tier.
- All money uses BigDecimal and is returned as decimal text. Exact formatting is preserved in the UI.
- The latest 20 settled rows are displayed for inspection; aggregate statistics include every matching row.

Oracle review connections use SET TRANSACTION READ ONLY for a consistent snapshot. All SQL statements are fixed and parameterized; no DDL, DML or model-generated query is executed. A backend/database transaction-reference, owner, source account and amount match is required before any evidence is returned.

## Endpoints (assistant only)

Base: http://localhost:8081/assistant

- GET /api/session — creates/returns the CSRF token and safe session information.
- POST /api/login — email/password or phone/password; requires X-CSRF-TOKEN from /api/session and the assistant session cookie.
- GET /api/pending — current HARD_HOLD requests, ADMIN only.
- GET /api/model — local model readiness, ADMIN only.
- GET /api/payments/{id}/evidence — read-only evidence report, ADMIN only.
- POST /api/payments/{id}/review — on-demand local AI review, ADMIN only, CSRF required.
- POST /api/logout — ends the assistant's local and delegated backend session, CSRF required.

After login, GET /api/session again to obtain the token for the new session. Do not copy the SafePay UI's JSESSIONID into the assistant. Cookies and CSRF tokens are managed separately.

## Build and tests

Run **build-assistant.cmd** to compile, run Java tests and update dist/admin-assistant.jar. The script uses the existing backend Maven wrapper if present, otherwise Maven on PATH. It builds only this module.

Optional frontend checks with Node.js:

    node --test tests/frontend.test.mjs

Validation: 46 Java tests and 5 frontend checks passed. This includes a real local Ollama review of synthetic evidence, an actual embedded HTTP server/session check, and a read-only Oracle query confirming the configured schema columns. No payment data or schema was changed. See VALIDATION.md for scope and limitations.

Tests cover real H2 evidence queries, owner isolation and multi-account history, later-settlement exclusion, no balance/state mutations, exact money, authentication/CSRF, backend role revocation, stale model responses, duplicate reviews, local HTTP integration, invalid model output and sensitive-data exclusion from model prompts.

Optional integration tests are disabled during a normal build. Set SAFEPAY_OLLAMA_TEST=true to test your installed model using synthetic evidence. Set SAFEPAY_ORACLE_TEST=true and SAFEPAY_DATABASE_PROPERTIES to your configuration path to check Oracle connectivity and columns without returning payment rows.

The current feature provides evidence and suggested checks. It does not claim to detect proven fraud, calculate a calibrated fraud probability, verify customer intent or replace an administrator's review.

## Sources for model integration

- Ollama structured output: https://docs.ollama.com/capabilities/structured-outputs
- Ollama local generation API: https://docs.ollama.com/api/generate
- Model: https://ollama.com/library/qwen2.5:1.5b
