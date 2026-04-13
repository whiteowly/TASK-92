# CivicWorks Community Services — Implementation Design (Planning)

## 1) Scope and locked requirements

This planning package targets a **single-node, fully offline** municipal backend using:

- Spring Boot modular monolith
- PostgreSQL durable local storage
- JPA/Hibernate + Flyway migrations
- Docker-first runtime

Locked clarifications applied:

- One municipality-wide configured timezone drives all date/scheduling behavior.
- Search/recommendation must cover the public content/resource catalog at minimum, with shared extensibility.
- `origin` is local source/origin metadata on searchable records.
- Even-split settlement must reconcile exactly to cent-level by deterministic remainder-cent allocation after proportional discount allocation.
- External notification channels are config + outbox only, disabled by default, never network-sending.
- Late fees become eligible starting local day 11 after due date.
- Preserve all explicit original requirements (templates/retry/receipts, p95 target, etc.).

## 2) Architecture shape (modular monolith)

## 2.1 Module boundaries

Proposed package/module structure:

- `com.civicworks.platform`
  - security/authentication, RBAC, request context, error model, idempotency service, crypto service, audit infra, shared clock/timezone provider, structured logging/masking
- `com.civicworks.content`
  - public content/resource retrieval, internal CRUD, scheduling, publish/unpublish, immutable publish history
- `com.civicworks.moderation`
  - comments, sensitive-word dictionary/policy, moderation queue and actions
- `com.civicworks.billing`
  - fee items, billing cycles, bill generation, late fees, discounts, A/R status transitions
- `com.civicworks.dispatch`
  - zones/capacity, order creation/assignment, forced assignment constraints, driver acceptance constraints
- `com.civicworks.settlement`
  - payment posting, split/even split allocation, reversal/refund linkage, shift handover/discrepancy workflows
- `com.civicworks.notifications`
  - in-app messages, reminders, templates, retries, receipts, channel config, outbox export payloads
- `com.civicworks.searchanalytics`
  - PostgreSQL FTS, typeahead, filters/sort, public + authenticated recommendation surfaces, search history retention, KPI + anomaly flags
- `com.civicworks.ops`
  - backup orchestration hooks, health/readiness, operational reports

Each module has `api` (controllers/DTOs), `application` (use-cases), `domain` (entities/rules), `infra` (repositories/adapters).

## 2.2 Cross-module communication rules

- Synchronous in-process application services for transactional operations.
- Domain events persisted in `audit_log` + optional `system_event` table when asynchronous follow-up jobs are needed.
- No module may bypass `platform` security/idempotency/audit hooks.

## 3) Domain model and persistence plan

## 3.1 Core entities from prompt (implemented as first-class tables)

- `users`
- `content_items`
- `comments`
- `fee_items`
- `accounts`
- `bills`
- `payments`
- `dispatch_orders`
- `audit_log`

## 3.2 Additional required tables (beyond prompt list)

### Platform/security/ops

- `system_config` (single active timezone, auth/session security settings, global toggles)
- `idempotency_keys` (operation scope, key, request hash, response snapshot, status)
- `auth_sessions` (mandatory server-side session store for opaque bearer tokens)
- `encryption_key_metadata` (active key id/version metadata; key material itself external to DB)
- `backup_runs` (daily backup metadata, checksums, restore-check status)
- `system_event` (optional event queue for deferred local processing)

### Content + moderation

- `content_publish_history` (immutable state transitions with actor, timestamp)
- `content_tag` and `content_item_tag` (normalized tags; optional array mirror for read model)
- `sensitive_words` (dictionary with active flag)
- `moderation_actions` (approve/reject/escalate actions + rationale)

### Billing

- `billing_cycles` (monthly/quarterly cycle definitions)
- `billing_due_date_policy` (per-cycle-type due-in-days policy with effective timestamp)
- `billing_runs` (each scheduled/manual run attempt + status)
- `bill_line_items` (fee composition for traceability)
- `bill_discounts` (applied discounts and allocation metadata)
- `bill_late_fees` (late fee posting records, cap tracking)

### Dispatch/driver

- `zones` (zone metadata)
- `zone_capacity_rules` (max concurrent assignments per zone)
- `drivers` (driver profile, rating)
- `driver_daily_presence` (minutes online per local day)
- `dispatch_attempts` (assignment history, forced flag, rejection reason, timestamps)

### Settlement/reconciliation

- `payment_allocations` (split/even-split lines, deterministic cent allocation order)
- `payment_reversals` (links to original payment/allocation)
- `cash_shifts` (shift boundaries and operator)
- `shift_handover_reports` (method totals, posted A/R totals, variance)
- `discrepancy_cases` (variance > $1.00 workflow status and notes)

### Notifications/search/analytics

