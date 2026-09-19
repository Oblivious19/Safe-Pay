# SafePay Backend Phase 2.12 — Audit, Notifications and Real-Time Updates

## Purpose and scope

Phase 2.12 completes SafePay V1's durable evidence and in-app communication layer. Financial and review workflows first commit their authoritative Oracle state together with immutable audit evidence and durable notifications. REST APIs retrieve that stored truth. A bounded dispatcher subsequently publishes small advisory STOMP messages so the frontend can refresh promptly, but a missed WebSocket message never changes or loses the underlying payment result.

This phase does not let WebSocket clients initiate financial mutations. SafePay remains a simulated pre-settlement control layer and does not claim to reverse a posted interbank payment.

No Flyway migration, table, sequence or constraint was added. The implementation reuses the existing V7/V10 `AUDIT_LOG` and `APP_NOTIFICATION` contracts. The approved official `spring-boot-starter-websocket` dependency was added for STOMP support.

## Phase contents

| Subphase | Implemented responsibility |
|---|---|
| 2.12A | Canonical `AUDIT_LOG`/`APP_NOTIFICATION` mappings, restricted repositories and exact Oracle vocabulary. |
| 2.12B | Customer-safe timelines, privileged audit projections and structured detail sanitization. |
| 2.12C | Canonical lifecycle-event matrix for payment, OTP, review and settlement boundaries. |
| 2.12D | Atomic audit/notification creation with deterministic references and deduplication identities. |
| 2.12E | Auditor global search and role-appropriate owned transaction audit REST APIs. |
| 2.12F | Owned notification listing and concurrency-safe, naturally idempotent mark-as-read REST behavior. |
| 2.12G | Durable bounded dispatcher, database-time retry scheduling and terminal delivery outcomes. |
| 2.12H | Advisory user-specific STOMP destinations and safe real-time event envelopes. |
| 2.12I | Ownership, masking, retry, Oracle lifecycle, reconnect and repository-contract verification. |

## Core authority model

The information flow is deliberately one-way:

```text
authoritative business transaction
        |
        +-- financial/review state mutation
        +-- immutable AUDIT_LOG append
        +-- durable APP_NOTIFICATION insert
        |
        v
Oracle commit succeeds atomically
        |
        +-- REST reads stored authoritative state
        |
        +-- notification dispatcher publishes advisory STOMP event
                |
                +-- received: frontend refreshes promptly
                +-- missed/disconnected: frontend reconciles through REST
```

WebSocket acceptance means the in-process messaging layer accepted the event. It does not prove that a browser displayed it.

## Oracle mappings and protections

### `AUDIT_LOG`

- Primary key uses `SEQ_AUDIT_LOG_ID`.
- `event_reference` is globally unique and limited to 64 characters.
- Events retain actor, role, action, entity identity, payment identity, previous/new states, outcome, reason, correlation ID, idempotency key and timestamp.
- `TRG_AUDIT_LOG_IMMUTABLE` rejects every update and deletion.
- Audit repositories expose append and approved read queries only; no delete surface exists.
- Customer timelines permit only an explicit safe action allow-list.
- Raw `details_json` is never returned. Privileged responses receive only sanitized approved fields.

### `APP_NOTIFICATION`

- Primary key uses `SEQ_APP_NOTIFICATION_ID`.
- `notification_reference` is unique and is the frontend deduplication identity.
- `(recipient_user_id, deduplication_key)` independently blocks duplicate business notifications.
- Delivery vocabulary is exactly `PENDING`, `RETRY_PENDING`, `DELIVERED`, `FAILED`.
- Channel remains exactly `IN_APP`.
- `@Version` protects concurrent read/delivery changes.
- `TRG_APP_NOTIFICATION_WRITE_GUARD` prevents deletion and changes to recipient, content, transaction identity, deduplication identity, correlation identity and creation time.
- Attempt count can only increase; terminal delivery cannot reopen; read state cannot revert to unread.
- `CK_APP_NOTIFICATION_LIFECYCLE` validates the complete timestamp/error/attempt shape for every delivery status.

No test or service disables these Oracle protections.

## Lifecycle evidence matrix

The canonical lifecycle service writes approved events at the real transition boundary, not afterward in a controller:

| Business event | Audit | Customer notification |
|---|---:|---:|
| Payment created | yes | no |
| Payment protected | yes | yes |
| Payment released | yes | yes |
| OTP required | yes | yes |
| OTP issued/resend/denied | yes | no |
| OTP verified | yes | yes |
| Risk review pending | yes | yes |
| Risk review approved | existing review audit retained | yes |
| Risk review rejected | existing review audit retained | yes |
| Re-verification requested | existing review audit retained | OTP-required notification |
| Payment cancelled | yes | yes |
| Payment failed | yes | yes |
| Payment settled | yes | yes |
| Settlement manual-review escalation | yes | no customer delivery claim |

