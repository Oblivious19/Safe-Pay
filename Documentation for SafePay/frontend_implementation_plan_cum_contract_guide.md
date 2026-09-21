# SafePay frontend implementation plan cum contract guide

**Contract date:** 20 September 2026  
**Project:** SafePay A — Oracle JET frontend with incremental integration into the existing SafePay backend  
**Status:** Phase 0A accepted from the user's 43 passing Maven tests. User authorized Phases 1–3 and deferred the synthetic same-tab console exercise to real payment integration. Phase 1–3 frontend source is implemented; compilation, tests, browser behavior and live integration remain user-verification gates. See section 19.  
**Execution order:** Phase 0 checkpoint → Phase 1 foundation/authentication → Phase 2 customer banking views → Phase 3 payment lifecycle → Phase 4 staff workspaces → Phase 5 integrated acceptance and handover.

**Navigation:** [Locked decisions](#2-locked-product-and-integration-decisions) · [Architecture/files](#3-architecture-and-minimal-file-structure) · [Visual contract](#4-approved-visual-and-reusable-component-contract) · [Integration rules](#5-exact-cross-cutting-integration-rules) · [Phase 0](#6-phase-0--approval-and-compatibility-checkpoint) · [Phase 1](#7-phase-1--foundation-reusable-visuals-authentication) · [Phase 2](#8-phase-2--accounts-dashboard-profile-and-beneficiaries) · [Phase 3](#9-phase-3--payment-lifecycle-history-otp-and-customer-notifications) · [Phase 4](#10-phase-4--complete-separate-staff-workspaces) · [Phase 5](#11-phase-5--full-integration-resilience-accessibility-and-handover) · [API inventory](#12-api-coverage-inventory--no-page-capability-left-implicit) · [Deferred reminder](#15-deferred-work--mandatory-post-integration-reminder).

## 1. Purpose, authority, and limits

This is the working implementation contract for building a usable SafePay prototype from the team's useful frontend structure and the approved visual inspiration, while retaining A's financial, security, database, and REST contracts. Each phase delivers screens **and their actual A API integration together**. A collection of mocked pages is not a completed phase.

The aim is precise, reviewable implementation and verification. Neither a plan nor passing automated tests can guarantee that future work will be error-free. An unresolved discrepancy must be reported, isolated, and resolved before the dependent implementation proceeds.

### 1.1 Sources and permitted use

| Source | Location | Authority and permitted use |
|---|---|---|
| A: authoritative backend | `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay` | Current backend, exact DTOs, roles, migrations, state machine, services, and constraints remain authoritative. |
| B: functional frontend reference | `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Frontend/SafePayJet` | Read-only source of reusable page organization, MVVM patterns, presentation, and selected interaction ideas. Rewrite incompatible integration logic against A. |
| B: backup frontend references | `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Frontend-backups` | Read-only references for serial refresh and pending-attempt recovery ideas. Not a second application to merge wholesale. |
| C: visual inspiration | `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay_Frontend_for_Codex/Frontend/SafePayJet` | Read-only UI/UX reference only: colors, themes, CSS, layouts, cards, templates, component presentation, spacing and motion. No C business logic, security, routes, or data contracts. |
| Current decisions | This contract and the user's latest explicit instructions | Newer explicit user decisions override older PRD, chat, handoff, or local instruction conflicts. |
| Backend decision sources | `Documentation for SafePay/UPDATED_Decision_Register.md`, `UPDATED_implementation_plan.md`, reconciled register, `change_request_implementation_guide.md` | Explain why A works as it does. Verify historical statements against current source. |
| Context and learning roadmap | `SafePay_Backend_DB_Context.md`, `dbsetup_cum_backend_master_guide_contract.md`, `GOALS_FOR_TODAY.txt` | Preserve checkpoints and later documentation/presentation obligations. They do not independently authorize additional implementation. |

The supplied alternative attachment location under `Downloads/SafePay_Frontend_for_Codex` was not the source used; the inspected inspiration is the Training/Project location above.

### 1.2 Execution permissions

1. This request authorizes creation of this guide. It does **not** authorize frontend implementation, package installation, backend changes, launches, test execution, SQL execution, or deployment.
2. Read-only listing, searching, and reading of the authorized project/reference locations remain permitted without repeated confirmation.
3. Once the user explicitly approves a phase and its proposed affected files, direct scoped implementation is allowed. Paste-ready code is unnecessary unless requested.
4. B and C remain read-only. Do not alter, reorganize, build, or install inside them.
5. No unrelated refactoring, schema changes, system changes, extension installation, package downloads, or secret/configuration changes. Dependency and execution approvals must be explicit and scoped.
6. Maven tests remain user-run. The historic permission for compile-only checks does not imply permission to launch applications, run tests, contact Oracle, or apply migrations.
7. If a critical choice or implementation flaw appears: report the exact source, evidence, impact, alternatives, and smallest proposed change; pause the affected work and obtain approval. Do not silently repair A to fit a frontend assumption.
8. Never claim compliance with an employer's internal monitoring or approval system. Follow the concrete access and change boundaries the user provided.

### 1.3 Frozen baseline and evidence boundary

- A's original backend phases 2.1–2.12 and change-request phases 0–8 are locked at the user's accepted checkpoint.
- User-provided evidence confirms **1,148 full-suite tests**, **178 affected tests**, and **31 regression tests**, with zero failures, errors, and skips on 19 September 2026. These are historical user-run results, not tests rerun for this document.
- Earlier totals such as 866, 918, 1,002, and 1,054 are superseded checkpoint counts.
- Applied V1–V13 and repeatable grants remain immutable. V1–V11 and V13 are in `db/migration`; V12 showcase seed remains in `db/showcase`. Do not reseed, backfill, repair Flyway, reset balances, or import B's data for frontend development.
- A green backend suite does not prove browser cookie behavior, accessibility, live API integration, or manual database outcomes. Those are explicit gates below.
- A has no implemented frontend under this contract yet. All proposed frontend paths in this document are **future files**, not claims of existing implementation.

## 2. Locked product and integration decisions

| ID | Decision | Consequence for implementation |
|---|---|---|
| D01 | Keep A as the common unchanged backend | Adapt frontend contracts to A; do not merge B's backend, migrations, or data. |
| D02 | Retain prepared demo accounts | Registration creates a customer identity, not an account or opening balance. A new user can legitimately have no accounts. |
| D03 | Four exact authorities | `CUSTOMER`, `SYSTEM_ADMIN`, `RISK_OFFICER`, `AUDITOR`. “Admin/staff” is a collective UI term, never a fifth authority or a permission union. |
| D04 | Registration email and mobile mandatory in frontend only | A's API/database remain permissive. This is a deliberate prototype limitation; bypassing the frontend can bypass the added requirement. |
| D05 | Passphrase-friendly password validation | Retain A's length/UTF-8 limits, add approved weak-password controls, and avoid arbitrary uppercase/digit/symbol composition rules. Exact blocklist behavior is a Phase 0 gate. |
| D06 | Show customer reference after registration | Display returned `userId` as Customer ID, with copy and a note to retain it. No new database column or invented bank/customer identifier. |
| D07 | Separate accounts and balance retrieval | Select an account first; retrieve its balance independently. Do not infer a balance from account summaries. |
| D08 | Account activity means existing outgoing payment activity | Filter customer transactions by `sourceAccountId`. It is not a full bank statement or incoming-credit ledger. |
| D09 | Customer-owned beneficiaries | Keep `BANK_ACCOUNT` and `UPI`; do not attach beneficiaries to a selected source account. |
| D10 | A's explicit create/authorize workflow | `CREATED` is a saved instruction, not successful payment, completed protection, or settlement. |
| D11 | Exact payment categories | Only new payments **above ₹1,00,000.00** require a category. At or below the boundary, omit category. |
| D12 | Legacy category compatibility | Existing category remains null; display “Not specified” for historical high-value payments and sort those after the five categories. Preserve existing purpose. No reseeding. |
| D13 | Exact decimal money and identifiers | Preserve A's `BigDecimal`/`NUMBER(18,2)` semantics and current mixed numeric/string wire responses. Keep IDs as strings in frontend state. |
| D14 | Staff UI reflects A's actual routes | SYSTEM_ADMIN gets account/user/operations tools; RISK_OFFICER gets reviews and decisions; AUDITOR gets read-only evidence and reconciliation. |
| D15 | Medical-first review priority | Use the server's category filter and priority ordering across the full paginated queue. Category urgency does not alter risk classification. |
| D16 | Profile editing and daily reporting deferred | No working edit-profile or daily-report feature now. Read-only profile and current operational statistics are in scope. |
| D17 | C visual direction approved | Teal/mint light and teal-charcoal dark themes, unified with B's Manrope typography and carefully adapted useful layouts. |
| D18 | Reusable Oracle JET structure | Small shared component foundation, thin page view models, services, and one HTTP/codec boundary. No large custom component framework. |
| D19 | No vector functionality | Oracle vector search, embeddings, semantic search, and their deployment decisions are removed entirely, not put on the pending list. |
| D20 | No new administrative health feature | No new health endpoint, screen, DTO, or scheduler-liveness claim. Existing stats/failures remain in scope. |
| D21 | Countdown convenience remains conditional | The user requested a proposal, not implementation approval for response enrichment. Gate it separately; do not infer approval from visual-design acceptance. |
| D22 | Incremental integration | Every phase uses real A responses, ownership/role checks, failure behavior, and persistence verification appropriate to its feature. |

### 2.1 Earlier wording superseded by these decisions

- “Nullable legacy categories should become OTHERS” was explicitly reverted. Null and existing purpose remain unchanged.
- “Admin sees all customer information” is constrained by the exact SYSTEM_ADMIN DTOs and routes, not direct entity serialization or a generic admin role.
- The account directory includes `accountId` as well as owner identity, masked account number, type, bank/IFSC, and status. It does not include list balances. Full account number and all three balances belong in individual detail.
- The proposed branch/location filter is not in current A and is excluded from this frontend contract.
- Existing OpenAPI-before-frontend notes predate the user's explicit shift to an integrated frontend prototype and final documentation stage. OpenAPI stays pending; current source-derived contracts must be verified in each phase meanwhile.
- Old AGENTS/PRD suggestions for device/location signals, complete Maker-Checker, AI, or extra financial operations do not re-enter scope through this guide.

## 3. Architecture and minimal file structure

### 3.1 Oracle JET MVVM model

```text
HTML view + JET controls / shared components
                 ↕ Knockout bindings and component events
Page view model: observable UI state, validation, user intent
                 ↓
Feature API service: exact routes, request/response mappings
                 ↓
Shared HTTP client + lossless wire codec + session coordination
                 ↓
A REST APIs / advisory WebSocket events
                 ↓
A services / repositories / canonical Oracle schema
```

This is MVVM with an additional service/transport boundary. The Model is frontend data and its service-backed representation; it does not require duplicating every JPA entity. View models must not contain raw Oracle assumptions or independent payment-state business rules.

B's `oraclejetconfig.json` declares `architecture: mvvm`; its package manifest uses JET/core-pack `~21.0.0`, CLI `~21.0.1`, and TypeScript `5.8.3`. Retain the compatible MVVM/Knockout/TypeScript/RequireJS baseline as the starting candidate, rather than introducing a Virtual DOM/React migration. Confirm the resolved lockfile and runtime before approving installs. A root `node >=16` declaration alone does not establish compatibility with all resolved tooling.

Oracle documents custom components as registered metadata/view/view-model/CSS units with Knockout binding support; use them only where reusable behavior warrants them. Simple shared styling does not require a separate custom element for every control. See [Oracle JET custom component model](https://docs.oracle.com/en/middleware/developer-tools/jet/13/reference-api/CompositeOverview.html) and the [MVVM development guide](https://docs.oracle.com/en/middleware/developer-tools/jet/20/develop/toc.htm). These explain architecture; exact component APIs must be checked against the selected local JET 21 typings and documentation during implementation.

### 3.2 Proposed target root

`C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/frontend/SafePayJet`

All relative frontend paths below are relative to this proposed root. Nothing is copied until phase authorization. Preserve B's useful conventions (`src/ts/views`, `src/ts/viewModels`, `src/ts/services`, `src/css`) to reduce learning cost.

| Proposed files/area | Responsibility | Creation/change phase |
|---|---|---|
| `package.json`, lockfile, `oraclejetconfig.json`, `tsconfig.json` | Audited compatible tooling and scripts; no copied caches/build output or uncontrolled dependency additions | 1, after Phase 0 approval |
| `src/index.html`, `src/js/main.js`, `src/js/path_mapping.json` | Entry shell, module mapping, theme startup and approved assets | 1 |
| `src/ts/root.ts`, `src/ts/appController.ts`, `src/ts/accUtils.ts` | Application lifecycle, routing, title/focus handling, authorized navigation | 1 |
| `src/ts/services/apiClient.ts`, `apiError.ts`, `wireCodec.ts` | One transport, ProblemDetail adapter, exact numeric parsing/serialization | 1 |
| `src/ts/services/types.ts` | Shared `Id`, decimal, paging, role and error contracts; split feature DTOs only when necessary for readability | 1, incrementally extended |
| `src/ts/services/authService.ts`, `sessionRouteService.ts` | Login/refresh/logout, in-memory session, route access and session coordination | 1 |
| `src/ts/utils/money.ts`, `format.ts`, `theme.ts` | Exact amount input/display, dates/IDs, theme selection | 1–2 |
| `src/css/tokens.css`, `app.css`, `responsive.css` | Semantic design tokens, shell, shared variants and responsive foundations | 1 |
| `src/ts/jet-composites/sp-*` and small shared templates | Only genuinely reused interactive UI; follow the selected JET tooling's component registration convention | 1–4 as first required |
| `src/ts/views/{home,about,login,register}.html` and matching view models | Public explanation, authentication and registration success | 1 |
| `src/ts/services/{accountService,profileService,beneficiaryService}.ts` | Customer read routes and beneficiary mutations | 2 |
| `src/ts/views/{dashboard,profile,beneficiaries}.html` and matching view models | Account selector/activity, read-only profile, beneficiary management | 2 |
| `src/ts/services/{transactionService,otpService,notificationService,realtimeService}.ts` | Customer payment, verification, notification and advisory event contracts | 3; transaction listing starts in 2 |
| `src/ts/services/pendingPayment.ts`, `refreshLoop.ts` | Scoped operation-attempt state and serial refresh lifecycle; adapt B backup ideas safely | 3; refresh utility can start in 2 |
| `src/ts/utils/paymentState.ts` | Exhaustive presentation mapping of A states, not an alternate state engine | 3 |
| `src/ts/views/{send-money,transactions}.html` and matching view models | Payment review/submission, history, detail and status/OTP panels | 3 |
| `src/ts/services/{adminAccountService,adminUserService,adminOperationsService,riskReviewService,auditService}.ts` | Explicit role-separated staff API adapters | 4 |
| `src/ts/views/{admin-accounts,admin-users,admin-operations,risk-reviews,audit}.html` and matching view models | Staff workspaces; auditor evidence uses tabs/panels rather than a page for every GET | 4 |
| Focused page CSS alongside shared CSS | Only layout genuinely specific to a page; no duplicated visual system | Each phase |
| Focused frontend tests and later browser-flow tests | Contract/precision/session/state regressions and integrated flows; tooling approval required | Each phase; consolidated in 5 |
| Manifest/static shell assets and approved service-worker configuration if required | Installability and offline explanation, never queued payments or cached banking responses | 5 |

These names establish the intended ownership of code. Do not create empty files merely to satisfy the table. Reuse an existing appropriate module before adding one; keep helpers separate when they actually eliminate duplication or isolate high-risk behavior.

### 3.3 What survives from B and what changes

| B area | Decision | Treatment in A frontend |
|---|---|---|
| Page templates and view-model organization | ADAPT | Preserve comprehensible MVVM modules and page flow; rebind to A DTOs. |
| Service layer split | ADAPT | Keep one feature service per coherent domain. Replace old routes/auth/field names, do not retain a second HTTP client. |
| `api.ts` and stale alternate adapters | REJECT as active integration | Do not copy competing/unused clients into A. |
| Customer dashboard/account cards | ADAPT | Separate account summaries and balance fetch; outgoing activity uses source-account query. |
| Beneficiary cards and Pay shortcut | ADAPT | Customer ownership; pass opaque beneficiary ID, preselect and refetch safely. |
| Payment review/status layouts | ADAPT | Retain useful flow/presentation, replace B's creation, hold, retained-balance and state assumptions completely. |
| Staff cards/tables/drawers | ADAPT | Exact A role separation and DTOs; no `ADMIN` shortcuts. |
| Daily-report service | DEFER | No B endpoint calls; no mock values presented as real operational reports. |
| Backup serial refresh loop | ADAPT | One in-flight read, cancellation/generation guards, bounded retries, cleanup on disconnect/logout. |
| Backup pending attempt/retry-key ideas | ADAPT conditionally | Scope to signed-in user + operation + resource; preserve frozen payload and key across VM navigation. Storage/reload policy requires Phase 0 decision. |
| Direct cancel handler bypassing confirmation | REPLACE | Confirmation dialog opens first; only its explicit confirmed action calls A. |
| `setInterval` polling/catch-all logout/invalid-money-as-zero patterns | REPLACE | Serial refresh, status-specific errors, explicit invalid/unavailable values. |
| Full page reload navigation and VM teardown clearing uncertain attempts | REPLACE | Router navigation; app-scoped recovery state survives view disposal. |
| B balance overwrite, retained ₹5,000 rule, PIN, old hold vocabulary | REJECT | No compatible A capability authorizes them. |

## 4. Approved visual and reusable-component contract

### 4.1 Theme tokens and typography

| Token role | Light theme | Dark theme |
|---|---|---|
| Page background | `#F5F7F7` | `#0C1416` |
| Main surface | `#FFFFFF` | `#151E21` |
| Elevated surface | Light neutral derived from the surface palette, contrast checked | `#1C282C` |
| Main text | `#18343C` | `#F1F7F5` |
| Muted text | Dark-enough neutral, contrast checked | `#9EB0AC` |
| Primary action/accent | `#12675F` with verified foreground | `#3DCEB4` with `#032824` foreground |
| Borders | Visible neutral divider, contrast checked | `#2C3C40` |
| Featured account card | Restrained `#12343D` → `#1B4B51` gradient with verified text | Same family adapted to dark surrounding surfaces |

- Use **Manrope** consistently; fallback to Segoe UI/system sans-serif. Font delivery/license and network behavior are a Phase 0 dependency/asset gate. Do not download fonts merely because the visual choice is approved.
- Use semantic CSS variables for surfaces, text, action variants, status colors, spacing, radii, shadows, layering and motion. Page files consume tokens instead of redefining colors.
- Start from system light/dark preference; allow explicit light/dark and “Use system.” Persist only theme preference by default, and apply it before visible paint to avoid a theme flash.
- Light and dark modes must both work for dialogs, fields, overlays, tables, error messages and disabled controls. Do not hard-code the payment panel to dark mode.
- Money uses tabular numerals and exact decimal formatting; identifiers can wrap/copy without losing digits. Masked account text remains masked.
- Use one cohesive icon system already compatible with the selected JET version. SafePay shield/check/initials are appropriate; Oracle sample logos and scaffold avatars are not product branding.

### 4.2 Shared building blocks

| Building block | Reuse and contract |
|---|---|
| App shell/header/navigation | Role-aware route groups, theme control, user menu, notification entry, mobile drawer; current authority decides visibility. |
| Page heading and actions | Consistent title, short explanation, breadcrumbs/back action and optional refresh/context label. |
| Account card/balance panel | Account ID/masked identity and selected state; distinct balance loading/error state; no balance fetched inside a purely presentational card. |
| Beneficiary card | Name, type, masked destination, status, Pay event; parent handles eligibility and navigation. |
| Transaction card/row | Exact amount, beneficiary, reference, timestamp, canonical state and category where applicable. |
| Status badge/panel | Text + icon + semantic color. Category urgency and payment risk are separate concepts. |
| Payment status/OTP panel | Server state, next permitted action, deadline/cooldown information, safe confirmation and refresh; no autonomous financial transition. |
| Form field/validation presentation | JET controls, associated labels, hints, inline errors and summary focus. Do not wrap every standard control unnecessarily. |
| Confirmation dialog | Distinct primary/destructive variants, busy state, keyboard trap/restore and explicit action. |
| Feedback states | Skeleton, empty, filtered-empty, stale, unavailable, forbidden and retry states. Unavailable balance is never ₹0.00. |
| Staff filter/paged results/detail | Shared filter layout and pagination; feature-specific columns and authority. No generic all-powerful admin component. |

Use standard JET components for buttons, fields, select controls, dialogs, drawer, validation groups, progress and data presentation. Custom components expose properties/events/slots; they do not fetch arbitrary APIs or read global session state. Shared presentation stays separate from feature services.

### 4.3 Page design selections

| Page/area | Approved presentation and required adaptation |
|---|---|
| Home/about | Clear split hero, restrained SafePay illustration, concise protection explanation and steps. Explicit educational/simulated-payment wording; no real-bank guarantee. |
| Login/register | Focused form, readable error/help text, comfortable spacing, theme-consistent controls, password visibility action. No published demo credentials or PIN card. |
| Registration success | Persistent success card with actual Customer ID and copy action; short check animation, reduced-motion alternative. Explain identity creation and prepared-account onboarding. |
| Dashboard | Account selection first, balance/status card, selected account's outgoing activity, useful actions and pending-payment visibility. No fictional aggregate balance or daily revenue. |
| Beneficiaries | Search/filter only over honestly identified loaded data or an existing API; type/status distinction, accessible Pay action and clear disabled explanation. |
| Payment entry/review | Focused desktop dialog/panel and mobile bottom-sheet style with sufficient space for category/purpose, review details and errors. Keep form state on safe validation failures. |
| Payment status | Calm state-specific explanation and actions. Progress is derived from actual server milestones, not an animated promise of settlement. |
| History/detail | Compact readable rows/cards, real server paging/filtering, full detail fetch on open, timeline and explanation where authorized. |
| System admin | Denser directory/operations layout, clear read-only account detail, separate user-security confirmations. |
| Risk officer | Medical-first server queue, category filter, amount/risk evidence, detail and decision area; reason and reverification flows clearly distinguished. |
| Auditor | Dense evidence tables with scoped filters and detail panels, reconciliation values and safe audit timeline. No operational mutation controls. |
| Notification center | Paged feed/drawer, explicit read status and links to authorized fresh detail, with accessible live feedback. |

### 4.4 Rejected visual borrowing and required improvements

- Do not merge B's forest/lime styling and C's teal direction into competing themes.
- Do not copy all generated `staged-themes`, vendor CSS, `web`, `node_modules`, caches, scaffold pages or default Oracle branding.
- Avoid continuous pulsing/glowing financial actions, floating decorative orbs, noisy confetti, fake countdown progress, auto-playing chimes, or motion that distracts from amounts and confirmation.
- Fix C-style variant conflicts where a generic theme rule overrides the destructive/cancel button. Use scoped variants, not escalating `!important` or undocumented JET internals.
- Do not mark every pending payment red. Reserve error/danger styling for its actual meaning; protected/verification/review states need distinct text.
- Fix low-contrast source pairings rather than copying them literally. Target WCAG AA: 4.5:1 normal text, 3:1 large text, visible focus and usable non-text controls; test actual rendered states. See [WCAG 2.2](https://www.w3.org/TR/WCAG22/).
- Test keyboard navigation, screen-reader names, focus return, zoom/reflow, reduced motion, safe areas and mobile keyboard overlap. Keep tables semantic and scroll their container rather than globally changing every table into block elements.
- Use short purposeful transitions; skeletons preserve layout. CSS inspection alone is not accessibility or responsive certification.

## 5. Exact cross-cutting integration rules

### 5.1 Session, routing and security

1. Access tokens live in application memory. Never place them in URLs, localStorage, analytics, console output or user-visible error objects.
2. The backend owns the HttpOnly `SAFEPAY_REFRESH` cookie. JavaScript must not read or manufacture it. Its current path is `/api/v1/auth`; SameSite is Strict.
3. Use credentialed requests where cookie/CSRF exchange requires them. Fetch `GET /api/v1/auth/csrf` and use its returned `headerName`/`token` for refresh and logout; do not invent blanket CSRF exemptions or send an obsolete token indefinitely.
4. Match A's exact allowed origin. `localhost` and `127.0.0.1` are not interchangeable origins. Local HTTP requires the already supported explicit secure-cookie override; production HTTPS remains secure.
5. On startup, resolve the session before rendering protected content. Deep links retain only safe relative destinations and must pass the current role gate after authentication.
6. Centralize refresh. One refresh request at a time within a tab is necessary but is not enough for concurrent tabs sharing a rotating cookie. Resolve the cross-tab gate before claiming this works.
7. Distinguish 401/session expiry from 403/permission denial, validation, business-rule rejection and network failure. Never log out for every exception; never refresh indefinitely.
8. Clear user-scoped caches, subscriptions and sensitive in-memory state on logout or identity change. Re-fetch current protected data after session/authority changes.
9. Authority-based navigation is UX only; A remains the enforcement boundary. Multi-role users see only the routes allowed by their actual authority set; role selection does not grant roles.
10. Session-revocation/status/role changes can invalidate a running browser. Surface a clear sign-in-again outcome and do not keep showing stale privileged data.
11. Protect forms through correct validation, output encoding and normal text bindings. Never use raw HTML binding for names, purpose, reason, notification or error content. “Sanitization” must not alter passwords or silently change payment intent.
12. A's API CSP is not a frontend HTML hosting policy. If frontend hosting/proxying is changed, check the actual HTML CSP, script/style/font sources and cookie behavior; do not relax backend security headers to make an unreviewed asset work.

### 5.2 Money, identifiers and response envelopes

| Concern | Binding contract |
|---|---|
| Money input | Work with decimal text; reject exponent notation/commas as API input, more than two fraction digits, empty/negative/sub-₹1 payment values and overflow. Do not silently round `5000.001`. |
| Exact calculations | Use an approved lossless codec and decimal representation; integer minor units with `BigInt` are suitable for fixed-scale comparisons. Never compare payment thresholds or available funds through binary floating point. |
| Wire compatibility | A balance DTOs use strings, while transaction/review amounts use JSON numbers from `BigDecimal`. Parse numeric lexemes before precision is lost; converting a value to string after ordinary JSON parsing is insufficient. |
| Outbound serialization | Preserve the JSON numeric fields expected by A without first converting through `Number`. Use the approved codec's safe serializer; do not hand-build JSON or assume quoted numbers/BigInt serialize correctly. |
| IDs | Keep returned IDs as strings, including route/query IDs. Long-valued request fields require exact numeric wire serialization; no `parseInt` roundtrip for database IDs. |
| Mixed naming | Account DTO currency is `currency`; transaction DTO currency is `currencyCode`. Explicit mapping is required. Do not rename fields globally. |
| Paging | A uses `{items,page,size,totalElements,totalPages,first,last}`. Page index starts at 0; reset page on filter change; use server totals. Do not treat it as Spring's raw `content` format. |
| Arrays | Customer accounts and beneficiaries are arrays, not the staff paged response. Do not wrap or unwrap them by guesswork. |
| Dates | Send proper ISO offset timestamps. Present understandable local time while retaining exact timestamp on detail. Date filters must reflect server `from` inclusive / `to` exclusive semantics. |
| Missing/invalid data | Null, omitted, malformed, forbidden and unavailable are different from zero/empty. Reject malformed critical DTOs visibly rather than fabricating safe-looking balances. |

**Required codec proof:** round-trip `9007199254740993` as an ID; preserve cents near `9999999999999999.99`; distinguish `100000.00` from `100000.01`; retain `0.10` exactly for display/arithmetic; reject a three-decimal payment. These are representation tests, not instructions to submit enormous live payments.

### 5.3 Errors, idempotency and unknown outcomes

- Map A's ProblemDetail response and extensions, including `errorCode`, `fieldErrors`, correlation information and OTP attempt information where present. Inspect current `GlobalExceptionHandler` and security problem responses rather than importing B's error envelope.
- Display a concise customer message and safe correlation reference; never expose SQL, stack traces, credentials, tokens or raw security exception detail.
- Generate one correlation ID per logical HTTP attempt as appropriate; record the server-returned `X-Correlation-ID` for support. It is not the idempotency key.
- A requires exactly one `Idempotency-Key` for create, authorize, cancel, OTP issue/resend/verify, and officer decisions/notes. `Idempotency-Replayed` is an exposed response header.
- Each logical operation has its own key and **frozen** request. A new OTP code submission is a new verify operation; retransmission of the same uncertain verification keeps the same key and payload.
- A timeout, aborted fetch, tab close or network loss does not prove the mutation rolled back. Keep the UI in “Checking outcome” or equivalent, block duplicate submission, and reconcile through existing detail/list APIs or safe same-key replay.
- Never retry a money mutation automatically with a fresh key. Never alter the payload under an old key. Never create another payment because an authorize request failed to return.
- Do not assume a 2xx response means payment success: authorization can return a transaction in `FAILED`. Read the returned state.
- Non-idempotency-service routes (registration, beneficiary creation, admin user controls, notification read) must not be described as having durable replay just because the client adds a header. Handle duplicate-click suppression and uncertain outcomes according to their actual contracts.
- Current idempotency retention is finite (configuration is PT12H). Recovery must not assume keys remain valid forever. An unresolved attempt beyond supported replay cannot be silently treated as a new payment.

### 5.4 Refresh, WebSocket and race handling

- Adapt the B backup's serial refresh concept: wait for one read to finish before scheduling the next, use cancellation/generation checks, and prevent late results from replacing a newly selected account/filter/user.
- Page disposal stops that page's timers/subscriptions; it must not erase an unresolved app-scoped payment attempt. Logout/identity changes require separate deliberate handling.
- Refresh authoritative detail and affected balances after financial mutations. Cancelling must not leave a stale reserved amount on the dashboard.
- Use `/ws` with STOMP and one bearer `Authorization` CONNECT header. Allowed subscriptions are `/user/queue/notifications` and `/user/queue/transactions` for CUSTOMER, `/user/queue/risk-reviews` for RISK_OFFICER.
- Do not put tokens in WebSocket URLs, invent SockJS support, subscribe to other users' destinations or send financial commands via STOMP. Client SEND frames are denied by A.
- Events are advisory refresh signals, not a replacement for REST state. Reconcile after reconnect, token renewal, tab focus or missed events. Debounce bursts and dispose listeners correctly.
- A stale review/payment action can legitimately lose a race. On conflict, preserve the message and re-fetch; never override the server with optimistic local settlement/cancellation.

## 6. Phase 0 — approval and compatibility checkpoint

**Deliverable:** approved execution scope and a recorded answer for each decision that blocks the next phase. No app implementation merely because this guide exists.

### 6.1 Checks already grounded in source

- A exposes account list/detail and a separate balance endpoint.
- A exposes source-account/date/state transaction filtering and real paged responses.
- Profile is read-only through `GET /api/v1/users/me`.
- Category validation and server priority ordering already exist; no new category migration or backend feature is needed.
- Existing operations statistics/failures and auditor evidence endpoints can support their screens without new backend APIs.
- There is no need to copy C business code or B data to implement the selected interface.

### 6.2 Decisions requiring explicit approval before dependent work

| Gate | Recommended resolution for approval | Why it matters / affected scope | When to stop |
|---|---|---|---|
| G1: implementation authorization | Approve the target root and Phase 1 affected frontend files; later phases can be approved individually or explicitly as a bundle | Guide approval is not implicit permission to install/run/change backend | Before first implementation |
| G2: tooling and additional dependencies | Use compatible locked JET 21/TS baseline; audit actual Node/toolchain requirements; approve exact versions, lossless JSON/decimal strategy, STOMP client if needed, and test tools before installation | Build/runtime, precise money, supply/download permissions | Before dependency installation or choosing a codec that cannot meet the proofs |
| G3: countdown | Prefer separately approved GET-only server-time enrichment, or explicitly retain an estimated display from existing timestamps with A deciding all expiry/actions | Existing transaction detail lacks the convenience fields; backend changes are not already approved | Before claiming an accurate server-aligned payment countdown |
| G4: cross-tab refresh | Recommend a shared refresh coordinator using supported browser locking/coordination, fresh cookie state after acquiring the lock, and a tested fallback; no token persistence | A's rotating refresh cookie is shared across tabs; independent refreshes can trigger replay defenses | Before session concurrency is declared complete; ask again if fallback would restrict supported browsers/tabs |
| G5: reload recovery storage | Recommend minimal, identity-scoped pending-operation metadata with expiration; persist frozen non-secret request only if user approves browser storage and exact fields. Never persist OTP/password/token | Navigation-only memory recovery does not solve hard reload after an unknown create result | Before payment submission/recovery implementation; do not claim reload-safe replay without the needed evidence |
| G6: weak-password rule | Keep A's 12–72-character and 72-UTF-8-byte bounds; add a reviewed small local common-password/context check, no remote password service or arbitrary composition rule | Product direction is approved; exact rejection list/threshold remains a concrete choice | Before registration-strength policy is finalized |
| G7: fonts/assets and hosting | Approve Manrope delivery/source/license and any new asset/dependency; confirm local browser/API origin and intended HTTPS deployment path | Prevent silent downloads, CORS/cookie/font failures and unreviewed external requests | Before downloading assets or changing serving configuration |

These gates do not prevent producing this complete guide. They prevent later implementation from pretending that unapproved choices were already settled.

### 6.3 Optional countdown change: bounded backend proposal only

If G3 approves enrichment, inspect and propose a scoped patch to:

- `backend/src/main/java/com/ofss/dto/transaction/TransactionResponse.java` — optional GET presentation fields such as `serverTime`, `protectionRemainingMillis`, and `canCancel`, with explicit null/omission rules.
- `backend/src/main/java/com/ofss/services/TransactionServiceImpl.java` — enrich `getTransaction` using one consistent database-time observation and existing cancellation eligibility; do not change funds, state transitions or deadline enforcement.
- `backend/src/main/java/com/ofss/controller/TransactionController.java` — only if needed to keep enrichment exclusively on the detail GET path.

No new endpoint, table, column, migration or scheduler is required for this convenience. Preserve existing POST responses and persisted idempotency response compatibility. Approve concrete DTO serialization and regression scope before editing; the file list is a bounded proposal, not proof that tests require no updates.

Without enrichment, label any browser-clock-derived countdown as an estimate, stop it visually at zero, re-fetch state, and let A accept/reject cancellation. Do not transition to RELEASED because a JavaScript timer reached zero. OTP already returns server-time/cooldown data and is a separate contract.

### 6.4 Runtime prerequisites for user-run integration

| Area | Check before the affected scenario |
|---|---|
| Oracle | Existing owner/app connections reach the correct PDB and V13 schema. Runtime uses restricted app credentials; migration owner stays server-side. |
| Flyway | Existing showcase environment resolves both `classpath:db/migration,classpath:db/showcase`; target 13 if pinned. No new migration is required by the core frontend plan. |
| JWT | Existing `SAFEPAY_JWT_SECRET_BASE64` present in backend environment only. Never a frontend variable. |
| Browser origin | Match `SAFEPAY_BROWSER_ORIGIN` to the actual frontend origin; current default is `http://localhost:8000`. |
| Cookies | Local HTTP uses the explicitly approved `SAFEPAY_REFRESH_COOKIE_SECURE=false`; HTTPS uses secure cookies. Preserve SameSite/origin/CSRF enforcement. |
| Protection | `SAFEPAY_PROTECTION_SCHEDULER_ENABLED` must reflect the intended timer-release scenario. Disabled scheduling must not be mistaken for a frontend countdown defect. |
| Settlement | `SAFEPAY_SETTLEMENT_PROCESSOR_ENABLED` and the approved clearing-account configuration must be ready before expecting automatic simulated settlement. A RELEASED transaction is otherwise a valid observed state. |
| Notifications | Dispatcher setting and event delivery affect timeliness; REST remains authoritative. No false scheduler-liveness indicator. |
| OTP delivery | Manual email verification needs the existing approved development routing/configuration. Tests must not suddenly send real email. Frontend never receives mail credentials or reads stored OTP verification material. |
| Personas | Existing distinct customer/other-customer/system-admin/risk-officer/auditor accounts; no production credentials in guide/source/demo screen. Use separate browser profiles/isolated contexts for simultaneous different-persona flows because ordinary tabs share the refresh cookie. |

At each phase, record which prerequisites were actually satisfied. Do not mark an unexecuted or blocked scenario as passed.

## 7. Phase 1 — foundation, reusable visuals, authentication

**Outcome:** a themed, responsive JET shell with secure real login/refresh/logout and registration, ready for the later banking pages.

### 7.1 Build sequence

1. After Phase 0 authorization, create the minimal A frontend scaffold from selected B source/configuration, excluding generated output, old endpoints, sample pages and credentials.
2. Establish tokens, theme startup, typography, page shell, basic feedback/confirmation patterns, focus/title behavior and component registration.
3. Implement `wireCodec`, `apiClient`, typed errors and shared DTO primitives before feature calls, so numeric corruption cannot spread into later services.
4. Implement auth/session service with CSRF exchange, credentials, access-token memory, expiration/refresh, cross-tab solution and exact route guards.
5. Build login/register/home/about views using real A responses. Avoid blanket adoption of B register fields.
6. Add registration success with returned Customer ID, copy feedback, reduced motion, and explicit account-onboarding explanation.
7. Exercise session restoration, deep links, permission failures and logout before unlocking customer screens.

### 7.2 Backend sources consumed, not changed

`AuthController`, `RegisterUserRequest`, `RegisterUserResponse`, `LoginRequest`, `AuthTokenResponse`, `CsrfTokenResponse`, `SecurityConfig`, `RefreshCookieOriginFilter`, `RefreshCookieFactory`, `RefreshSessionServiceImpl`, `SafePayJwtAuthenticationConverter`, security problem writers and `GlobalExceptionHandler`.

### 7.3 Registration contract

| Field | Frontend rule | A mapping / important difference |
|---|---|---|
| `fullName` | Required, normalized ordinary text, 2–120 characters | Same request name; do not split into incompatible first/last fields on the wire. |
| `email` | Required in UI, valid address, ≤254 characters | A normalizes case/whitespace and still allows absence if alternate contact is valid. |
| `mobileNumber` | Required in UI, E.164 format `+` and 8–15 total digits | A pattern is `^\+[1-9][0-9]{7,14}$`; do not send a local-format number without validated normalization. |
| `password` | 12–72 characters and at most 72 UTF-8 bytes; approved weak-password controls | Preserve bytes exactly. Do not trim, lowercase or strip characters. Password-manager paste/autofill remains usable. |
| Confirmation password | Client comparison only | Never add it to A's request. |
| Customer ID | Response `userId` | A's generated identity; not a new column, bank account, or guaranteed login identifier. |

Login uses `loginIdentifier` and `password`. Do not reject an existing password at login merely because the new registration strength advice is stricter. Do not import DOB, address, PAN/KYC, PIN, direct account creation, or balance allocation.

### 7.4 Exit gate

- Real registration returns 201 and identity details; missing UI email/mobile is prevented, while the known backend permissiveness is accurately documented.
- Real login, valid refresh, logout, session restoration, expiry, CSRF rejection and wrong-role navigation behave correctly.
- New user with no account is handled gracefully; no account/funding request is fabricated.
- Tokens/secrets absent from URLs, browser persistent storage, logs and page source; theme preference is safe to persist.
- Two-tab refresh/logout and session invalidation are demonstrated with the approved strategy.
- User-run persistence check confirms new `APP_USER` and customer `USER_ROLE` association, plus applicable `AUTH_SESSION`/audit effects; no bank account is silently created.
- Type/build and meaningful focused tests are run only under explicit execution approval. Report exact observed counts, not an invented target.

## 8. Phase 2 — accounts, dashboard, profile and beneficiaries

**Outcome:** a customer can understand prepared accounts, inspect accurate balances/outgoing activity, view their profile, and manage customer-owned beneficiaries through A.

### 8.1 Build sequence and source mapping

| Step | Frontend work | A sources to inspect and use |
|---|---|---|
| 1 | `accountService.list/get/getBalance`; account selector/cards | `AccountController`, `AccountServiceImpl`, `AccountSummaryResponse`, `AccountBalanceResponse` |
| 2 | Dashboard selected-account loading/error/empty/stale states | Above DTOs plus `TransactionController.list`, `TransactionServiceImpl.listTransactions`, `TransactionSummaryResponse` |
| 3 | Shared paged outgoing-activity list with account/date/state filters | `TransactionService`/`TransactionServiceImpl`, current transaction DAO and `PagedResponse`; same adapter later reused by history |
| 4 | Read-only profile view | `CustomerProfileController`, `UserService`, `CustomerProfileResponse` |
| 5 | Beneficiary list/create/detail/status UI | `BeneficiaryController`, `BeneficiaryServiceImpl`, `CreateBeneficiaryRequest`, `BeneficiaryResponse`, `UpdateBeneficiaryStatusRequest` |
| 6 | Pay shortcut preselection and route handoff | Pass beneficiary ID to later payment page; validate customer ownership/ACTIVE state afresh |

Names in this source mapping are backend symbols, not instructions to change them. Resolve interfaces/implementations from current source if a future rename occurs.

### 8.2 Account and activity behavior

- Fetch owned account summaries, display their masked identities and statuses, select an eligible account, then fetch that account's balance separately.
- Show `currentBalance`, `reservedAmount`, and `availableBalance` with clear explanations. Available equals current minus reserved; A has no retained-₹5,000 rule.
- Use `GET /transactions?sourceAccountId=...` for selected-account activity. Label it “Outgoing payments” or equivalent. Do not claim an incoming-credit bank statement.
- Account switching cancels/invalidates old reads; late responses cannot populate the newly selected card.
- Empty account list, unavailable balance, inactive account, no payments and no filter matches have different explanations.
- Do not fetch every account's balance blindly on every poll. Fetch what the visible selection needs, reusing safe short-lived in-memory state only when marked stale appropriately.
- Default recent activity is allowed, but pending visibility and history must not silently assume that the first 20 rows are all records. Use real pagination and explicit server filters.
- A's transaction query accepts one `state` at a time. Implement state tabs/filter choices for awaiting authorization, protected, verification, officer review and released/awaiting settlement, each backed by its own selected-state paged query. Do not send an invented comma-separated/array state filter. Do not claim a combined pending count from one downloaded page; a future combined-state endpoint requires a separate decision.

### 8.3 Beneficiary payload rules

| Field | Required treatment |
|---|---|
| `beneficiaryName` | 2–120 characters after A-compatible trimming; do not send `name`. |
| `nickname` | Optional, ≤60. |
| `paymentMethod` | Exactly `BANK_ACCOUNT` or `UPI`. |
| Bank beneficiary | `bankName` ≤120, `bankAccountNumber` ≤34, `ifscCode` matching `^[A-Z]{4}0[A-Z0-9]{6}$`; omit/null UPI fields as the DTO requires. |
| UPI beneficiary | `upiId` ≤255; omit/null bank-only fields; mirror A's normalization without changing intended identity. |
| `relationshipLabel` | Optional, ≤50. |
| `purposeNote` | Optional, ≤140; this is beneficiary context, not payment category or payment purpose. |
| Status action | `PATCH /beneficiaries/{beneficiaryId}/status` with `ACTIVE` or `DISABLED`; do not invent DELETE/update-all routes. |

The Pay action preselects the beneficiary and presents the customer's source-account choice and amount. Do not repeat beneficiary entry. Do not put full bank details, UPI identifier, amount draft or secrets in a URL. Re-fetch authoritative detail before submission; a disabled beneficiary remains ineligible even if an old card says ACTIVE.

### 8.4 Exit gate

- Both account summary and separate balance calls match exact DTOs; no numeric/field-name drift.
- Activity really filters by selected source account; date/state/paging and switch races verified.
- Beneficiary creation works for both methods; mismatched method fields, duplicates, malformed IFSC, inactive beneficiaries and cross-customer access are handled safely.
- Profile has no edit/save control; accounts have no create/delete/fund control.
- User-run read-only database checks find only the intended beneficiary/status changes in `BENEFICIARY`; dashboard/profile reads do not mutate financial state. Security/read-audit records may legitimately be written by A.
- Missing and malformed source data is visible, never replaced by a fake zero balance or fabricated beneficiary.

## 9. Phase 3 — payment lifecycle, history, OTP and customer notifications

**Outcome:** customer payment screens faithfully represent A from instruction creation through protection/verification/review/release/settlement. Staff-dependent completion is explicitly finished in Phase 4.

### 9.1 Build sequence

1. Finish `transactionService`, typed requests/responses, exact money/category validation and exhaustive state presentation.
2. Build source/beneficiary/amount entry and review confirmation. Refetch relevant eligibility/balance; the server remains final authority.
3. Create the instruction with a stable create key; retain returned transaction ID; authorize that same instruction with a separate stable authorize key after explicit confirmation.
4. Build protected/cancel/status panels, approved countdown behavior, unknown-outcome recovery and serial authoritative refresh.
5. Add OTP issue/resend/verify with independent idempotency, deadline/cooldown/attempt controls and accurate failure messages.
6. Complete history/detail/risk explanation/audit timeline; all detail views re-fetch actual detail rather than treating a list row as complete.
7. Add notification feed/read marking and authenticated advisory WebSocket refresh, with reconnect/poll fallback.
8. Validate lower/medium/high paths and very-high handoff. Complete officer decision cycles in Phase 4 before declaring end-to-end very-high success.

### 9.2 Backend sources consumed

`TransactionController`, `TransactionService`/`TransactionServiceImpl`, `TransactionStateServiceImpl`, `TransactionDb`, `TransactionState`, `PaymentCategory`, `MoneyUtility`, transaction DTOs, `IdempotencyService`, `RequestFingerprintService`, `VerificationController`, `OtpServiceImpl`, OTP DTOs, `NotificationController`, `AuditController`, `StompSecurityChannelInterceptor`, protection/settlement services and current risk-policy mappings. The historical test class named `TransactionQueryServiceTest` does not imply that a production class called `TransactionQueryService` exists.

No controller/service/schema change is included merely to simplify a page. The conditional countdown patch requires its own approval gate.

### 9.3 Request contracts and visible payment flow

| Operation | Exact contract / frontend responsibility |
|---|---|
| Create | `POST /api/v1/transactions`: `sourceAccountId`, `beneficiaryId`, `amount`, optional `purpose`, optional `customerReference`, conditional `category`; exactly one Idempotency-Key. Successful response is 201 and a CREATED instruction. |
| Authorize | `POST /transactions/{transactionId}/authorize`, body `{"confirmed":true}`, own key. Creation and authorization are separate backend transactions; they are not one atomic browser operation. |
| Inspect | `GET /transactions/{transactionId}`; read state even on a 2xx mutation response. |
| Explain | `GET /transactions/{transactionId}/risk-explanation`; show evidence from A, not a fabricated risk score or category-derived risk. |
| Cancel | `POST /transactions/{transactionId}/cancel`, no invented request body, own key. Confirmation first, refetch transaction and balance afterwards. |
| OTP issue/resend | `POST /transactions/{transactionId}/otp` or `/otp/resend`, separate logical keys; accepted challenge response is 202. |
| OTP verify | `POST /transactions/{transactionId}/otp/verify`, `challengeId` and six-digit `otp`, own logical verify key. Success 200; failed verification can produce 422 with attempts information. |
| History | Query existing account/date/state/page/size filters; use response items and totals. |
| Audit timeline | Existing `GET /transactions/{transactionId}/audit`, ownership checked by A for CUSTOMER. |

Create's `amount` must fit 16 integer digits and two fractional digits. Normal `purpose` is optional up to 280 characters; `customerReference` is optional up to 100. Do not confuse customerReference with the server-generated transactionReference or user ID.

### 9.4 Category and legacy behavior

| Payment | Category form | Outbound category | Purpose | Display/ordering |
|---|---|---|---|---|
| New ₹1.00–₹1,00,000.00 inclusive | Hidden/not required | Omit | Existing optional purpose, ≤280 | No category label anywhere for that payment |
| New above ₹1,00,000.00, non-OTHERS | Mandatory choice | Exact enum | Optional, ≤280 | Display chosen label; server priority |
| New above ₹1,00,000.00, OTHERS | Mandatory choice | `OTHERS` | Required after trim, 1–140 | Display purpose and Others label |
| Existing lower/equal payment | Hidden | No new write | Preserve existing value | No category label |
| Existing high-value category null | No retrospective mandatory selection | No new write | Preserve existing value unchanged | “Not specified”; after the five categories |

Priority order is `MEDICAL`, `LOAN`, `FRIENDS_FAMILY`, `INVESTMENTS`, `OTHERS`, then legacy null. Display “Friends & Family” while sending `FRIENDS_FAMILY`. Medical category alone does not prove an emergency, change VERY_HIGH risk, bypass OTP, or authorize settlement.

When amount changes from high-value to lower-value before submission, remove the stale category from the outbound request. If an earlier request is already uncertain, do not mutate its frozen payload. Start no replacement operation until its outcome is resolved.

The user approved strict rejection of category-free new high-value requests, including old category-free POST retries. Existing stored payments remain processable. Do not introduce a frontend compatibility bypass.

### 9.5 Canonical state presentation

| A state | User meaning | UI behavior; server remains authoritative |
|---|---|---|
| `CREATED` | Payment instruction saved, not authorized | Show review/authorize or eligible cancel; no paid/protected claim. |
| `AUTHORIZED` | Authorization processing milestone | Processing display; fetch current state; do not assume it is a stable screen. |
| `RISK_ASSESSED` | Risk policy applied | Processing display with available evidence; refetch. |
| `PROTECTED` | Funds reserved during protection window | Deadline/estimated timer as approved; eligible Undo through A; timer zero triggers refresh, not release. |
| `VERIFICATION_REQUIRED` | Customer verification required | Real OTP controls and eligible cancellation; no approval/settlement claim. |
| `PENDING_RISK_REVIEW` | Awaiting risk officer decision | Explain hold; eligible cancel; show approved safe review/transaction evidence. |
| `RELEASED` | Released for simulated settlement | Processing/awaiting settlement; reserved funds can remain; no customer cancel control. |
| `SETTLED` | Simulated settlement completed | Final receipt/reference and refreshed balance. Never claim actual external beneficiary credit. |
| `CANCELLED` | Cancelled before settlement | Show terminal reason when safely available; no further authorization/cancel. |
| `FAILED` | Payment failed | Show safe reason and current outcome; no assumption that a fresh retry is safe until reconciled. |

Unknown future states render as unsupported/current-state-unavailable with refresh and no financial action, not as completed. Review `REJECTED` is a review outcome; the payment becomes `CANCELLED`, not a new transaction `REJECTED` enum.

### 9.6 Risk/timing presentation and boundaries

| Amount band in current approved policy | Expected tier | Expected path after authorization |
|---|---|---|
| ₹1.00–₹5,000.00 | LOW | RELEASED, then settlement processing; no protection timer |
| >₹5,000.00–₹25,000.00 | MEDIUM | 10-second protection, eligible Undo, then release/settlement |
| >₹25,000.00–₹1,00,000.00 | HIGH | 60-second protection and stronger warning, then release/settlement |
| >₹1,00,000.00 | VERY_HIGH | No countdown-based auto-release; OTP then officer review |

These explain the current policy, not a license to duplicate the policy engine in JavaScript. Display returned risk/policy/deadline values and respect changed server outcomes. Category selection is the request's conditional rule; it is not a browser risk engine.

### 9.7 OTP and recovery details

- Bind challenge ID, transaction ID, `expiresAt`, `resendAvailableAt`, `remainingIssues`, and `serverTime` from actual challenge responses. Verification response uses `challengeStatus`, `transactionState`, `verified`, `remainingAttempts`, `verifiedAt`, and `serverTime`.
- Show masked delivery destination. Do not expose the OTP from logs, database, stored hash, or developer-only responses.
- Disable resend until the server-derived cooldown permits it; handle a server rejection if clocks or state have advanced.
- Preserve same-key replay for an uncertain issue/resend/verify. A newly typed different OTP requires a new verification attempt/key, so a changed value does not collide with its prior fingerprint.
- After successful verification, refetch transaction detail. Verification can lead to review; it is not settlement.
- Handle expired code, invalid code, exhausted attempts, issue limit, unavailable email delivery, a cancelled transaction, and officer-requested reverification separately.
- There is no invented GET-OTP-challenge endpoint. Reload recovery must use the approved retained non-secret metadata and current existing issue/resend behavior; inspect service eligibility before offering an action. Never promise the old code remains valid after a resend.
- Clear OTP input promptly on completion/logout; do not persist it for reload recovery.

### 9.8 Exit gate and database expectations

- Create does not debit/reserve funds; authorization obtains the current eligibility/available-funds decision and can reserve funds or return FAILED according to A.
- One logical create produces one payment; same-key retry returns its existing result, key-reuse with changed payload is rejected, and lost-response UI cannot generate duplicates.
- Category boundary, purpose limits, old null-category rows and amount changes behave exactly as section 9.4.
- Cancellation wins/loses races truthfully, releases the relevant reservation when accepted, and refreshes UI balances.
- MEDIUM/HIGH expiration depends on actual backend release processing, not browser time.
- OTP attempt/cooldown/reverification and notification read behavior work without secret leakage.
- Read-only verification uses `PAYMENT_TRANSACTION`, `ACCOUNT`, `TRANSACTION_RISK_FACTOR`, `IDEMPOTENCY_RECORD`, `PAYMENT_OTP_CHALLENGE`, `AUDIT_LOG` and `APP_NOTIFICATION` as applicable. Do not inspect secret OTP/session material.
- Expected evidence is operation-specific. A failed validation must not create a payment; a business failure during authorization can legitimately persist a FAILED payment and audit evidence.
- Very-high officer approval/rejection/reverification and full settlement evidence remain explicitly linked to Phase 4/5 gates, not silently counted as passed here.

## 10. Phase 4 — complete, separate staff workspaces

**Outcome:** all current staff capabilities are accessible through precise, reusable UI, and the customer/officer/auditor/admin integration loop can be demonstrated.

Implement the following three workspaces in sequence within this one phase. Shared filter/table/detail patterns reduce duplication; permissions and API services remain separate.

### 10.1 SYSTEM_ADMIN: accounts, users, operations

| Area | Implementation details | A source/contract |
|---|---|---|
| Account directory | Server paging; filters `customerId`, `minCurrentBalance`, `accountType`. Customer lookup can use the authorized user directory; pass its `userId` as account query `customerId`. | `AdminAccountController`, `AdminAccountServiceImpl`, admin account DTOs |
| Account rows | Only `accountId`, `ownerId`, `ownerName`, `maskedAccountNumber`, `accountType`, `bankName`, `ifscCode`, `status` | No list balance/full account number; no unimplemented branch filter |
| Account detail | Format every returned field: IDs/name/full account number/type/bank/IFSC/currency/current/reserved/available/status/version/timestamps | `AdminAccountDetailResponse`; exact `versionNo` name |
| Separate balance read | Use existing admin account balance endpoint where a focused refresh is appropriate | Reuse account balance presentation, not customer endpoint under a staff token |
| User directory/detail | `q`, `role`, `status`, server paging; show safe returned profile/security information | `AdminUserSecurityController`, `AdminUserSecurityServiceImpl`, `AdminUserSecurityResponse` |
| User status | `PATCH .../status` with `status` = `ACTIVE`, `LOCKED`, or `DISABLED`; confirmation and fresh result | This is user status, not bank-account disabling |
| Role assignment/removal | PUT/DELETE existing role path with exact enum; no generic `ADMIN` | Read live authorities and A's current constraints; do not invent self/last-admin restrictions absent from A |
| Session revocation | Existing POST endpoint; explain forced reauthentication consequences | No fabricated response body or durable-idempotency claim |
| Operations statistics | Actual snapshot with `observedAt`, state counts, pending reviews, exception/notification statuses and backlog counts | `AdminOperationsController`, `OperationalDashboardResponse` |
| Failures | `source=TRANSACTION` or `NOTIFICATION`, transaction ID/date range/page/size; safe explanation and retry metadata | `OperationalFailureResponse`, reporting service/repository |

No SYSTEM_ADMIN account delete, direct balance edit, arbitrary settlement, officer decision, health dashboard, daily report, or new account-status endpoint is added. A failure row is read-only; a “Retry now” button cannot be invented from retry metadata.

### 10.2 RISK_OFFICER: priority queue and decisions

1. Load `/admin/risk-reviews` with default `sort=PRIORITY`, optional exact `category`, page and size. `sort=OLDEST` is the existing alternate option.
2. Keep the server ordering across pages. Do not locally sort only the downloaded page and call it the global medical-first queue.
3. Open detail by **review ID**, not transaction ID. `RiskReviewDetailResponse` contains a nested `review` summary plus assigned/deciding actors, reason, timestamps and version.
4. Present masked customer/account context, exact amount, purpose/category, risk/policy/evidence and current review/transaction status.
5. Approve, reject, request verification and add note through their separate current endpoints. Approval reason can be optional according to A; rejection/reverification require `reason` (max 1,000). Notes use `note`, not `reason`.
6. Use a separate stable key per logical decision/note and require confirmation for consequential actions. Do not create a claim/reassign/edit-note endpoint.
7. Re-fetch review and related transaction evidence after every accepted or conflicted action; update the queue and customer notification state.
8. Notes are append-only audit evidence while the review/payment are eligible. A does not expose an invented separate editable note table or GET-notes collection; use current audit evidence for history.

Payment transitions remain: approval → RELEASED; rejection → CANCELLED; reverification request → VERIFICATION_REQUIRED. A later successful OTP can open the next review round; the old review must not be overwritten. SYSTEM_ADMIN alone never grants these actions.

### 10.3 AUDITOR: evidence and reconciliation

Create a coherent workspace with evidence tabs and reusable filter/detail panels:

| Tab | Read capabilities and display limits |
|---|---|
| Audit events | Global audit filters and transaction timeline; safe details only, no raw credentials or unsanitized payload dumps |
| Transactions | Staff transaction list/detail with customer/state/date filtering; use auditor routes, not customer-owned detail route |
| Reviews | Review list/status and detail/history; no decision buttons |
| Ledger | Posting list/header/entries; exact amounts and debit/credit meaning, no editing |
| Ledger reconciliation | Existing posting/reconciliation filters and server-calculated results; do not calculate an authoritative result from a single page |
| Reservation reconciliation | `accountId`/reconciliation filters, stored/calculated reserved amount and difference; omit current/available balances as approved |
| Exceptions | Existing transaction/stage/status/date filters and detail; no retry/resolve endpoint invented |
| Risk policies | Version/status list/detail, readonly policy configuration; no policy editor or alternate risk rules |

Backend sources: `AuditController`, `AuditEvidenceController`, `ReportingReadRepository`, current audit/reporting services, `StaffReadAccess`, `dto/audit/*`, review DTOs, and final V8/V9 reconciliation/reporting views. The historical `MAKER_CHECKER_APPROVAL` name was renamed by V9 to `RISK_REVIEW`; use the final schema.

### 10.4 Exit gate

- All three role workspaces work under their own tokens; wrong-role and manually entered route/API attempts are rejected appropriately.
- Account list/detail masking and monetary visibility match exact approved DTOs. Auditor reservation amounts do not become a balance-browsing permission.
- Admin changes invalidate sessions as A specifies; UI handles its own loss of access without inventing restrictions.
- Priority ordering, pagination and legacy null placement are demonstrated with multiple pages/category combinations where available.
- Customer cancel vs officer decision, two officers deciding, and duplicate note/decision retries produce one authoritative outcome and consistent UI.
- End-to-end VERY_HIGH: customer create/authorize → OTP → officer review → approval/rejection/reverification → customer refresh/notification and correct eventual state.
- Audit/ledger/reservation/exception/policy screens use actual safe projections. Empty or unavailable evidence is not represented as a successful reconciliation.
- User-run read-only checks confirm `RISK_REVIEW`, `AUDIT_LOG`, payment/reservation and applicable session/role changes. Notes are checked in their audit evidence, not an invented `RISK_REVIEW_NOTE` table.

## 11. Phase 5 — full integration, resilience, accessibility and handover

**Outcome:** a user-verified prototype with coherent behavior across frontend, A services and the existing Oracle database; remaining work is explicitly classified.

### 11.1 Work sequence

1. Close unresolved Phase 1–4 verification failures with approved, narrowly scoped patches; do not broaden into a backend refactor.
2. Run the authorized frontend type/build and focused regression checks, then browser flows against the approved local environment.
3. Execute cross-role financial scenarios and read-only persistence checks with distinct personas and recorded IDs/correlation references.
4. Exercise reload, slow network, lost mutation response, reconnect, expired session, multi-tab rotation and stale filtered data.
5. Inspect actual light/dark layouts, mobile/desktop, keyboard, screen reader labels, reflow, long names/IDs, precision, focus and reduced motion.
6. Finish static installability/offline presentation if included in the approved execution scope. Any service worker caches only approved static shell resources; it must not cache authenticated API/balance/OTP responses or queue/replay financial operations offline. Show a clear online-required state. Do not force an update reload during an unresolved payment.
7. Verify real serving origin and HTTPS/cookies/CSP when a deployment target is approved. Local success is not production deployment proof.
8. Present the exact passed/failed/blocked matrix, known prototype limitations and remaining deferred list to the user. Obtain acceptance before calling integration complete.

### 11.2 Required scenario matrix

| Group | Required checks | Expected invariant |
|---|---|---|
| Identity | Registration, duplicate contact, mandatory UI contacts, Unicode password byte boundary, login, logout, refresh, revocation | Correct identity/session, no secret leak or fictional account |
| Role/ownership | Four single-role personas, another customer, multi-role session if configured, direct forbidden URL/API | No privilege inflation or cross-customer data disclosure |
| Accounts | No accounts, multiple accounts, inactive status, stale/error balance, rapid selection changes | No wrong-account display or invented zero |
| Beneficiaries | Both methods, invalid/mismatched fields, duplicate, disable/reactivate, stale Pay link | Customer ownership and active eligibility enforced |
| Amount boundaries | 0, 0.99, 1, 5,000, 5,000.01, 25,000, 25,000.01, 100,000, 100,000.01, excess precision | Exact validation/tier/category behavior; no binary rounding |
| Category | All five; Others blank/140/141; legacy null; lower payment omits category | Existing legacy data unchanged; correct server ordering |
| Funds | Available funds exactly sufficient/insufficient, concurrent reservations, stale displayed balance | Server result wins, no overspend/retained-minimum invention |
| Idempotency | Double click, same key/payload, changed payload/key conflict, unknown response, reload policy, retention expiry | One logical operation; no silent replacement payment |
| Protection | Timely cancel, expiry boundary, cancel vs release, suspended tab/clock skew | Only backend changes state; correct reservation |
| OTP | Wrong/expired/exhausted code, cooldown, issue limit, delivery failure, lost response, reverification | Correct attempts/challenge cycle; no OTP storage/leak |
| Officer | Priority/paging/null category, approval/rejection/reverification/note, simultaneous decisions | Correct review round and payment state |
| Settlement | LOW release, protected release, approved VERY_HIGH release, eventual settlement, existing failure/manual-review evidence | No SETTLED UI before A; exactly-once balanced posting |
| Notifications | Feed/page/read, duplicate advisory event, reconnect, forbidden subscription | REST state and ownership preserved |
| Admin/auditor | Every inventory route, mask/balance permissions, status/role/session consequences, reconciliation | Safe role-specific projections and no extra mutation controls |
| UX | Both themes, small viewport, keyboard/zoom/focus, reduced motion, long text, error/retry states | Readable, operable and consistent interface |
| Offline/update | Offline before request vs network loss after request, return online, shell update | No offline payment queue; unknown outcome remains unknown until reconciled |

Do not intentionally corrupt Oracle tables, disable constraints, trigger real infrastructure failures, or reset seed data to manufacture these scenarios. Some failures can be covered by focused frontend mocks and existing backend tests; live fault injection requires separate approval. Mark that evidence distinction.

### 11.3 What to record at every gate

| Field | Required content |
|---|---|
| Scenario | Concrete action and role, not “tested dashboard” |
| Precondition | Actual source/beneficiary/user/review state and enabled processors relevant to the case |
| Request | Method, path, safe exact parameter names, correlation/key reference without secrets |
| API result | Actual status, safe body/state, replay indication where relevant |
| UI result | What the user saw and what action remained possible |
| Persistence result | Relevant record IDs, state, balances/reservation/posting invariants from authorized read-only verification |
| Outcome | PASS / FAIL / BLOCKED / NOT RUN; never combine unexecuted cases with passed cases |
| Follow-up | Exact affected file/logic if a fix is needed, with approval scope |

Frontend tests should focus on behavior that can cause integration defects: transport/precision, auth refresh, DTO mapping, state actions, category threshold, idempotent recovery and race handling. Do not add tests merely mirroring static CSS. Keep test explanations short, as requested; document interesting traps such as “2xx with FAILED state” and “create response lost after commit.”

Maven remains user-run when regression is needed. Do not promise 1,148 will be the future count if approved backend tests change. Provide individual/combined/full-suite commands and expected counts only from the actual implemented test inventory at that time.

## 12. API coverage inventory — no page capability left implicit

All paths below are relative to `/api/v1`. ID placeholders retain their distinct meaning. This inventory records current source; each implementation phase must re-read its controller/DTO for exact optional fields, errors and annotations.

### 12.1 Authentication and customer APIs

| Method/path | Role | Frontend owner / phase |
|---|---|---|
| `POST /auth/register` | Public | Registration, Phase 1 |
| `POST /auth/login` | Public | Login, Phase 1 |
| `GET /auth/csrf` | Public endpoint; cookie protection workflow | Session service, Phase 1 |
| `POST /auth/refresh` | Refresh cookie + origin + CSRF | Session service, Phase 1 |
| `POST /auth/logout` | Authenticated + origin + CSRF | Shell/session service, Phase 1 |
| `GET /users/me` | CUSTOMER | Read-only profile, Phase 2 |
| `GET /accounts` | CUSTOMER | Account selector, Phase 2 |
| `GET /accounts/{accountId}` | CUSTOMER, owned | Account detail, Phase 2 |
| `GET /accounts/{accountId}/balance` | CUSTOMER, owned | Balance panel, Phase 2 |
| `GET /beneficiaries` | CUSTOMER | Beneficiaries, Phase 2 |
| `POST /beneficiaries` | CUSTOMER | Add beneficiary, Phase 2 |
| `GET /beneficiaries/{beneficiaryId}` | CUSTOMER, owned | Detail/Pay validation, Phase 2 |
| `PATCH /beneficiaries/{beneficiaryId}/status` | CUSTOMER, owned | Status confirmation, Phase 2 |
| `GET /transactions` | CUSTOMER, owned query | Activity starts Phase 2; full history Phase 3 |
| `POST /transactions` | CUSTOMER | Create instruction, Phase 3 |
| `GET /transactions/{transactionId}` | CUSTOMER, owned | Detail/status/recovery, Phase 3 |
| `POST /transactions/{transactionId}/authorize` | CUSTOMER, owned | Explicit authorization, Phase 3 |
| `POST /transactions/{transactionId}/cancel` | CUSTOMER, owned and eligible | Undo/cancel, Phase 3 |
| `GET /transactions/{transactionId}/risk-explanation` | CUSTOMER, owned | Explain protection, Phase 3 |
| `POST /transactions/{transactionId}/otp` | CUSTOMER, owned and eligible | Issue OTP, Phase 3 |
| `POST /transactions/{transactionId}/otp/resend` | CUSTOMER, owned and eligible | Resend OTP, Phase 3 |
| `POST /transactions/{transactionId}/otp/verify` | CUSTOMER, owned and eligible | Verify OTP, Phase 3 |
| `GET /notifications` | CUSTOMER | Notification feed, Phase 3 |
| `PATCH /notifications/{notificationId}/read` | CUSTOMER, owned | Read marker, Phase 3 |
| `GET /transactions/{transactionId}/audit` | Owned CUSTOMER, or RISK_OFFICER/AUDITOR according to A | Timeline, Phases 3–4; SYSTEM_ADMIN alone is insufficient |

### 12.2 SYSTEM_ADMIN APIs

| Method/path | Filters/body / frontend purpose |
|---|---|
| `GET /admin/accounts` | `customerId`, `minCurrentBalance`, `accountType`, `page`, `size` |
| `GET /admin/accounts/{accountId}` | Exact complete admin account DTO |
| `GET /admin/accounts/{accountId}/balance` | Focused balance response |
| `GET /admin/users` | `q`, `role`, `status`, `page`, `size` |
| `GET /admin/users/{userId}` | User/security detail |
| `PATCH /admin/users/{userId}/status` | Body contains `status`, one of `ACTIVE`, `LOCKED`, `DISABLED`; for example `{"status":"LOCKED"}` |
| `PUT /admin/users/{userId}/roles/{roleCode}` | Assign one exact role |
| `DELETE /admin/users/{userId}/roles/{roleCode}` | Remove one exact role, not delete user |
| `POST /admin/users/{userId}/sessions/revoke` | Revoke target sessions |
| `GET /admin/operations/stats` | Snapshot counts and backlog, not daily reporting or health |
| `GET /admin/operations/failures` | `source`, `transactionId`, `from`, `to`, `page`, `size` |

All are Phase 4; none justify additional user/account/financial mutation routes.

### 12.3 RISK_OFFICER APIs

| Method/path | Filters/body / frontend purpose |
|---|---|
| `GET /admin/risk-reviews` | `category`, `sort` (`PRIORITY` or `OLDEST`), `page`, `size`; pending queue |
| `GET /admin/risk-reviews/{reviewId}` | Nested review detail |
| `POST /admin/risk-reviews/{reviewId}/approve` | Optional decision reason per A; own idempotency key |
| `POST /admin/risk-reviews/{reviewId}/reject` | Required `reason`; own key |
| `POST /admin/risk-reviews/{reviewId}/request-verification` | Required `reason`; own key |
| `POST /admin/risk-reviews/{reviewId}/notes` | Required `note`; own key; created note evidence response |

All are Phase 4. There is no general staff semantic search, forced release, review edit, or account-balance browsing under this role alone.

### 12.4 AUDITOR APIs

| Method/path | Filters / purpose |
|---|---|
| `GET /audit-logs` | `transactionId`, `actionCode`, `outcome`, `actorType`, `correlationId`, `from`, `to`, `page`, `size` |
| `GET /audit/transactions` | `customerId`, `state`, `from`, `to`, `page`, `size` |
| `GET /audit/transactions/{transactionId}` | Safe staff transaction detail |
| `GET /audit/risk-reviews` | `status` (default PENDING), `page`, `size` |
| `GET /audit/risk-reviews/{reviewId}` | Review evidence detail |
| `GET /audit/ledger-postings` | `transactionId`, `status`, `from`, `to`, `page`, `size` |
| `GET /audit/ledger-postings/{postingId}` | Header and entries |
| `GET /audit/reconciliation/ledger` | `postingId`, `reconciliationStatus`, `page`, `size` |
| `GET /audit/reconciliation/reservations` | `accountId`, `reconciliationStatus`, `page`, `size` |
| `GET /audit/exceptions` | `transactionId`, `processingStage`, `status`, `from`, `to`, `page`, `size` |
| `GET /audit/exceptions/{exceptionId}` | Exception detail |
| `GET /audit/risk-policies` | `status`, `page`, `size` |
| `GET /audit/risk-policies/{policyVersion}` | Policy detail; version identifier, not an invented numeric policy route |

All are Phase 4 and read-only. The shared transaction-audit route is listed in 12.1. Match actual accepted enum/string values from A when building each filter; do not copy B statuses.

## 13. Database verification without changing the schema

Frontend integration uses REST, never JDBC, owner credentials or browser-to-Oracle access. Read-only SQL verification is a separate user-controlled check in the appropriate SQL Developer connection. No database execution occurred while writing this guide.

| Feature | Canonical database objects to inspect | What should be proved |
|---|---|---|
| Registration/authentication | `APP_USER`, `APP_ROLE`, `USER_ROLE`, `AUTH_SESSION`, safe `AUDIT_LOG` evidence | Correct identity/role/session lifecycle; no account auto-provisioning; never expose password/session hashes |
| Accounts/beneficiaries | `ACCOUNT`, `BENEFICIARY` | Correct ownership/status/method fields; no account mutation from ordinary reads |
| Create/authorize | `PAYMENT_TRANSACTION`, `ACCOUNT`, `TRANSACTION_RISK_FACTOR`, `IDEMPOTENCY_RECORD` | One instruction; exact amount/category; correct reservation and policy evidence; operation replay |
| OTP/reviews | `PAYMENT_OTP_CHALLENGE`, `RISK_REVIEW`, `AUDIT_LOG` | Challenge status/attempt metadata and review round/state; no OTP material inspection |
| Cancellation/release | `PAYMENT_TRANSACTION`, `ACCOUNT`, related audit/notification rows | Accepted state transition and reservation accounting; no ledger settlement on cancellation |
| Settlement | `LEDGER_POSTING`, `LEDGER_ENTRY`, `PAYMENT_TRANSACTION`, `ACCOUNT` | One committed balanced posting for a settled payment; correct internal-clearing simulation and reservation release |
| Failures/notifications | `TRANSACTION_EXCEPTION`, `APP_NOTIFICATION`, `AUDIT_LOG` | Safe failure/delivery/read status and expected lifecycle evidence |
| Auditor results | Final `VW_LEDGER_RECONCILIATION`, `VW_RESERVATION_RECONCILIATION` and reporting queries | API figures reconcile with current canonical query/view semantics |

Use returned IDs to inspect narrow rows, with parameterized/read-only queries prepared from the **current final DDL/columns** during the relevant phase. Do not paste old `MAKER_CHECKER_APPROVAL` queries or invent a separate reservation/note table. V9 renamed the review object; V13 added nullable `payment_category` on `PAYMENT_TRANSACTION`.

Record a before snapshot and an after snapshot for mutating scenarios. Exact balance deltas depend on the scenario and any concurrent processor work; refresh related rows consistently. A read can write security/read-audit evidence, so “read-only screen” means no business mutation, not necessarily zero new audit rows.

Existing V12 scenario rows are mutable through legitimate API operations. Inspect state before reuse; do not assume the seed's original counts or payment states persist. If fresh fixtures are required, obtain scoped approval for their creation through appropriate existing APIs/procedures; do not reset the whole showcase database.

## 14. Scope control, acceptance and continuation protocol

### 14.1 Before each approved phase

1. Read this contract and the latest user decisions.
2. Re-open only the relevant A controllers/DTOs/services and B/C reference files.
3. State the exact frontend files to create/adapt, the APIs being integrated, and any approved backend exception. Identify dependencies on unresolved gates.
4. Confirm execution permissions already granted for that phase; do not ask again for already authorized scoped work, and do not stretch it into unrelated work.
5. Implement with existing exact names/contracts, then report the changed scope and the checks actually performed.

### 14.2 Phase completion rule

A phase can be marked **implemented, verification pending** after its approved edits. Mark it **accepted/locked** only after its required checks pass and the user confirms the checkpoint. Dependent UI scaffolding may proceed only if explicitly approved; unresolved financial/security behavior must remain visibly blocked.

Each handoff includes:

- Added/adapted files and reused components.
- Actual API calls integrated and role/ownership behavior.
- Tests/build/manual cases executed by whom, with exact results.
- Database checks performed or still pending.
- Known limitations, unresolved decisions and next bounded phase.

No “working” claim based solely on static code inspection, no screenshot presented as proof of database consistency, and no automatic backend repair to force a demonstration.

### 14.3 Phase status at contract creation

| Phase | Status | Exit evidence required |
|---|---|---|
| 0: checkpoint | Superseded by approved 0A/0B/0C split; implementation prepared, user verification pending (section 18) | Required focused tests and setup checks |
| 1: foundation/auth | Not started | Real auth/session + UI/precision checks |
| 2: customer views | Not started | Account/balance/profile/beneficiary API and ownership checks |
| 3: payments/OTP | Not started | Customer lifecycle, precision, recovery and relevant persistence |
| 4: staff workspaces | Not started | Full role inventory and customer/officer/audit loop |
| 5: acceptance | Not started | Cross-role live scenarios, resilience, visual/accessibility checks and user acceptance |

## 15. Deferred work — mandatory post-integration reminder

This is a review list, not permission to implement these features automatically after Phase 5. At the final acceptance handoff, remind the user of the list and request their next priority.

| Deferred item | Agreed future treatment / decision still needed |
|---|---|
| Customer profile editing | After integration, revisit a narrow non-password profile-edit flow. Resolve contact uniqueness, verification, login/session consequences, DTO validation and authorized API changes before implementation. |
| Daily reporting inspired by B | Future complete backend + frontend feature adapted to A. Decide role permissions, reporting timezone, created-day versus settled-day meaning, outcome counting and aggregation. Isolated design placeholders may be prepared, but no live fake data or nonexistent API calls. |
| OpenAPI delivery | Complete accurate API documentation from the verified backend and integrated contracts at the final documentation stage; approve dependencies/configuration separately. |
| Master backend understanding guide | Resume after paused Phase 2.1 with incremental phase chapters through 2.12, including later change-request additions in the appropriate conceptual chapters. Keep tests to brief summaries, with explanation only for important traps. |
| Separate phase API-test guides | Produce each phase's guide alongside its master chapter, not all at the end. Include new endpoints in logical phase order. Manual API evidence gathered here helps but does not substitute for those guides. |
| Database understanding guide | Explain current V1–V13/final canonical objects, constraints, mappings and decisions; distinguish immutable migrations and repeatable grants, plus seeded versus runtime data. |
| Team presentation handover | After working verified integration, divide ownership among Aditya, Ruchi, Shreya and Gaurav, with feature flows from UI → service → controller → backend → database and reasons for design choices. |
| Account disable/reactivate and branch filtering | Previously excluded from selected implementation; no controls/routes now. Revisit only if the user requests a fresh scope and backend contract. |
| Password change/reset, support-email form, broader onboarding | Not part of this prototype integration. Require a later explicit feature/security decision; prepared accounts remain the immediate model. |

The form-only registration contact/strength enforcement remains a **known prototype limitation**, not a claim of production-grade server enforcement. Display it in the acceptance limitations; do not silently turn it into a backend-hardening task.

Countdown G3, precision/tooling G2, and session/recovery G4/G5 are **implementation gates**, not features to quietly defer beyond integration while claiming their behavior is complete.

## 16. Explicitly discarded or prohibited — not a pending feature list

- Oracle 23AI vector search, embeddings, semantic search, search deployment or prototype evaluation: removed entirely.
- New SYSTEM_ADMIN health capability/endpoint/DTO; invented scheduler heartbeat/liveness.
- Importing B's users/accounts/payments or using B's database as A's financial source.
- Direct administrator balance overwrite, account deletion, fake manual funding, or browser-controlled release/settlement.
- B's retained-minimum ₹5,000 rule, `ADMIN` authority, `HARD_HOLD` transaction enum, or payment `REJECTED` enum.
- Mandatory category for payments at or below ₹1,00,000; rewriting legacy categories/purpose; editing applied V1–V13.
- C functionality/backend contracts; sample PIN/KYC/DOB/address flows; visible demo credentials.
- Browser-to-database access, owner secrets in frontend, persistent access tokens, stored OTPs, or financial offline replay.
- Reintroducing device/location/AI/multi-signal risk behavior from historical material without a completely separate user request.
- Full bank-statement/incoming-credit activity, which the user explicitly does not require now.

## 17. Source pointers and revalidation checklist

The following are actual inspected source locations. They anchor future work; this document's proposed frontend files are not yet present.

- [Updated Decision Register](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/Documentation for SafePay/UPDATED_Decision_Register.md>)
- [Backend context and frozen checkpoints](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/SafePay_Backend_DB_Context.md>)
- [Backend change-request guide](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/change_request_implementation_guide.md>)
- [Original master-guide contract](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/dbsetup_cum_backend_master_guide_contract.md>)
- [Goals and later handover](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/GOALS_FOR_TODAY.txt>)
- [REST controllers](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/controller>)
- [DTO definitions](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto>)
- [Security configuration](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/security/SecurityConfig.java>)
- [WebSocket security](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/security/StompSecurityChannelInterceptor.java>)
- [Transaction orchestration](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/TransactionServiceImpl.java>)
- [Payment request validation](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/dto/transaction/CreateTransactionRequest.java>)
- [Payment category rules](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/beans/PaymentCategory.java>)
- [Canonical payment states](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/beans/TransactionState.java>)
- [Risk-review service](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/services/RiskReviewServiceImpl.java>)
- [Reporting repository](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/repository/ReportingReadRepository.java>)
- [Exception/ProblemDetail handling](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/java/com/ofss/excp/GlobalExceptionHandler.java>)
- [Backend runtime configuration](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/application.properties>)
- [Immutable migration directory](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/db/migration>) and [V12 showcase location](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend/src/main/resources/db/showcase>)
- [B package baseline](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Frontend/SafePayJet/package.json>) and [B MVVM/tooling configuration](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay-ruchi-frontend/Frontend/SafePayJet/oraclejetconfig.json>)
- [C visual-only CSS](<C:/Users/Aditya Rao/Downloads/Training/Project/SafePay_Frontend_for_Codex/Frontend/SafePayJet/src/css>)

Before each phase, verify these sources have not changed in ways that affect its exact contract. If code and a historical description conflict, record the discrepancy and obtain approval for any consequential change; do not silently pick whichever makes frontend work easier.

**Current authorization:** the user accepted the 43-test backend checkpoint and approved implementation of Phases 1–3. The later section 19 supersedes the original Phase 0 stop below.

## 18. Operative Phase 0 amendment, implementation and user-run checks

This section records the latest user approval and supersedes older unselected alternatives above. The user approved server-aligned countdown, per-tab sessionStorage recovery, current Chrome/Edge first, and preparing dependencies/setup files for **manual user execution**. The user explicitly split the current work into 0A, 0B and 0C and required a stop afterwards.

No package installation, application startup, compilation, Maven/frontend test, SQL command or migration was executed by Codex. Only source review and configuration-file readability checks were performed. Test counts below are expected from the present test inventory, not claimed passing results.

### 18.1 Implemented scope and boundaries

| Phase | Prepared implementation | Still needs verification |
|---|---|---|
| 0A | Transaction-detail GET adds `serverTime`, optional `protectionRemainingMillis`, and `canCancel`; one existing database-time query after ownership lookup | 43 focused backend tests; optional live read check below |
| 0B | `src/ts/services/pendingPayment.ts`: exact, validated, identity-scoped per-tab recovery store; 12 critical tests | Compile/test module, manual same-tab reload check after 0C setup |
| 0C | Local JET/TypeScript configuration, dependency pins, RequireJS mappings, a minimal setup page and 3 dependency smoke checks | User installation, typecheck, build, smoke checks and browser startup |

Phase 0A changes only these production files:

- `backend/src/main/java/com/ofss/dto/transaction/TransactionResponse.java`.
- `backend/src/main/java/com/ofss/services/TransactionServiceImpl.java` (`getTransaction`).

Related tests are updated in the existing `TransactionResponseTest`, `TransactionQueryServiceTest`, and `TransactionControllerTest` files. No new backend endpoint/controller, schema, migration, grants, application properties, dependency, financial state transition, or balance operation was added.

The DTO retains both previous constructor signatures. The three new optional values are omitted from mutation responses and old cached replay responses. Only `getTransaction` enriches the DTO with a database-time observation. Existing category serialization remains intact.

| Detail GET state | `serverTime` | `protectionRemainingMillis` | `canCancel` |
|---|---|---|---|
| CREATED | Current database observation | Omitted | true |
| PROTECTED before deadline | Current database observation | Non-negative whole milliseconds until deadline | true |
| PROTECTED at/after deadline | Current database observation | 0 | false |
| PROTECTED with missing deadline | Current database observation | Omitted | false; fails closed |
| VERIFICATION_REQUIRED / PENDING_RISK_REVIEW | Current database observation | Omitted; no auto-release timer | true |
| AUTHORIZED / RISK_ASSESSED / RELEASED / SETTLED / CANCELLED / FAILED | Current database observation | Omitted | false |

These are **snapshot hints**, not guarantees that a later cancel wins a race. Cancellation still rechecks the actual state/deadline. A released row may retain its historic `protectedUntil`, but this does not create a new running countdown.

0B stores one unresolved mutation per tab at `safepay.pending-payment.v1`. Supported persisted operations are CREATE, AUTHORIZE, CANCEL, OTP_ISSUE and OTP_RESEND. OTP_VERIFY is deliberately excluded because persisting its exact replay payload would persist the OTP. Phase 3 will retain that sensitive attempt only in memory and reconcile transaction state after a reload; it must not silently issue another verification attempt.

Stored fields are version, user ID, UUID idempotency key, creation/expiry times and the permitted operation payload. Create payload uses exact string IDs/amount, plus optional original purpose/reference/category. No password, access/refresh token, OTP or arbitrary extra field is accepted. All returned snapshots are frozen copies. A frontend API adapter must later serialize only the operation's payload with the lossless wire codec; **do not send the recovery envelope or blindly stringify the stored monetary strings as the final API contract**.

Retention is 11 hours, shorter than A's configured 12-hour replay window, and never extended on reload. At expiry, the module blocks replay/replacement until explicit reconciliation rather than silently starting a duplicate. The expired record remains a blocker until cleared after review/logout. Corrupt/unreadable storage also blocks submission; no financial request is made by this module. Identity change clears the previous user's record, and logout cleanup must warn about unresolved outcomes before discarding metadata.

The module does not authenticate, submit requests, render a payment page or claim cross-tab payment orchestration. `sessionStorage` supports same-tab reloads; it is not permanent cross-device recovery. Cross-tab refresh coordination remains Phase 1. Countdown rendering and real payment recovery wiring remain Phase 3.

### 18.2 Prepared frontend structure

Root: `C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/frontend/SafePayJet`

```text
SafePayJet/
  .gitignore
  package.json
  oraclejetconfig.json
  tsconfig.json
  src/
    index.html
    css/app.css
    js/main.js
    js/path_mapping.json
    ts/root.ts
    ts/services/pendingPayment.ts
  tests/
    pendingPayment.test.cjs
    setup.test.cjs
```

Only the required setup/module/test structure is created now. View models, customer pages and shared design components will be created as needed in Phases 1–3. No empty future-page files or alternate framework have been introduced.

`node_modules`, `package-lock.json`, `.test-build`, `web` and any JET staging output are generated by the user-run commands below. No lockfile was fabricated without resolving dependencies: the first successful install creates the real lockfile, which should then be retained for reproducibility. Do not use `npm ci` before that initial lockfile exists.

Foundation pins: JET/core-pack 21.0.0, CLI/tooling 21.0.1, TypeScript 5.8.3. Additional pins: lossless-json 4.3.1, STOMP.js 7.3.0 and Manrope package 5.3.0. Manrope is installed locally with its license; actual themed font usage is Phase 1. The setup screen intentionally uses a system font and is not a completed design-system page.

The numeric/STOMP entry points were checked against their publisher manifests: [lossless-json manifest](https://raw.githubusercontent.com/josdejong/lossless-json/main/package.json), [STOMP.js 7.3.0 manifest](https://raw.githubusercontent.com/stomp-js/stompjs/v7.3.0/package.json). Oracle documents [server-only serving](https://docs.oracle.com/en/middleware/developer-tools/jet/19/develop/understand-web-application-workflow.html), which avoids an automatic browser launch. [Fontsource Manrope](https://fontsource.org/fonts/manrope) supplies the font and license. These references do not substitute for installation/build verification in this workspace.

### 18.3 Phase 0A — minimal Maven verification, successful path first

In Eclipse, use the existing backend Maven Build configuration whose base directory is:

`C:/Users/Aditya Rao/Downloads/Training/Project/SafePay/backend`

Retain the last working test environment. Do not change database credentials, Flyway locations/target, Java installation or mail settings for this small patch. No migration is required. Enter each line below in the **Goals** field (without a leading `mvn`).

| Order | Maven Goals | Expected result |
|---|---|---|
| 1 | `-Dtest=TransactionResponseTest test` | Tests run: **10**, failures/errors/skipped: **0**, BUILD SUCCESS |
| 2 | `-Dtest=TransactionQueryServiceTest test` | Tests run: **11**, failures/errors/skipped: **0**, BUILD SUCCESS |
| 3 | `-Dtest=TransactionControllerTest test` | Tests run: **22**, failures/errors/skipped: **0**, BUILD SUCCESS |
| 4 | `-Dtest=TransactionResponseTest,TransactionQueryServiceTest,TransactionControllerTest test` | Tests run: **43**, failures/errors/skipped: **0**, BUILD SUCCESS |

These cover deadline boundaries, missing deadline, every canonical cancellation state, database rather than application time, ownership failure before time lookup, GET JSON fields, category preservation and old cached/mutation response compatibility. Only four test methods were added overall; existing relevant tests were extended. A full-suite rerun is not the mandatory gate for this isolated change. If separately requested, its baseline-derived expectation is 1,152 tests, subject to any other intervening changes; this is not a result claimed here.

Optional live read verification, using Postman and the normal existing application launch:

1. Start/restart the backend yourself through the existing approved V13 application configuration so it loads the changed classes. Do not create a new database or rerun SQL manually.
2. Sign in using the existing CUSTOMER login request. Keep the bearer token local.
3. Send `GET http://localhost:8080/api/v1/transactions?page=0&size=20` with that token and choose a transaction ID from its own results. If your backend uses another configured port, use that existing port.
4. Send `GET http://localhost:8080/api/v1/transactions/{actualTransactionId}`. Expect 200 and a current `serverTime`; compare the other fields against the state table in 18.1.
5. For an existing PROTECTED row, remaining milliseconds must be `max(0, floor(protectedUntil - serverTime))`. An expired seed row legitimately returns 0/false; do not reseed it to force an active timer.
6. Repeat GET. Time is freshly observed. A nonterminal transaction can change state between reads if a processor is enabled; this is not a countdown defect.

No direct database query is necessary for 0A: the existing DAO time query and focused service test establish the source, while the optional real GET proves the deployed path. Live active-window rendering/cancel tests belong to Phase 3.

### 18.4 Phase 0C — installation and startup, successful path

0B's code is prepared before 0C, but its executable checks require TypeScript installed by 0C. The practical verification order is **0A → 0C installation → 0B tests → 0C tests/build/browser**.

Open a normal PowerShell terminal. Run commands one at a time; do not proceed past a failure.

**1. Enter A's new frontend folder.**

```powershell
Set-Location -LiteralPath 'C:\Users\Aditya Rao\Downloads\Training\Project\SafePay\frontend\SafePayJet'
node --version
npm.cmd --version
```

Expected: an existing supported Node version (the inspected executable reports 24.21.0) and npm (inspected package version 11.19.0). No global installation or PATH modification is needed on the inspected machine. `npm.cmd` avoids PowerShell script-policy problems with `npm.ps1`.

**2. Install only this project's dependencies and generate its real lockfile.**

```powershell
npm.cmd install --no-fund
```

Expected: installation finishes without `npm ERR!`; `node_modules` and `package-lock.json` appear in this frontend root. Package counts and audit/deprecation notices vary and are not test counts. Preserve the lockfile after successful verification; later identical clean installs can use `npm.cmd ci --no-fund`.

**3. Check application TypeScript.**

```powershell
npm.cmd run typecheck
```

Expected: exit code 0 and no TypeScript error. No browser/backend launch occurs.

**4. Run the Phase 0B recovery checks, then the Phase 0C setup checks.**

```powershell
npm.cmd run test:recovery
npm.cmd run test:setup
```

Expected: recovery reports **12 tests, 12 pass, 0 fail**, and setup reports **3 tests, 3 pass, 0 fail**. These use Node's built-in test runner, not a heavy frontend test framework. The recovery tests use an in-memory storage double and do not call the backend or Oracle. Setup checks inspect installed dependencies and numerical round trips without opening a socket.

**5. Combined frontend check, then build.**

```powershell
npm.cmd test
npm.cmd run build
```

Expected: `npm test` runs the **12 + 3** groups successfully (15 total, displayed in two summaries). The JET build completes successfully and creates `web/index.html`, compiled modules and local library assets. Do not expect a Maven-style BUILD SUCCESS message from npm itself; use the command's successful exit and absence of build errors.

**6. Serve the setup screen.**

```powershell
npm.cmd run serve
```

Leave that terminal open. Open Chrome or Edge yourself at `http://localhost:8000`. Expected heading: **Phase 0 setup**. Expected status after module loading:

> Ready: Oracle JET, exact-number JSON and STOMP modules loaded.

This is a setup-only screen. It has no login, financial controls, backend requests or database operations. It intentionally does not claim Phase 1 completion. Stop the local frontend server with **Ctrl+C** when finished.

### 18.5 Phase 0B — recovery verification deferred by the user

The standalone Developer Tools synthetic exercise is **pending, not passed**. The user explicitly removed it as a blocker for Phases 1–3 on 20 September 2026. The attempted agent browser check could not attach and created no synthetic data.

Verify reload recovery through actual payment screens during combined integration instead. Future instructions must be provided directly in chat, with explicit clicks, commands and expected outcomes. Do not ask the user to create or execute unexplained browser-console scripts. Normal page refresh after a confirmed payment is useful but does not prove lost-response replay; that remaining scenario requires a controlled real-workflow check before final acceptance.

### 18.6 Combined acceptance evidence and stop

Send the following results, keeping credentials/tokens out of screenshots/logs:

1. Maven combined result: 43 tests, zero failures/errors/skips.
2. `typecheck` and JET `build` outcomes.
3. Frontend recovery/setup results: 12 + 3 passing.
4. Readiness page screenshot and same-tab reload result with the same UUID.
5. Optional live GET response: state and the three presentation fields, with identifiers masked if desired.

This locks **only Phase 0A/0B/0C** after successful user verification. No Phase 1 login/design work or Phase 2/3 customer/payment implementation proceeds before that checkpoint. Do not mark the 1,148 historical backend baseline or the new expected counts as newly passed without actual results.

### 18.7 Troubleshooting — use only after the normal steps above

| Symptom | Required next action / bounded fix |
|---|---|
| Maven fails | Stop and share the first failure/root cause and summary. These three tests do not require a new Oracle environment setup. Do not alter migrations, weaken assertions or run Flyway repair. |
| Maven test count differs | Confirm exact Goals and base directory, refresh Eclipse's project files, and share the actual count. Do not assume a different total is success for this gate. |
| GET lacks `serverTime` | Confirm the backend was rebuilt/restarted, the port targets A, and the route is detail GET rather than list/create/authorize. Old cached mutation responses intentionally omit it. |
| PROTECTED returns 0/false | Compare server time with the deadline; an expired existing row is expected. Refresh state; do not change browser time or reset seed data. |
| GET returns 401/403/404 | Re-login if expired; use a CUSTOMER token and an ID owned by it. Do not grant extra roles to bypass ownership. |
| Node/npm not found | Check the already installed executable location (`C:\Program Files\nodejs`). Use that existing installation or ask for help; do not install globally or edit PATH silently. |
| PowerShell blocks `npm.ps1` | Use the provided `npm.cmd` commands; do not change execution policy. |
| npm proxy/certificate/network/ETARGET error | Stop and share the package name and exact error, excluding secrets. Do not disable TLS, switch to an untrusted registry, or substitute arbitrary versions. |
| npm audit/deprecation notices | Keep the summary for review. Do not run `npm audit fix --force` or mass-upgrade JET/transitive libraries. Notices alone are not test results. |
| `npm ci` complains about a missing lockfile | First installation is `npm.cmd install --no-fund`; `ci` is for the resulting synchronized lockfile. Do not invent or copy an unrelated lockfile. |
| `tsc`/`ojet` not recognized | Complete the local installation and invoke the `npm.cmd run ...` scripts from the frontend root. Do not install a global CLI. |
| TypeScript/setup test/build failure | Stop at the first error and supply its path/message. Resolve it in the related setup file; do not remove the precision/recovery check to get a green build. |
| Port 8000 is already in use | Stop your previously opened local frontend server using its own terminal's Ctrl+C and retry. Do not kill unrelated services. A different origin requires an explicit configuration decision before API integration. |
| Setup remains “Waiting…” or module 404 | Use the served localhost URL, not `file://`; inspect the first console/Network error. Re-run the build after fixing its source. Never hand-edit generated `web` assets as a lasting fix. |
| Recovery state is unavailable | Browser storage is blocked/full. Allow normal session storage for the local development site or use a normal supported browser profile. Do not fall back to submitting without saved recovery data. |
| Recovery state is invalid/expired | Do not create a replacement real payment. Reconcile with A first. For the synthetic Phase 0 console exercise only, the explicit cleanup step is safe because no request was sent. |
| Refresh loses the record | Keep the same tab and exact origin; inspect site storage settings. Closing a tab, changing port/profile or explicitly clearing storage is outside same-tab reload persistence. |

Troubleshooting does not authorize extra system changes or scope expansion. Report any consequential new decision before proceeding.

## 19. Operative Phase 1–3 checkpoint — 20 September 2026

### Authority and evidence

- The user confirmed successful individual Maven checks and supplied the combined **43 tests, 0 failures, 0 errors, 0 skipped** screenshot. Phase 0A is locked at that user-run checkpoint.
- The Phase 0 readiness-page screenshot confirms the initial browser modules loaded. Do not turn this into a claim that the later integrated pages were tested.
- The synthetic same-tab console check remains pending and moves into real payment recovery acceptance. This is not a passed check.
- The user authorized the complete Phase 1–3 bundle and requests direct chat-based, sequential verification instructions. No extra execution authorization is inferred.
- This increment changes frontend source, its focused test script/manifest scripts, and this operative contract only. No backend Java, Maven configuration, database object, migration, account balance, secret, package version, system setting, or B/C reference repository is changed.
- No compilation, npm/Maven tests, install, application startup, browser transaction or SQL operation was executed by the agent for this increment. Source review is not runtime acceptance.

### Implemented file responsibilities

| Phase | Implemented source groups under frontend/SafePayJet | Integration |
|---|---|---|
| 1 | root.ts, appController.ts, accUtils.ts; services/types, wireCodec, apiClient, apiError, authService, sessionRouteService; utils/money, format, theme; views and viewModels/home, about, login, register; CSS tokens/app/responsive; sp-status composite; entry/module/font mappings | Memory-only access token; HttpOnly cookie exchange; fresh CSRF; Web Locks serialize cookie rotation across current Chrome/Edge tabs; BroadcastChannel carries invalidation notices only; role-gated routes; exact JSON numbers; customer identity registration |
| 2 | services/accountService, profileService, beneficiaryService; dashboard, profile, beneficiaries views/view models; transaction list adapter reused with Phase 3 | Separate selected-account balance GET; source-account outgoing activity; server paging/state/date filters; read-only profile; BANK_ACCOUNT/UPI beneficiaries, detail and status PATCH |
| 3 | services/transactionService, otpService, notificationService, realtimeService, refreshLoop; existing pendingPayment store; utils/paymentState; send-money and transactions views/view models | Create then explicit authorize; exact high-value category/purpose rule; server-observed countdown; cancellation confirmation; same-key uncertain-outcome replay; OTP issue/resend/verify; risk explanation, paged audit, notifications/read marking and advisory STOMP |
| Critical checks | tests/contracts.test.cjs plus test:contracts script | 12 source-defined tests for exact wire numbers, monetary boundaries, request routing, authentication retry limits, unknown mutation handling and canonical states; these have not been executed by the agent |

Shared feedback, payment rows, filters and pagination are reused through named Knockout templates in src/index.html. Standard JET controls/dialogs and the small registered sp-status component provide common presentation without a second component framework.

### Important behavior and remaining acceptance

- Staff identities may authenticate, but staff workspaces are Phase 4; the UI grants no customer authority to staff.
- Backend A origin remains http://localhost:8080; the frontend remains http://localhost:8000. No proxy or backend configuration change is included.
- Local Manrope assets are copied from the already approved installed font package during the user-run JET build; there is no external font request or new download command.
- A successful create means CREATED only. Authorize, cancel and every OTP operation have separate keys. Unknown outcomes retain a frozen recovery record and block a replacement.
- OTP codes remain memory-only; current challenge retrieval is not invented. After reload, a customer may need an explicit replacement-code request, subject to A's cooldown/issue limits.
- A replayed challenge's original timestamp is combined with a fresh detail-GET server observation, so stale response data cannot restart an expired code timer.
- All 10 canonical transaction states have distinct presentation. A countdown reaching zero requests fresh detail; it never performs a release or settlement.
- Account/filter changes invalidate late reads. Serial refresh is stopped on page disposal. Old identity data is discarded on session change.
- Frontend mandatory contact/stronger passphrase rules remain form-only. The previously accepted permissive API/database contact behavior is unchanged.
- Expected focused npm inventory: existing recovery 12 + setup 3 + new contracts 12 = **27**, in three summaries. Counts are expectations, not reported results.
- User verification still covers TypeScript/build, both themes/mobile/keyboard use, live login/CSRF/refresh/logout, two-tab behavior, registration with no accounts, separate balances, both beneficiary methods, create/authorize/cancel, categories, OTP and actual recovery. Test instructions belong in the chat handoff.
- A normal refresh after a confirmed operation is not evidence of unknown-outcome replay. Keep the latter pending until a controlled scenario is verified.
- A disabled protection/settlement processor can leave a payment PROTECTED/RELEASED respectively. Do not alter stored data or mislabel such a state as frontend success.
- Phase 4 staff decisions and Phase 5 final acceptance remain unimplemented. Profile editing and daily reporting stay deferred; OpenAPI/master understanding/API-test guides remain at the final documentation stage. Vector functionality stays removed entirely.

**Stop:** do not start Phase 4, run commands beyond the standing permissions, or mark Phase 1–3 accepted until the user supplies verification and authorizes the next step.
