# CivicWorks Community Services — API Specification Plan (Planning)

## 1) API conventions

- Base path: `/api/v1`
- Content type: `application/json`
- Time format: ISO-8601 with offset; business-day rules computed in configured municipality timezone.
- Authentication: opaque Bearer token (local auth only) with server-side session validation.
- Authorization: RBAC with deny-by-default + method-level guards.
- Optimistic locking: `version` on mutable resources; stale updates return `409`.
- Error envelope:

```json
{
  "error": {
    "code": "DOMAIN_ERROR_CODE",
    "message": "Human-readable summary",
    "details": [{"field": "fieldName", "issue": "validation reason"}],
    "requestId": "uuid"
  }
}
```

## 1.0 Auth/session model (concrete)

- Token format: `cwk_sess_<base64url-random-256bit>`.
- Persistence: server stores only `SHA-256(token)` in `auth_sessions` with `issuedAt`, `expiresAt`, `revokedAt`, `lastSeenAt`.
- Session TTL: `system_config.sessionTtlMinutes`, default `480`, allowed `60..1440`.
- Expiry model: fixed TTL from login time (no refresh token in baseline).
- Revocation model: logout, admin revoke, or user disable/lock revokes immediately.
- Clock behavior: server clock authoritative; client clock skew does not affect acceptance.

Auth failure codes used by protected endpoints:

- `401 SESSION_INVALID`
- `401 SESSION_EXPIRED`
- `401 SESSION_REVOKED`

## 1.1 Idempotency contract

Required on:

- `POST /billing/runs`
- `POST /settlements/payments`

Header: `Idempotency-Key: <opaque-string>`

Rules:

- missing key -> `400 MISSING_IDEMPOTENCY_KEY`
- same key + same request hash -> replay prior response
- same key + different request hash -> `409 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`

## 1.2 Audit expectations (global)

Every state-changing endpoint must write `audit_log` with:

- actor id/role
- action code
- entity reference
- before/after summary (where relevant)
- timestamp, request id, outcome

Read-only endpoints for Auditor do not require per-row audit, but access to sensitive report endpoints should emit access audit entries.

## 2) Roles and permissions baseline

- `SYSTEM_ADMIN`: global configuration, user/role/status management, full audits, all module admin operations
- `CONTENT_EDITOR`: content/resource create/update/schedule/publish/unpublish
- `MODERATOR`: comment review actions, sensitive-word policy management
- `BILLING_CLERK`: fee setup, billing runs, payment posting/reversal workflow participation
- `DISPATCHER`: capacity rule management, assignment and forced dispatch operations
- `DRIVER`: read eligible orders, accept/decline own assignments with constraints
- `AUDITOR`: read-only finance/activity reporting

## 2.1 Static security-boundary inventory

- **Auth entry points**: `/auth/login` (public), `/auth/logout` (authenticated), `/auth/sessions*` (admin-only).
- **Public read surfaces**: `/public/content-items*`, `/public/search*` (published catalog only).
- **Controller boundary**: endpoint family RBAC is mandatory and deny-by-default.
- **Service boundary**: object-level checks are mandatory before mutation (for example, driver/order ownership, role-limited state transitions).
- **Admin-only surfaces**: system config, due-date policy mutation, session revocation, channel toggles, discrepancy final resolution.
- **Internal-only write surfaces**: scheduled job execution records, notification outbox generation.
- **Sensitive data boundary**: encrypted-at-rest identifiers are never returned unmasked to unauthorized roles; tokens/passwords never logged.

## 3) Domain API surfaces

## 3.1 Auth and admin

### `POST /auth/login`
- roles: public
- request: username, password
- response: `accessToken`, `tokenType`, `sessionId`, `expiresAt`, role set, user status
- failures:
  - `401 INVALID_CREDENTIALS`
  - `423 USER_DISABLED`

### `POST /auth/logout`
- roles: authenticated
- invalidates current session token (idempotent)