- `notification_templates`
- `in_app_messages`
- `task_reminders`
- `delivery_receipts` (read/ack states)
- `notification_channel_config` (email/sms/im toggles; default disabled)
- `notification_outbox` (export-only payloads; never network dispatched)
- `search_documents` (shared searchable projection; record type + origin)
- `search_queries_recent` (for typeahead)
- `search_history` (per-user retention default 90 days)
- `kpi_snapshots`
- `anomaly_flags` (including arrears >15% WoW)

## 3.3 Constraints/indexing essentials

- Unique:
  - `users.username`
  - `fee_items.code`
  - `idempotency_keys(scope, key)`
  - `auth_sessions.token_hash`
  - `billing_due_date_policy(cycle_type, effective_from)`
  - `billing_runs(cycle_date, cycle_type)` (when finalized)
- FK + check constraints for enum/state fields.
- Money fields: `NUMERIC(12,2)` (or wider where needed), never floating-point.
- Search:
  - `search_documents.tsv` generated column (`to_tsvector`) + GIN index
  - trigram indexes (`pg_trgm`) for typeahead text columns
- Dispatch concurrency support:
  - partial indexes on active assignments by zone and driver
  - index on `dispatch_attempts(order_id, driver_id, attempted_at)` for 30-minute anti-repeat checks
- Billing/late-fee scheduling:
  - indexes on `bills(due_date, status, balance)` and `bill_late_fees(bill_id)`

## 4) Transaction and correctness design

## 4.1 Idempotency policy

Mandatory idempotency on:

- payment posting API
- billing-run generation API

Behavior:

1. Require `Idempotency-Key` header.
2. Compute canonical request hash.
3. If `(scope, key)` absent: reserve key row in `PENDING`, execute transaction, persist response snapshot, mark `COMPLETED`.
4. If present and hash matches completed row: return prior response (same status/body).
5. If present and hash differs: `409 CONFLICT` (`IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`).

## 4.2 Monetary arithmetic rules

- `BigDecimal`, explicit `RoundingMode.HALF_UP` to cent precision.
- Discount floor: bill amount cannot go below `0.00`.
- Late fee: `min(5% of eligible base, remaining cap to $50)`.
- Even-split algorithm:
  - compute proportional-discount-adjusted target total first
  - calculate nominal even parts
  - round each to cents
  - deterministically allocate residual cents by fixed allocation order (line sequence ascending)
  - persist allocation order and residual handling metadata for reversible auditing

## 4.3 Concurrency controls

- Use optimistic versioning (`@Version`) on mutable aggregates (`content_items`, `bills`, `dispatch_orders`).
- Use `SELECT ... FOR UPDATE` or serialized transaction segment for:
  - zone capacity assignment checks
  - forced dispatch anti-repeat validation
  - payment/reversal posting against same bill

## 4.4 Bill due-date policy and derivation

Concrete policy to avoid implementation drift:

- Due-date policy is **per billing cycle type** (`MONTHLY`, `QUARTERLY`) using `dueInDays` integer values.
- Default initial policy (seeded by migration):
  - `MONTHLY.dueInDays = 15`
  - `QUARTERLY.dueInDays = 15`
- Allowed range: `1..60` days.

Configuration and ownership:

- `SYSTEM_ADMIN` can update due-date policy.
- `BILLING_CLERK` can view but cannot mutate due-date policy.
- Policy updates are audited and versioned by `effective_from` timestamp.

Billing-run derivation rule:

1. Billing run starts for `(cycleDate, cycleType)`.
2. Resolve active policy row for `cycleType` at run start time.
3. Compute `bill.due_date = cycleDate + dueInDays` (municipality timezone local-date math).
4. Persist policy snapshot (`dueInDays`, `policyVersion/effectiveFrom`) on `billing_runs` and each generated `bills` row for traceability.
5. Late-fee eligibility evaluates from local day 11 after the stored `due_date`.

## 4.5 Public-content visibility and access separation

Content lifecycle states used for visibility:

- `DRAFT`, `SCHEDULED`, `PUBLISHED`, `UNPUBLISHED`

Visibility rules:

- **Public content APIs** expose only records in `PUBLISHED` state with `published_at <= now` in municipality timezone.
- `DRAFT`, pre-release `SCHEDULED`, and `UNPUBLISHED` records are never exposed publicly.
- **Internal management APIs** can read/manage non-public states based on role permissions.

Role boundary summary:

- Public (anonymous): published catalog read/search only.
- `CONTENT_EDITOR`: create/update/schedule/publish/unpublish and internal state-aware read.
- `SYSTEM_ADMIN`, `AUDITOR`: internal read of all states (auditor read-only).

Data/query enforcement expectations:

- Public retrieval/search must always include explicit published-state predicates.
- Internal endpoints must not accidentally default to public-only scope.
- Unauthorized access to non-public content via public endpoints returns not-found style response to avoid state leakage.

## 5) Scheduler and jobs plan

Use Spring Boot + Quartz JDBC store (PostgreSQL) for restart-safe persisted jobs and trigger history.

Planned job families:

1. `content-publication-job` (scheduled publish/unpublish in municipality timezone)
2. `billing-cycle-run-job` (12:05 AM local on cycle date)
3. `late-fee-eligibility-job` (12:05 AM local; applies from local day 11 after due date)
4. `notification-reminder-job` (scheduled reminders + retry increments)
5. `search-history-retention-job` (purge beyond retention, default 90 days)
6. `daily-backup-job` (local pg dump to attached volume + checksum)
7. `kpi-anomaly-job` (daily/weekly KPI rollup and arrears anomaly checks)

Scheduling invariants:

- All cron evaluation uses configured municipality timezone from `system_config`.
- All jobs must be idempotent and replay-safe.
- Job execution writes structured audit/system events.

## 6) Security, encryption, masking, audit

## 6.1 Authentication and RBAC

Chosen auth/session model: **opaque bearer token with server-side session state**.

Token/session format and storage:

- Login issues a cryptographically random 256-bit token, returned as bearer value (format prefix: `cwk_sess_` + base64url payload).
- Only `SHA-256(token)` is stored in `auth_sessions` (never plaintext token).
- `auth_sessions` fields include: `session_id`, `user_id`, `token_hash`, `issued_at`, `expires_at`, `revoked_at`, `revoked_reason`, `last_seen_at`.

Login/logout semantics:

- `POST /auth/login` validates BCrypt password hash and active user status, then creates a new session row and returns token + expiry metadata.
- `POST /auth/logout` revokes the current session (`revoked_at` set). Endpoint is idempotent.
- Multiple sessions per user are allowed (different local devices); each is independently revocable.

Expiry/revocation behavior:

- Session TTL is fixed-duration from issue time, configurable in `system_config` as `sessionTtlMinutes` (default `480`, allowed `60..1440`).
- No refresh-token mechanism in baseline; expired sessions require re-login.
- Immediate revocation triggers:
  - explicit logout
  - user status changed to disabled/locked
  - admin-initiated session revoke

Clock/skew expectations:

- Expiry checks use **server clock only** (single-node local deployment), so client clock skew does not affect acceptance.
- Token validity is evaluated at request authentication time; no mid-request expiry interrupt.

Why this fits product/security constraints:

- Fully offline-friendly (no external identity dependency).
- Strong revocation semantics without JWT blacklist complexity.
- Server-side control aligns with single-node deployment and role-sensitive municipal operations.

RBAC enforcement model:

- Role set from prompt enforced at both HTTP route layer and service-method layer.
- Deny-by-default authorization policy.

## 6.2 Field-level encryption

- Sensitive resident identifier fields encrypted at rest via application-layer crypto adapter + key version metadata.
- Decryption only in service paths requiring plaintext.
- Queryable surrogates (hashed/tokenized lookup field) used where exact-match lookup is needed.

## 6.3 Log masking and structured logs

- JSON structured logs with request-id, actor-id, role, action, entity-ref, latency, outcome.
- Masking policy for resident identifiers, payment instrument references, auth artifacts.
- No plaintext sensitive fields in logs.

## 6.4 Audit model

- Immutable `audit_log` entries for all privileged or state-changing actions:
  - content publish/unpublish
  - moderator actions
  - billing runs, postings, reversals
  - dispatch forced assignments/rejections
  - settlement reversals/discrepancy decisions
  - admin config/security changes

## 6.5 Static security-boundary inventory

Auth entry points:

- Public: `POST /auth/login`
- Authenticated: `POST /auth/logout`
- Admin session control: `GET /auth/sessions`, `POST /auth/sessions/{sessionId}/revoke`
- Public content/search read: `GET /public/content-items*`, `GET /public/search*`

Controller/service enforcement boundaries:

- Controllers enforce coarse RBAC by endpoint family.
- Services enforce business/object-level authorization and invariant checks before state mutation.
- No repository direct-write from controllers.

Object-level authorization expectations:

- Drivers can only read/respond to dispatch orders they are assigned to or eligible to grab.
- Billing clerks can mutate billing/settlement data but cannot change global security settings.
- Moderators can mutate moderation state/dictionary only; cannot publish content or post settlements.
- Auditors are read-only for finance/activity/report surfaces.

Admin/internal-only surfaces:

- `SYSTEM_ADMIN` only: system config, due-date policy mutation, user status controls, session revocation, channel toggles, discrepancy resolution (final authority).
- Internal module-only writes: notification outbox generation and scheduled-job bookkeeping.

