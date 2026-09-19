# SafePay Backend Deferred Phase 2.2 — Spring Security Completion

## Purpose and completed scope

This work formally completes the security implementation originally deferred from Phase 2.2. It does not create a new backend phase. SafePay now has one consistent authentication and authorization boundary for REST and advisory WebSocket/STOMP access, together with rotating refresh sessions, browser-cookie protections, administrative security controls and security audit evidence.

The implementation remains within the approved V1 prototype boundary. It protects SafePay's simulated pre-settlement workflows; it does not claim control over posted interbank settlement on live NPCI/RBI rails.

No Flyway migration, table, sequence, constraint, trigger or Oracle grant was added or changed. The implementation reuses the V1/V7/V10 contracts for `APP_USER`, `APP_ROLE`, `USER_ROLE`, `AUTH_SESSION` and `AUDIT_LOG`.

## Phase contents

| Subphase | Completed responsibility |
|---|---|
| SEC-A | Official Spring Security web, OAuth2 resource-server/Jose, messaging and test dependencies plus validated policy configuration. |
| SEC-B | Database-time failed-login tracking, five-attempt threshold, 15-minute temporary lock, reset after success and session revocation on lock. |
| SEC-C | HS256 access-token issuance and validation with 15-minute validity, issuer, audience, user ID, exact authorities and security version. |
| SEC-D | Seven-day opaque refresh sessions, SHA-256 hashes only, atomic rotation, replay detection, family revocation and user invalidation. |
| SEC-E | Login, refresh, logout and CSRF-bootstrap APIs with an HttpOnly refresh cookie and memory-only access-token contract. |
| SEC-F | Stateless bearer filter chain, exact route/method RBAC and RFC 7807 `401`/`403` responses. |
| SEC-G | Explicit credentialed CORS, cookie-operation Origin checks, CSRF protection and defensive HTTP response headers. |
| SEC-H | Shared authenticated-principal extraction, existing-controller closure, obsolete security deferral removal and forbidden-access audit evidence. |
| SEC-I | JWT-authenticated STOMP `CONNECT`, exact user-queue subscription authorization, cross-user denial and prohibition of inbound financial messages. |
| SEC-J | Approved `SYSTEM_ADMIN` user status, role and global session-revocation controls with locking, token invalidation and audit evidence. |
| SEC-K | Forged/expired/wrong-issuer/wrong-audience token coverage, browser/WebSocket isolation, Oracle repository verification and complete manual test gates. |

## Authentication architecture

```text
login credentials
      |
      v
generic credential authentication
      |
      +-- failure -> database-time counter -> attempt 5 locks user
      |
      +-- success -> reset counter, issue:
              access JWT (15 minutes; response body)
              opaque refresh token (7 days; HttpOnly cookie)

protected REST request
      |
      v
official JWT decoder verifies HS256 + issuer + audience + time
      |
      v
SafePay converter reloads current APP_USER + USER_ROLE
      |
      +-- reject LOCKED/DISABLED user
      +-- reject SECURITY_VERSION mismatch
      +-- reject authority-set mismatch
      +-- accept current SafePayPrincipal
```

The access token is not accepted merely because its signature is valid. Every authenticated REST and STOMP connection is reconciled against current database user status, security version and exact role assignments.

## Approved token and lock policy

| Policy | Enforced value |
|---|---|
| JWT algorithm | HS256 through Spring Security/Nimbus; no custom parser |
| Signing secret | Base64 environment value decoding to at least 256 random bits |
| Issuer | `safepay-backend` |
| Audience | `safepay-pwa` |
| Access-token validity | 15 minutes |
| Refresh-session validity | 7 days |
| Failed-login threshold | 5 consecutive failures |
| Temporary login lock | 15 minutes |
| Refresh storage | Opaque random token in HttpOnly cookie; SHA-256 hash only in Oracle |
| Refresh rotation | Every successful refresh replaces the old session |
| Replay response | Revoke the complete token family, increment security version and reject |

