# Live SafePay service layer

This frontend connects to the existing backend at http://localhost:8080. Open the UI at http://localhost:8000 to use the backend's existing CORS configuration.

See [INTEGRATION.md](../../../INTEGRATION.md) for the endpoint map, startup commands, validation and differences from the supplied browser-only demonstration.

All requests include session cookies. CSRF tokens remain in memory and protected writes include X-CSRF-TOKEN. Mutations are never retried automatically. Login supports mobile/password or email/password. Account lists, beneficiaries, payments and administrator actions use only the existing backend routes.

The countdown is anchored to the backend's protectionRemainingMillis response; the local Oracle timestamp is preserved for display. Cancellation still requires a backend-authorized PROTECTED payment and a fresh server check.