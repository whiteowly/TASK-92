# CivicWorks Community Services

A single-node, fully offline Spring Boot + PostgreSQL modular monolith for municipal community services.

## What it does

- **Auth/Session**: Opaque bearer token authentication with server-side session state, BCrypt password hashing, RBAC (7 roles), session TTL/revocation
- **Content Management**: Public content/resource catalog (NEWS, POLICY, EVENT, CLASS) with publish/unpublish lifecycle, scheduled publishing, immutable publish history
- **Comments + Moderation**: Threaded comments with sensitive-word filtering, moderation queue (approve/reject/escalate)
- **Billing**: Fee items (FLAT/PER_UNIT/METERED), automatic billing cycle runs, due-date policy, late fees (5% / $50 cap), discounts
- **Dispatch**: Driver order assignment with eligibility constraints (distance/rating/online-time), zone capacity, forced dispatch with 30-min anti-repeat
- **Settlement**: Payment posting (full/split/even-split) with deterministic cent allocation, reversals, shift handover, discrepancy workflows
- **Notifications**: In-app messages, reminders with retry, templates; external channels (email/SMS/IM) are config + outbox only, **disabled by default, never send over network**
- **Search**: PostgreSQL full-text search + trigram typeahead, public vs authenticated search, per-user history (90-day retention), recommendations
- **Reporting**: Financial/activity reports, KPI snapshots, anomaly flags (arrears > 15% WoW)
- **Ops**: Daily encrypted backup to attached Docker volume, audit log for all privileged actions

## Prerequisites

- **Docker** (with Compose v2). That is sufficient for both the primary runtime and the broad test path — no host Java or Gradle is required.
- Host JDK 17+ is **only** needed if you want to invoke `./gradlew` directly. The project pins toolchain/daemon to Java 21.

## How to run

### Primary runtime command

```bash
docker compose up --build
```

A single command works on a cold machine — no `./init_db.sh` step, no `.env` file, no `export ...` is required. App + PostgreSQL come up; Flyway applies all migrations (V1 baseline + V2 Quartz tables) on first boot. To run detached add `-d`.

### Bootstrap & secret model

There are no `.env` files in the repo and no committed secret values in `docker-compose.yml`. The bootstrap is done automatically by `docker-entrypoint.sh` on first boot:

| Item | Where it lives | How it's produced | Override |
|---|---|---|---|
| App encryption key | `cwk-secrets` named volume, file `/run/cwk-secrets/encryption_key` (mode `0600`) | Generated on first boot from `/dev/urandom` (32 bytes, base64-url) and reused on every subsequent boot | Set `CIVICWORKS_ENCRYPTION_KEY` (env) or mount a key file and set `CIVICWORKS_ENCRYPTION_KEY_FILE=/path` |
| DB credentials | none required | PostgreSQL runs with `POSTGRES_HOST_AUTH_METHOD=trust` on the **internal compose network only** (no host port exposure) | To require a password, set `POSTGRES_PASSWORD` on `db` and `SPRING_DATASOURCE_PASSWORD` on `app` |
| Backup files | `pgbackups` named volume, mounted at `/backups` in both `db` and `app` | Written by the daily backup job (`pg_dump`) | — |

This is **not** a production secret-management path; it's the dev-only bootstrap.

### Host URL/port

- The app binds **only to the loopback** of the host: `127.0.0.1:18080` → container `:8080`. The default port `18080` was chosen to avoid the very common `:8080` collision.
- Override the host port:
  ```bash
  APP_HOST_PORT=19000 docker compose up --build -d
  ```
- **PostgreSQL is intentionally not exposed to the host.** It is reachable only from the app on the internal compose network. For ad-hoc DB debugging only:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.db-expose.yml up -d
  ```

### Health-check verification (deterministic)

The container's own healthcheck is the authoritative signal:

```bash
docker compose ps
# repo-app-1 should show STATUS = "Up X (healthy)"
```

Two repo-native, deterministic verification commands that always work, in this order of preference:

```bash
# 1) Container-local — runs inside the app container; works on any host.
docker exec repo-app-1 wget -qO- http://127.0.0.1:8080/actuator/health