Sensitive-data/logging boundaries:

- Sensitive resident identifiers stored encrypted; decrypted only in authorized service paths.
- Logs and audit payloads store masked forms of sensitive fields.
- Authentication artifacts (tokens/passwords) never logged.

## 7) Offline notifications and outbox rules

- In-app messages and reminders are first-class local records.
- External channel configs (`email`, `sms`, `im`) are default-disabled.
- Channel execution path only writes to `notification_outbox`; never sends network traffic.
- Delivery receipts represent in-app read/ack states.
- Retry counters apply to local delivery workflow (record state transitions), not outbound networking.

## 8) Search/recommendation design

- Shared search projection (`search_documents`) with minimum mandatory participation from public content/resources.
- Required filters: category/tags, price range (when priced), origin metadata.
- Sort: relevance, newest, price (where applicable).
- Public search surface (`/public/search*`) is published-catalog-only.
- Authenticated search surface (`/search*`) includes published catalog plus role-permitted searchable records.
- Typeahead:
  - public: aggregate published-catalog terms (no per-user history)
  - authenticated: per-user recent queries + trigram support
- Recommendations:
  - public: deterministic non-personalized local aggregates
  - authenticated: deterministic per-user local activity/category affinity
- Per-user search history retention defaults to 90 days (configurable in `system_config`) and applies only to authenticated users.

## 9) Backup and restore plan

- Daily backup job writes encrypted/compressed PostgreSQL dumps to attached Docker volume.
- Backup metadata in `backup_runs` with checksum and size.
- Provide restore procedure via `init_db.sh` subcommand path (planning target) and periodic restore verification marker.
- Backup mechanism stays offline/local (no cloud/network dependency).

## 10) Observability and performance plan

- Structured application logs + request timing metrics.
- Endpoint-level latency and error-rate metrics; persistence/query timing.
- KPI/report tables stored locally (`kpi_snapshots`, `anomaly_flags`).
- Performance target: p95 <300 ms typical queries @ 50 concurrent users.

Performance tactics:

- pagination defaults + caps
- narrow DTO projections on list APIs
- proper indexes for hot predicates
- avoid N+1 via fetch planning
- bounded transaction scopes
- background jobs staggered to reduce contention during peak API hours

## 11) Runtime and scaffold plan

Planned repo runtime contract:

- `docker compose up --build` (primary)
- PostgreSQL service with attached persistent volumes (data + backups)
- Flyway auto-migrations on app startup
- `./init_db.sh` as the only project-standard DB init/bootstrap/reset path
- `./run_tests.sh` as broad owner-run test command

Scaffold milestones:

1. Spring Boot skeleton + module package layout + dependency baseline
2. Docker Compose (app + postgres + volume mounts)
3. Flyway baseline migration + extensions (`pg_trgm`, optionally `pgcrypto` if DB-side helpers used)
4. Security baseline (auth + RBAC + audit hook)
5. Domain module slice-by-slice implementation

## 11.1 README/static review checklist (must be present once implementation starts)

`README.md` must explicitly disclose and trace:

- primary runtime command: `docker compose up --build`
- broad test command: `./run_tests.sh`
- DB init/bootstrap path: `./init_db.sh`
- Docker/bootstrap/no-`.env` expectations (no checked-in env files, no manual export requirement)
- module boundaries and main entry points (security config, API package roots, migration path, scheduler/job wiring)
- configuration shape (timezone, session TTL, billing due-date policy, notification channel defaults)
- offline constraints: in-app notifications active, external channels outbox-only and disabled by default, no network sending

## 12) Authoritative references used for planning choices

- Spring Boot Quartz integration (`spring.quartz.job-store-type`, JDBC schema cautions):
  - https://docs.spring.io/spring-boot/reference/io/quartz.html
- Spring Framework scheduling and cron timezone support:
  - https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Security method and request authorization, method-security enablement:
  - https://docs.spring.io/spring-security/reference/6.5/
- Spring Security password storage (`BCryptPasswordEncoder`, `DelegatingPasswordEncoder`):
  - https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html
- Spring Boot + Flyway startup migrations:
  - https://docs.spring.io/spring-boot/reference/howto/data-initialization.html
- PostgreSQL full-text search and generated `tsvector` indexing:
  - https://www.postgresql.org/docs/16/textsearch-tables.html
- PostgreSQL trigram indexing for typeahead/similarity:
  - https://www.postgresql.org/docs/16/pgtrgm.html
- PostgreSQL column encryption considerations (`pgcrypto` and cautions):
  - https://www.postgresql.org/docs/16/pgcrypto.html
  - https://www.postgresql.org/docs/16/encryption-options.html