### `GET /auth/sessions`
- roles: `SYSTEM_ADMIN`
- lists active/revoked sessions with actor/user filters

### `POST /auth/sessions/{sessionId}/revoke`
- roles: `SYSTEM_ADMIN`
- explicit administrative session revocation

### `GET /admin/system-config`
### `PUT /admin/system-config`
- roles: `SYSTEM_ADMIN`
- includes municipality timezone, `sessionTtlMinutes`, search retention days, channel toggles
- validation:
  - timezone must be IANA zone id
  - `sessionTtlMinutes` in `60..1440`
  - retention positive bounded integer

### `GET /admin/audit-log`
- roles: `SYSTEM_ADMIN`, `AUDITOR`
- filters: actor, action, entityType, date range

## 3.2 Content/resource management

Resource types: `NEWS`, `POLICY`, `EVENT`, `CLASS`
Lifecycle states: `DRAFT`, `SCHEDULED`, `PUBLISHED`, `UNPUBLISHED`

Visibility contract:

- Public content APIs only return `PUBLISHED` records where `publishedAt <= now` (municipality timezone).
- `DRAFT`, `SCHEDULED` (before publish time), and `UNPUBLISHED` are never returned by public endpoints.
- Internal management APIs can view non-public states with role restrictions.

### `GET /public/content-items`
### `GET /public/content-items/{id}`
- roles: public (no authentication required)
- response scope: published catalog only (`NEWS`, `POLICY`, `EVENT`, `CLASS`)
- filters: type, category/tags, origin, date windows, pagination
- failures:
  - `404 CONTENT_NOT_PUBLIC` when id exists but is not publicly visible

### `POST /content-items`
### `GET /content-items/{id}`
### `GET /content-items`
### `PUT /content-items/{id}`
- roles:
  - create/update: `CONTENT_EDITOR`
  - read/list all states: `CONTENT_EDITOR`, `SYSTEM_ADMIN`, `AUDITOR`
- validation:
  - title required
  - rich text sanitized before persist (`sanitized_body`)
  - tags/category bounded count and length
  - `scheduledAt` must be valid in municipality timezone
  - internal list supports `state` filter for moderation/operations visibility

### `POST /content-items/{id}/publish`
### `POST /content-items/{id}/unpublish`
- roles: `CONTENT_EDITOR`
- writes immutable `content_publish_history`
- failures:
  - invalid state transitions -> `409 INVALID_STATE_TRANSITION`

### `GET /content-items/{id}/publish-history`
- roles: `CONTENT_EDITOR`, `SYSTEM_ADMIN`, `AUDITOR`

## 3.3 Comments and moderation

### `POST /content-items/{id}/comments`
- roles: authenticated
- supports threaded replies (`parentId`)
- target visibility rule: comments can be created only on publicly visible (`PUBLISHED` and currently published) content items
- pre-save filter behavior:
  - exact + case-insensitive sensitive-word matching
  - count hits -> set `filterHitCount`
  - if hits >= 2, default moderation state `HOLD_FOR_REVIEW`

### `GET /moderation/comments?state=...`
- roles: `MODERATOR`

### `POST /moderation/comments/{commentId}/actions`
- roles: `MODERATOR`
- actions: `APPROVE`, `REJECT`, `ESCALATE`
- required: action note/reason
- audit required

### `POST /moderation/sensitive-words`
### `PUT /moderation/sensitive-words/{id}`
### `DELETE /moderation/sensitive-words/{id}`
### `GET /moderation/sensitive-words`
- roles: `MODERATOR`

## 3.4 Billing center and A/R

### `POST /billing/fee-items`
### `PUT /billing/fee-items/{id}`
### `GET /billing/fee-items`
- roles: `BILLING_CLERK`
- calculation types: `FLAT`, `PER_UNIT`, `METERED`
- validation: rate >= 0