# 2) Side-car on the compose network — works on any host with a Docker daemon.
docker run --rm --network repo_default curlimages/curl:latest \
  -sS http://app:8080/actuator/health
```

Both return `{"status":"UP","groups":["liveness","readiness"]}`.

The host-loopback URL is documented as a contract:

```bash
curl http://127.0.0.1:18080/actuator/health
```

This works on a normal review machine but is **not deterministic** across all hosts: VPN clients (Tailscale, WireGuard, corporate VPNs) and certain firewall/MTU configurations can intercept or drop traffic to the docker bridge. The compose network is built with a conservative MTU (1450) to mitigate the most common tunnel-MTU symptom, but if your host VPN intercepts the bridge subnet entirely the container-local and side-car checks above are the documented fallbacks. Do not interpret a host-side timeout as an app failure if `docker compose ps` reports `(healthy)` and one of the two commands above succeeds.

### Database reset / restore (key-aware)

You do **not** need to run `./init_db.sh` on first boot — `docker compose up --build` initializes the schema via Flyway. Use the script only to reset, restore, or purge:

```bash
./init_db.sh              # safe reset: drops only the pgdata volume
                          # PRESERVES cwk-secrets (encryption key) and pgbackups
./init_db.sh restore FILE # restore from a pg_dump backup file (key preserved)
./init_db.sh purge        # full wipe: removes pgdata, pgbackups AND cwk-secrets
                          # (interactive confirmation required; encrypted columns
                          #  in any prior backup become UNRECOVERABLE)
./init_db.sh status       # show current container + volume state
```

**Backup / restore + encryption-key implications.** Encrypted columns (e.g. `users.encrypted_resident_id`) are sealed with the per-installation key in `cwk-secrets/encryption_key`. The implications:

- A safe `reset` deletes the database but keeps the key, so a `pg_dump` backup taken under that key restores cleanly via `./init_db.sh restore`.
- A `purge` rotates the key. A backup taken before purge will **not** decrypt after purge — you must explicitly re-key (set `CIVICWORKS_ENCRYPTION_KEY` to the old value) before restoring, or the encrypted columns are lost.
- To inject a known key out-of-band (e.g. from a real secret store), set `CIVICWORKS_ENCRYPTION_KEY` in the environment before `docker compose up`; the entrypoint will not overwrite the volume file in that mode.

## How to test

### Broad test command (Dockerized)

```bash
./run_tests.sh
```

This is the single documented broad test command. It runs `gradle test` inside a containerized JDK 21 by building the runner image from `Dockerfile.test` (based on the official `gradle:8.10.2-jdk21-alpine` image, with the project's dependency graph baked into image layers so reruns are fast). The host only needs **Docker** — no Java or Gradle installation is required. The script exits non-zero on failure and always tears down its own test containers.

The current test suite is unit + standalone-MockMvc — no Testcontainers, no Docker-in-Docker.

### Targeted test commands (optional, host JDK 17+ required)

```bash
./gradlew test --tests "*ServiceTest"       # Domain unit tests
./gradlew test --tests "*PolicyTest"        # Policy/rule tests
./gradlew test --tests "*AllocationTest"    # Settlement allocation tests
./gradlew test --tests "*IT"                # MockMvc-based integration tests
```

## Module boundaries

| Module | Package | Purpose |
|---|---|---|
| Platform | `com.civicworks.platform` | Security, RBAC, audit, crypto, idempotency, clock, error handling, Quartz config |
| Content | `com.civicworks.content` | Content/resource CRUD, publish lifecycle, search indexing |
| Moderation | `com.civicworks.moderation` | Comments, sensitive-word filter, moderation actions |
| Billing | `com.civicworks.billing` | Fee items, billing runs, due-date policy, late fees, discounts, automatic cycle service |
| Dispatch | `com.civicworks.dispatch` | Orders, driver assignment, zone capacity, forced dispatch |
| Settlement | `com.civicworks.settlement` | Payments, allocations, reversals, shift handover, discrepancies |
| Notifications | `com.civicworks.notifications` | Templates, in-app messages, reminders, outbox |
| Search/Analytics | `com.civicworks.searchanalytics` | FTS, typeahead, recommendations, history, KPIs, anomalies, daily aggregation |
| Ops | `com.civicworks.ops` | Backup, health |

## Configuration

Key settings in `application.yml`:

| Setting | Default | Description |
|---|---|---|
| `civicworks.timezone` | `America/New_York` | Single municipality timezone — used by every Quartz cron trigger |
| `civicworks.session.ttl-minutes` | `480` | Session TTL (60-1440) |
| `civicworks.search.history-retention-days` | `90` | Search history retention |
| `civicworks.billing.monthly-cycle-day` | `1` | Day of month the automatic monthly cycle runs |
| `civicworks.billing.quarterly-cycle-day` | `1` | Day of quarter (Jan/Apr/Jul/Oct) the quarterly cycle runs |

System-wide config is also stored in the `system_config` table and editable via `PUT /api/v1/admin/system-config` (SYSTEM_ADMIN only). All runtime services read these values through `SystemConfigService` so admin edits take effect on the next operation without a restart. The `application.yml` / `@Value` defaults remain the fallback for the initial boot window before any admin edit lands, and for invalid values in `system_config`.

Runtime consumers and their refresh semantics:

| Setting | Consumer | Refresh |
|---|---|---|
| `timezone` | `MunicipalClock` (late-fee eligibility, today() reads, zoned reporting) | Read on every clock call; next consumer picks up the new zone |
| `sessionTtlMinutes` | `AuthService#login` | Read on every login; existing sessions keep their original TTL |
| `searchHistoryRetentionDays` | `SearchHistoryRetentionJob` | Read on every scheduled job run |
| `emailChannelEnabled` / `smsChannelEnabled` / `imChannelEnabled` | `NotificationService` external-channel routing | Read on every `createMessage` / `enqueueExternal` call |