Raw passwords, raw refresh tokens, JWT signing secrets and access tokens are never written to Oracle audit details or application configuration files.

## Required environment configuration

The backend requires:

```text
SAFEPAY_JWT_SECRET_BASE64=<Base64 for at least 32 random bytes>
```

The local frontend origin defaults to `http://localhost:8000`. Override it explicitly when the approved PWA runs elsewhere:

```text
SAFEPAY_BROWSER_ORIGIN=http://localhost:8000
```

For an explicitly approved local non-HTTPS browser run only:

```text
SAFEPAY_REFRESH_COOKIE_SECURE=false
```

Outside local HTTP, refresh and CSRF cookies remain `Secure=true`. Secrets and credentials must stay in environment/run configuration, never source control.

## Browser CORS, Origin and CSRF contract

Credentialed wildcard CORS is prohibited. `BrowserSecurityProperties` validates every configured origin as an exact HTTP/HTTPS origin containing only scheme, host and optional port. Paths, queries, fragments, user information and wildcards are rejected at startup.

The PWA browser flow is:

1. Call `GET /api/v1/auth/csrf` with credentials enabled.
2. Read the returned token or the readable `XSRF-TOKEN` cookie.
3. For `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout`, send the token in `X-XSRF-TOKEN` and include credentials.
4. The browser supplies `Origin`; SafePay independently requires exactly one configured Origin.
5. Missing/incorrect Origin or CSRF evidence returns safe RFC 7807 `403` output.

Only refresh and logout use authentication cookies and therefore require CSRF validation. Other protected mutations use a bearer token in the Authorization header and continue to enforce ownership, state and idempotency in their existing service layers.

Exposed CORS response headers are limited to `X-Correlation-ID` and `Idempotency-Replayed`. Approved request headers include authorization, content type, Origin, CSRF, correlation and idempotency headers.

Security headers include frame denial, `no-referrer`, a deny-by-default API content security policy and disabled camera, microphone, geolocation and payment browser features. HSTS remains supplied by Spring Security on secure requests.

## REST route authorization

| Route family | Access |
|---|---|
| Registration, login, refresh, CSRF bootstrap, health and WebSocket handshake | Public transport entry; refresh still requires Origin + CSRF and refresh-cookie validation |
| `/api/v1/beneficiaries/**` | `CUSTOMER` |
| `/api/v1/transactions/**` | `CUSTOMER`, except the separately authorized audit route |
| `/api/v1/notifications/**` | `CUSTOMER` |
| `/api/v1/admin/risk-reviews/**` | `RISK_OFFICER` exactly |
| `/api/v1/audit-logs/**` | `AUDITOR` exactly |
| `/api/v1/transactions/{id}/audit` | `CUSTOMER`, `RISK_OFFICER` or `AUDITOR`, with service-layer ownership/context rules |
| `/api/v1/admin/users/**` | `SYSTEM_ADMIN` exactly |
| Logout | Any authenticated SafePay role, for its own session only |

There is no `ADMIN` authority. The combined local administrator works because it has separate `RISK_OFFICER`, `SYSTEM_ADMIN` and `AUDITOR` rows.

`AuthenticatedUser` is the single controller boundary for obtaining a SafePay user ID and canonical roles. Request payloads and query parameters never select the authenticated customer or administrator identity.

## Refresh-session safety

`AUTH_SESSION` stores only the hash of each refresh token. Rotation locks the user before the session, uses Oracle time, creates a replacement in the same token family, marks the old session used/revoked and links it to the replacement.

Reuse of a rotated token is treated as replay:

1. lock the user;
2. lock the complete family;
3. revoke every still-active family member;
4. increment `APP_USER.SECURITY_VERSION`;
5. append denied replay evidence;
6. reject without issuing another token.

Logout requires the authenticated user and an owned active refresh token. It revokes only that session and clears the refresh cookie.

## WebSocket/STOMP security

The HTTP handshake remains `/ws`, restricted to the same explicit configured browser origins. Authentication occurs only on STOMP `CONNECT`:

```text
Authorization: Bearer <access-token>
```

Tokens in URLs or query parameters are not read. The same official decoder and current-user converter used by HTTP authenticate the connection, so forged, expired, revoked-role, locked-user and security-version-stale tokens are rejected.

Subscriptions are exact:

| Destination | Required authority |
|---|---|
| `/user/queue/notifications` | `CUSTOMER` |
| `/user/queue/transactions` | `CUSTOMER` |
| `/user/queue/risk-reviews` | `RISK_OFFICER` |

Shared topics, direct `/queue/**` subscriptions, another user's destination shape and unknown destinations are denied. Every client `SEND` frame is denied because WebSocket delivery is advisory only. Financial and review mutations remain REST/service operations.

Denied authenticated STOMP subscriptions and sends append safe audit evidence. The evidence records a canonical reason code, never a token or requested destination.

## Administrative security controls

The four explicitly approved routes are:

| Method | Endpoint | Effect |
|---|---|---|
| `PATCH` | `/api/v1/admin/users/{userId}/status` | Set `ACTIVE`, administratively `LOCKED`, or `DISABLED`. |
| `PUT` | `/api/v1/admin/users/{userId}/roles/{roleCode}` | Assign one canonical role; repeated assignment is a no-op. |
| `DELETE` | `/api/v1/admin/users/{userId}/roles/{roleCode}` | Remove one existing canonical role; repeated removal is a no-op. |
| `POST` | `/api/v1/admin/users/{userId}/sessions/revoke` | Globally invalidate access and refresh sessions for the target user. |

All routes require `SYSTEM_ADMIN` at both method and service boundaries. The service locks administrator and target rows in ascending user-ID order, then rechecks that the administrator is active and still holds the live role.

Every actual status or role change:

- increments the target's security version;
- revokes all currently active refresh sessions;
- uses Oracle time;
- appends immutable `SYSTEM_ADMIN` audit evidence;
- returns only safe identity/status/role metadata.

Global session revocation increments the security version even when no active refresh row remains, ensuring all outstanding access JWTs become stale.

Administrative locks are indefinite (`LOCKED_UNTIL` is null), unlike the 15-minute authentication-failure lock. Unlocking restores `ACTIVE` and clears failure/lock fields. The service does not invent an unapproved self-management or last-role policy: an explicitly authorized `SYSTEM_ADMIN` operation applies to the path-selected user exactly as defined by the approved endpoint contract.

Administrators cannot view password hashes, OTP material, JWTs or refresh tokens and cannot mutate payments, balances, ledger or audit history through these controls.

## Oracle mapping and concurrency safeguards

- `APP_USER` uses `VERSION_NO` optimistic locking, plus pessimistic locks for security mutations.
- `SECURITY_VERSION` changes after lock/disable/unlock, role changes, replay compromise and global session revocation.
- `USER_ROLE` retains its `(USER_ID, ROLE_ID)` primary key and canonical `APP_ROLE` foreign key.
- `AUTH_SESSION` mutations use existing row/family locks and lifecycle constraints.
- `AUDIT_LOG` remains append-only under `TRG_AUDIT_LOG_IMMUTABLE`.
- Role and status values remain exactly those already constrained by V1/V11.
- `SAFEPAY_APP` already had the minimum required DML grants; none were widened.

## Main production components

### Authentication and token lifecycle

- `JwtSecurityProperties`, `AuthenticationPolicyProperties` and `RefreshCookieProperties` validate the fixed V1 policy.
- `JwtBeansConfiguration` publishes official HS256 encoder/decoder beans with issuer/audience validation.
- `JwtAccessTokenService` issues claims; `SafePayJwtAuthenticationConverter` reloads and compares current identity/security state.
- `LoginSecurityServiceImpl` owns failed-login state and temporary locking.
- `RefreshTokenCodec` creates opaque tokens and hashes them; `RefreshSessionServiceImpl` issues, rotates, detects replay and logs out.
- `AuthServiceImpl` composes login/refresh/logout; `AuthController` exposes the approved browser APIs.