References are deterministic hashes of the canonical event, payment and occurrence identity. Raw OTP values, email addresses, idempotency keys and other sensitive business identities are not copied into references.

## Audit REST APIs

| Method | Endpoint | Authorization and behavior |
|---|---|---|
| `GET` | `/api/v1/audit-logs` | Requires `AUDITOR` and a current active database role; newest-first bounded global search. |
| `GET` | `/api/v1/transactions/{transactionId}/audit` | Customer sees only an owned safe timeline; Risk Officer requires review context; Auditor receives the privileged sanitized timeline. |

Approved global filters are `transactionId`, `actionCode`, `outcome`, `actorType`, `correlationId`, `from`, `to`, `page` and `size`. Page size defaults to 20 and cannot exceed 100. Arbitrary database sorting is not accepted.

Customer responses omit event references, reason codes, actor metadata and detail objects. Risk Officer/Auditor responses still receive sanitized structures rather than raw JSON.

## Notification REST APIs

| Method | Endpoint | Behavior |
|---|---|---|
| `GET` | `/api/v1/notifications?page=0&size=20` | Lists only the authenticated user's notifications, newest first. |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Locks and marks only an owned notification read; replay returns the unchanged original read time. |

The response exposes the client deduplication reference, safe type/severity/title/message, optional payment ID, read state and timestamps. It omits correlation ID, business deduplication key, delivery errors, retry counts and internal routing state.

Cross-user and nonexistent notification identifiers use the same `NOTIFICATION_NOT_FOUND` result. Mark-read deliberately needs no `Idempotency-Key`: the first call stores `read_at`, and every replay returns that same value without a second mutation.

## Dispatcher policy and execution

The approved V1 dispatcher settings are:

```text
poll interval:       1 second
maximum batch:       25
maximum attempts:    5 total
retry after failure: 5 seconds, 30 seconds, 1 minute, 5 minutes
after attempt 5:     FAILED
```

`NotificationDispatcherProperties` rejects configuration drift at startup. The scheduler selects only `PENDING` rows or `RETRY_PENDING` rows whose Oracle deadline is due, oldest first and within the configured batch.

Each notification is handled in its own `REQUIRES_NEW` transaction:

1. lock the notification pessimistically;
2. recheck terminal state and due time;
3. publish the sanitized advisory event;
4. on success, increment the attempt and mark `DELIVERED`;
5. on publication failure, increment the attempt and persist the next approved deadline/error code;
6. after the fifth failure, mark `FAILED` with no next deadline;
7. flush before committing the worker result.

A failure in one notification does not prevent later IDs in the same bounded batch. It also cannot roll back the earlier financial workflow because notification dispatch occurs only after durable business commit.

## WebSocket/STOMP contract

The endpoint and destinations are:

```text
STOMP endpoint:                 /ws
customer notification refresh: /user/queue/notifications
customer transaction refresh:  /user/queue/transactions
Risk Officer queue refresh:     /user/queue/risk-reviews
```

No public/shared customer topic is used. Customer notification and transaction advisories route to the stored recipient's principal name. Review-event advisories separately route to every currently active user holding the canonical `RISK_OFFICER` role; they do not misuse the customer as a review-queue recipient.

The real-time envelope contains only:

- event kind;
- notification ID and client deduplication reference;
- approved notification type and severity;
- optional payment ID;
- occurrence timestamp.

It contains no amount, balance, account number, beneficiary identifier, OTP, raw audit details, internal note, error detail or credential.

There are no inbound `@MessageMapping` financial commands. All mutations remain REST/service operations. The simple in-process broker is appropriate for the V1 single-instance prototype; a clustered production deployment would require a durable external broker decision.

## Main production components

### Audit and evidence

- `OperationContext` carries correlation/idempotency context without accepting duplicated business facts.
- `TransactionLifecycleEvent` is the canonical audit/notification matrix.
- `TransactionLifecycleEvidenceServiceImpl` appends audit and notification evidence inside the caller's mandatory transaction.
- `AuditDetailSanitizer` converts approved detail fields and drops unknown/sensitive content.
- `AuditQueryServiceImpl` enforces live roles, ownership, safe action sets, filters and pagination.
- `AuditController` exposes the two approved read APIs.

### Notification REST