Quartz **cron triggers** are bound to the boot-time timezone. Rotating the cron timezone itself (as distinct from runtime clock reads) requires a restart — this is a limitation of the persistent `LocalDataSourceJobStore`, not a dead config write.

## Offline constraints

- In-app notifications are active and fully local
- External notification channels (email, SMS, IM) are **outbox-only** and **disabled by default** (`emailChannelEnabled` / `smsChannelEnabled` / `imChannelEnabled` in `system_config`)
- When an external channel is enabled via the admin API, messages created against a template whose channel is EMAIL/SMS/IM (or direct `NotificationService#enqueueExternal` calls) write a row to `notification_outbox` — there is no network send path anywhere in the codebase
- When an external channel is disabled, external-channel sends are a silent no-op; the `in_app_messages` row (if any) is still created
- The `notification_outbox` table stores export-ready payloads for manual offline export

## API base path

All API endpoints use the base path `/api/v1`. Public endpoints are under `/api/v1/public/`.

## Default admin credentials

- Username: `admin`
- Password: `admin123`

**Change immediately in any non-development environment.**

## Scheduled jobs

All cron triggers fire in the configured municipality timezone (`civicworks.timezone`). Persisted in the `QRTZ_*` tables (Flyway V2) via `LocalDataSourceJobStore` + PostgreSQL JDBC delegate, `useProperties=true`. Restart-safe.

| Job name (QRTZ_JOB_DETAILS) | Cron (local time) | Description |
|---|---|---|
| `contentPublicationJob` | `0 */5 * * * ?` (every 5 min) | Publishes content with `state=SCHEDULED` whose `scheduledAt <= now` |
| `billingCycleRunJob` | `0 5 0 * * ?` (12:05 AM daily) | If today is the configured monthly/quarterly cycle date, executes the billing run |
| `lateFeeBillingJob` | `0 5 0 * * ?` (12:05 AM daily) | Applies late fees per policy (5%, $50 cap) to overdue bills past the 10-day grace period |
| `reminderDeliveryJob` | `0 */10 * * * ?` (every 10 min) | Delivers due in-app reminders, increments retry counter |
| `searchHistoryRetentionJob` | `0 0 2 * * ?` (2:00 AM daily) | Purges per-user search history past `searchHistoryRetentionDays` |
| `dailyBackupJob` | `0 0 3 * * ?` (3:00 AM daily) | `pg_dump` to the `pgbackups` volume; checksum + size recorded in `backup_runs` |
| `kpiAggregationJob` | `0 30 0 * * ?` (12:30 AM daily) | Computes KPI snapshots and emits `arrearsGrowthHigh` anomaly when WoW arrears growth > 15% |