### `POST /billing/runs`
- roles: `BILLING_CLERK`
- idempotency required
- request: cycle date, cycle type (`MONTHLY|QUARTERLY`), optional scope filters
- behavior: generates A/R bills, line items, discounts, and derives due dates from active due-date policy snapshot

### `GET /billing/policies/due-date`
- roles: `SYSTEM_ADMIN`, `BILLING_CLERK`, `AUDITOR`
- returns per-cycle-type policy:
  - `monthlyDueInDays`
  - `quarterlyDueInDays`
  - `effectiveFrom`

### `PUT /billing/policies/due-date`
- roles: `SYSTEM_ADMIN`
- request:
  - `monthlyDueInDays` (1..60)
  - `quarterlyDueInDays` (1..60)
  - optional `effectiveFrom` (defaults now)
- behavior:
  - creates new policy version
  - future billing runs use policy active at run start
  - each run stores policy snapshot for traceability

### `GET /billing/runs/{runId}`
### `GET /billing/bills`
### `GET /billing/bills/{billId}`
- roles: `BILLING_CLERK`, `AUDITOR`

### `POST /billing/bills/{billId}/discounts`
- roles: `BILLING_CLERK`
- discount types: `PERCENTAGE`, `FIXED`
- validation: cannot reduce bill below `0.00`

### Late fee rule (job-driven, no direct endpoint required)
- eligible beginning local day 11 after due date
- 5% with $50 cap per bill

## 3.5 Dispatch and driver order-taking

### `POST /dispatch/orders`
### `GET /dispatch/orders`
- roles:
  - create/manual ops: `DISPATCHER`
  - list filtered for own assignments: `DRIVER`

### `POST /dispatch/orders/{orderId}/grab`
- roles: `DRIVER`
- constraints enforced:
  - max 3 miles distance
  - min rating 4.2
  - online at least 15 minutes today
- failures:
  - `409 ORDER_NOT_GRABBABLE`
  - `403 DRIVER_CONSTRAINT_FAILED`

### `POST /dispatch/orders/{orderId}/assign`
- roles: `DISPATCHER`
- assigns a driver in dispatcher mode; zone capacity enforced

### `POST /dispatch/orders/{orderId}/forced-assign`
- roles: `DISPATCHER`
- requires driver id and forced flag true
- if later rejected by driver, rejection reason enum required and same driver cannot be re-assigned to same order within 30 minutes

### `POST /dispatch/orders/{orderId}/driver-response`
- roles: `DRIVER`
- accepts/declines assigned order
- decline requires reason (for forced assignments reason enum mandatory)

### `PUT /dispatch/zones/{zoneId}/capacity-rule`
- roles: `DISPATCHER`
- sets per-zone concurrent capacity

## 3.6 Settlement and reconciliation

Methods: `CASH`, `CHECK`, `VOUCHER`, `OTHER`

### `POST /settlements/payments`
- roles: `BILLING_CLERK`
- idempotency required
- supports:
  - full-bill settlement
  - split settlement
  - even-split settlement
- validation:
  - payment total must match selected settlement target after discount allocation and deterministic cent reconciliation
  - cannot over-settle closed bill

### `POST /settlements/payments/{paymentId}/reverse`
- roles: `BILLING_CLERK`
- creates reversal linked to original posting
- updates bill balance/status accordingly

### `POST /settlements/shifts/{shiftId}/handover`
- roles: `BILLING_CLERK`, `SYSTEM_ADMIN`
- returns totals by method and compares against posted A/R deltas
- if `abs(paymentTotals - postedAR) > 1.00`, create discrepancy case

### `GET /settlements/discrepancies`
### `POST /settlements/discrepancies/{id}/resolve`
- roles:
  - list: `BILLING_CLERK`, `AUDITOR`, `SYSTEM_ADMIN`
  - resolve: `SYSTEM_ADMIN` (and optionally designated finance authority)

## 3.7 Notifications and outbox (offline-only)