- `NotificationResponse` is the safe client projection.
- `NotificationServiceImpl` performs owned pagination and pessimistically locked mark-read operations using Oracle time.
- `NotificationController` derives the recipient from `SafePayPrincipal`; no customer ID is accepted from the request.
- `AppNotificationDao` provides only approved queries and mutations and has no deletion operation.

### Dispatch and real-time publication

- `NotificationDispatcherProperties` freezes the approved V1 polling, batching and retry policy.
- `NotificationDispatcherScheduler` triggers one bounded polling cycle.
- `NotificationDispatcherImpl` obtains due IDs and isolates unexpected per-item failures.
- `NotificationDispatchWorkerImpl` locks, publishes and records one delivery outcome atomically.
- `NotificationRealtimeEvent` is the deliberately minimal WebSocket payload.
- `StompNotificationEventPublisher` uses `convertAndSendToUser` for customer and officer-specific destinations.
- `WebSocketConfig` registers `/ws`, the private `/user` prefix and `/queue` broker destinations.

## Concurrency and recovery guarantees

- Mark-read and delivery both acquire a pessimistic row lock.
- `@Version` detects stale concurrent changes as an additional layer.
- A read timestamp is write-once and independent of delivery state.
- A terminal delivery status cannot return to pending/retry.
- Retry deadlines use Oracle time rather than browser or JVM time.
- Due IDs are revalidated after acquiring the row lock, so overlapping scheduler polls cannot publish the same attempt concurrently.
- A publication retry may produce a duplicate advisory if an earlier broker acceptance was followed by an uncertain failure. Clients use `notificationReference` to deduplicate and REST to reconcile.
- Durable notification rows remain readable even after delivery becomes `FAILED`.

## Verification coverage

Phase 2.12 tests cover:

- exact audit/notification mapping and restricted repository surfaces;
- safe audit filters, masking, role/ownership isolation and paging;
- lifecycle event generation, atomic coupling and deterministic references;
- notification DTO field exclusion and client deduplication identity;
- owned listing, cross-user invisibility and idempotent mark-read;
- delivery state transitions, attempts, deadlines and terminal protection;
- exact dispatcher policy, batch bounds and failure isolation;
- safe STOMP envelopes and user-specific routing;
- active Risk Officer review-queue routing;
- Oracle read timestamps, due queries and retry lifecycle checks;
- reconnect design in which REST remains authoritative.

The 2.12F–I implementation adds 56 tests across:

- `AppNotificationLifecycleTest`
- `AppNotificationRepositoryContractTest`
- `NotificationServiceImplTest`
- `NotificationControllerTest`
- `NotificationDispatcherPropertiesTest`
- `NotificationDispatcherSchedulerTest`
- `NotificationDispatcherImplTest`
- `NotificationDispatchWorkerImplTest`
- `StompNotificationEventPublisherTest`
- `WebSocketConfigTest`
- `NotificationServiceOracleIntegrationTest`

The previously verified baseline is 680 tests. The expected complete total after these 56 tests is 736. That total must not be treated as verified until the focused groups, Oracle integration test and complete suite are executed manually.

## Explicitly deferred security work

The separately approved Spring Security completion phase still owns:

- JWT login/access-token enforcement for HTTP;
- STOMP `CONNECT` authentication using the in-memory access token;
- inbound subscription authorization and cross-user subscription denial;
- stable security-level `401/403` responses;
- CORS, Origin, security-header and cookie controls;
- security-version and live user-status enforcement across HTTP/WebSocket sessions.

Until SEC-I is completed, `WebSocketConfig` is the transport/publishing foundation and uses same-origin handshake defaults, but it must not be represented as the final authenticated WebSocket boundary. No inbound financial message handler is exposed during this interim state.

## Operational notes for teammates

1. Treat Oracle and REST as authoritative; never mutate UI financial state solely because a WebSocket event arrived.
2. On connect/reconnect, fetch notifications and the affected transaction/review through REST.
3. Deduplicate advisories using `notificationReference`.
4. Never delete `AUDIT_LOG` or `APP_NOTIFICATION` rows or disable their protection triggers.
5. Do not manually reopen `DELIVERED`/`FAILED` notifications or clear `read_at`.
6. Do not add shared customer topics; use the approved user destinations.
7. Do not place money, account details, OTP material, audit JSON or internal review notes into real-time payloads.
8. Do not interpret `DELIVERED` as proof that a human saw the message; it means in-process publication was accepted.
9. Keep the dispatcher batch at or below 25 and do not change retry policy without a registered decision.
10. Complete and verify the deferred Spring Security phase before treating `/ws` as production-secured.