Inspect the live state at any time:

```bash
docker compose exec db psql -U civicworks -d civicworks \
  -c "SELECT job_name, job_class_name FROM qrtz_job_details ORDER BY job_name;"

docker compose exec db psql -U civicworks -d civicworks \
  -c "SELECT t.trigger_name, c.cron_expression, c.time_zone_id \
      FROM qrtz_triggers t JOIN qrtz_cron_triggers c USING(sched_name, trigger_name, trigger_group) \
      ORDER BY t.trigger_name;"
```

## Security model

- Opaque bearer tokens (`cwk_sess_<base64url>`) with SHA-256 hash storage
- Server-side session state with fixed TTL, no refresh tokens
- BCrypt password hashing
- Deny-by-default RBAC at controller + service level
- Field-level AES-256-GCM encryption for sensitive identifiers
- Immutable audit log for all privileged actions
- Structured JSON logs (logback + LogstashEncoder), masking applied to token / password / encryption-key fields

### Resident-identifier protection

Resident identifiers are sensitive PII and are never stored in plaintext. Both `accounts` and `users` carry a two-column storage layout that is populated and consumed through an application service — never by direct column writes:

| Column | Purpose |
|---|---|
| `encrypted_resident_id` | AES-256-GCM ciphertext produced by `CryptoService.encrypt(...)` |
| `resident_id_hash` | SHA-256 hex produced by `CryptoService.hash(...)` — used for deterministic lookup/dedupe without re-exposing plaintext |

Services (`com.civicworks.billing.application.AccountService`, `com.civicworks.platform.security.UserResidentIdService`) normalize input (trim), encrypt + hash atomically, and return role-safe views. DTOs never include the ciphertext or the hash.

Role visibility rules:

| Role | Set | Get (full plaintext) | Get (masked form `****1234`) | Lookup by resident-id |
|---|---|---|---|---|
| SYSTEM_ADMIN | ✅ | ✅ | — | ✅ |
| AUDITOR | ❌ | ✅ | — | ✅ |
| BILLING_CLERK | ✅ on accounts / ❌ on users | ❌ | ✅ | ✅ on accounts / ❌ on users |
| Other roles | ❌ | ❌ | ❌ | ❌ |

Endpoints:

| Method | Path | Allowed roles | Purpose |
|---|---|---|---|
| POST | `/api/v1/billing/accounts/{id}/resident-id` | SYSTEM_ADMIN, BILLING_CLERK | Set account resident id |
| GET | `/api/v1/billing/accounts/{id}/resident-id` | SYSTEM_ADMIN, BILLING_CLERK, AUDITOR | Read account resident id (masked for BILLING_CLERK) |
| GET | `/api/v1/billing/accounts/by-resident-id?q=` | SYSTEM_ADMIN, BILLING_CLERK | Hash-based account lookup |
| POST | `/api/v1/users/{id}/resident-id` | SYSTEM_ADMIN | Set user resident id |
| GET | `/api/v1/users/{id}/resident-id` | SYSTEM_ADMIN, AUDITOR, BILLING_CLERK | Read user resident id (masked for BILLING_CLERK) |
| GET | `/api/v1/users/by-resident-id?q=` | SYSTEM_ADMIN, AUDITOR | Hash-based user lookup |

Audit rows reference only the entity id and action — plaintext resident identifiers are never logged.

## Roles

`SYSTEM_ADMIN`, `CONTENT_EDITOR`, `MODERATOR`, `BILLING_CLERK`, `DISPATCHER`, `DRIVER`, `AUDITOR`