### `POST /notifications/templates`
### `PUT /notifications/templates/{id}`
### `GET /notifications/templates`
- roles: `SYSTEM_ADMIN`

### `POST /notifications/messages`
### `GET /notifications/messages`
### `POST /notifications/messages/{id}/ack`
- roles:
  - create: system/internal modules and `SYSTEM_ADMIN`
  - read/ack: recipient user

### `POST /notifications/reminders`
### `GET /notifications/reminders`
- roles: `SYSTEM_ADMIN` and module service accounts
- includes retry counter semantics

### `GET /notifications/outbox`
### `POST /notifications/outbox/{id}/mark-exported`
- roles: `SYSTEM_ADMIN`
- outbox contains channel-formatted payload records only; no send action endpoint exists

## 3.8 Search and recommendations

### `GET /public/search`
- roles: public
- scope: published public content/resource catalog only
- params:
  - `q`
  - `recordType` (public-capable types only)
  - `category`
  - `origin`
  - `minPrice`, `maxPrice` (applicable records)
  - `sort` (`RELEVANCE|NEWEST|PRICE_ASC|PRICE_DESC`)
  - pagination

### `GET /public/search/typeahead`
- roles: public
- sources: published-catalog terms + aggregate recent-query signals (no per-user history)

### `GET /public/search/recommendations`
- roles: public
- deterministic non-personalized recommendations from local aggregate behavior (for example popularity/category momentum)

### `GET /search`
- roles: authenticated
- scope: includes published public catalog plus role-permitted searchable records
- params:
  - `q`
  - `recordType` (optional)
  - `category`
  - `origin`
  - `minPrice`, `maxPrice` (applicable records)
  - `sort` (`RELEVANCE|NEWEST|PRICE_ASC|PRICE_DESC`)
  - pagination

### `GET /search/typeahead`
- roles: authenticated
- sources: recent per-user query history + trigram suggestions

### `GET /search/recommendations`
- roles: authenticated
- deterministic personalized recommendations from local user activity/category affinity

### `GET /search/history`
### `DELETE /search/history`
- roles: authenticated
- retention default 90 days via scheduled cleanup
- note: anonymous/public search requests do not create per-user search history

## 3.9 Reporting and analytics

### `GET /reports/financial`
### `GET /reports/activity`
### `GET /reports/kpis`
### `GET /reports/anomalies`
- roles: `AUDITOR`, `SYSTEM_ADMIN` (read-only for `AUDITOR`)

## 4) Validation/failure model highlights

- Standard status usage:
  - `400` malformed/validation
  - `401` unauthenticated
  - `403` unauthorized
  - `404` missing resource
  - `409` state conflict/version/idempotency mismatch/concurrency
  - `422` semantically invalid business operation
- Business failure examples:
  - discount drives amount negative -> `422 BILL_BELOW_ZERO`
  - late fee over cap attempt -> `409 LATE_FEE_CAP_REACHED`
  - forced dispatch rejection without enum reason -> `422 REJECTION_REASON_REQUIRED`
  - outbox send attempt endpoint (not provided) -> impossible by API design

## 5) API-level audit expectations by domain

- Content publish/unpublish and schedule updates: always audited.
- Moderator queue actions and dictionary changes: always audited with actor and rationale.
- Billing runs, discount applications, payment postings/reversals: always audited with monetary before/after snapshots.
- Dispatcher forced assignments and driver rejections: always audited.
- System config and channel toggles: always audited.
- Report reads by Auditor/System Admin: access audits for sensitive finance views.

## 6) Non-functional enforcement at API layer

- p95 target support:
  - default pagination and max page size
  - request DTO validation before service entry
  - selective projection endpoints for large lists
- Security:
  - RBAC at controller + service method level
  - sensitive fields encrypted/masked in responses/logs per policy
- Offline integrity:
  - no endpoint performs external notification channel delivery
  - outbox export remains manual/offline operation