### HTTP/browser enforcement

- `SecurityConfig` defines the stateless route matrix, resource server, exact CORS policy, explicitly configured Spring CSRF filter and security headers.
- `BrowserSecurityProperties` rejects unsafe origin configuration.
- `RefreshCookieOriginFilter` independently checks Origin on refresh/logout.
- `SecurityProblemWriter`, `SafePayAuthenticationEntryPoint` and `SafePayAccessDeniedHandler` provide correlation-aware RFC 7807 `401/403` output.
- `AuthenticatedUser` centralizes controller principal/role extraction.

### WebSocket enforcement

- `WebSocketConfig` registers only `/ws`, the private `/user` prefix and `/queue` broker prefix and installs the inbound security interceptor.
- `StompSecurityChannelInterceptor` authenticates CONNECT, authorizes exact subscriptions and denies SEND.
- `StompNotificationEventPublisher` continues to publish only sanitized user-specific events.

### Administration and evidence

- `AdminUserSecurityController` exposes the four approved routes.
- `AdminUserSecurityServiceImpl` provides locking, validation, security-version changes, session revocation and audit coupling.
- `AdminUserSecurityResponse` exposes safe fields only.
- `AuditLog.administrativeUserEvent` creates append-only administrative evidence.
- `SecurityIncidentAuditService` creates safe evidence for authenticated forbidden HTTP/method/STOMP access while preserving the stable denial response if audit persistence itself is unavailable.

## Verification coverage

The security tests cover:

- exact approved policy values and invalid configuration rejection;
- failed-login counting, lock expiry, threshold locking and session revocation;
- JWT claims, algorithm, signature, expiry, issuer, audience, current roles, status and security-version checks;
- refresh hashing, rotation, replay-family revocation, logout and expiry;
- refresh-cookie shape and authentication controllers;
- exact controller authority annotations and route-family contracts;
- RFC 7807 authentication/authorization failures;
- explicit-origin configuration, credentialed CORS and refresh/logout Origin filtering;
- centralized principal extraction and safe forbidden-access evidence;
- STOMP CONNECT, exact subscriptions, role separation, cross-user/shared destination rejection and SEND denial;
- administrative state semantics, role invariants, session invalidation, audit content, controller delegation and Oracle repository behavior.

The previously user-verified backend baseline is 736 tests. SEC-A through SEC-F add 76 tests. SEC-G through SEC-K add 54 net-new tests, producing an expected complete-suite total of:

```text
866 tests
```

The focused combined security run also includes seven pre-existing Phase 2.12 WebSocket routing tests, so its expected count is 137 rather than the 130 net-new security tests.

Offline `test-compile` succeeded for 252 production source files and 145 test source files. JUnit and Oracle tests remain deliberately unexecuted by Codex and the phase is not locked until the user completes the focused and full manual gates.

## Operational checklist for teammates

1. Generate a different random 256-bit-or-larger Base64 JWT secret for each environment; never commit it.
2. Keep access tokens in frontend memory only, not local/session storage.
3. Use `credentials: "include"` only against the exact configured SafePay origin.
4. Bootstrap CSRF before refresh/logout and send `X-XSRF-TOKEN`.
5. Never place bearer or refresh tokens in WebSocket URLs, logs or audit details.
6. Reconnect STOMP with the latest in-memory access token and reconcile authoritative state through REST.
7. Do not add shared customer topics or inbound WebSocket financial handlers.
8. Do not introduce a broad `ADMIN` role or treat `SYSTEM_ADMIN` as `RISK_OFFICER`/`AUDITOR`.
9. Do not edit V1–V11; any future schema need requires a separately approved forward migration.
10. Run all focused security groups and the complete 866-test suite before locking the backend implementation phase.

## Remaining boundary

No implementation subphase remains inside deferred Phase 2.2. Only the manual SEC-A–K focused/Oracle/security/full-suite verification gate is pending. End-to-end feature testing and frontend integration validation are separate next-stage activities and are not claimed complete by this guide.
