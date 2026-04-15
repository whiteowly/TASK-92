# CivicWorks Community Services

**Project type:** backend

A backend single-node, fully offline Spring Boot + PostgreSQL modular monolith for municipal community services. Covers auth/session, content management, comments + moderation, billing, dispatch, settlement, in-app notifications, search, reporting, and ops (backups, audit log).

## Architecture & Tech Stack

* **Backend:** Java 21, Spring Boot (modular monolith), Quartz scheduler (`LocalDataSourceJobStore`)
* **Database:** PostgreSQL 15 + Flyway migrations (V1 baseline + V2 Quartz tables)
* **Security:** Opaque bearer tokens (SHA-256 hashed), BCrypt passwords, RBAC (7 roles), AES-256-GCM field-level encryption
* **Search:** PostgreSQL full-text search + trigram typeahead
* **Logging:** Logback + LogstashEncoder (structured JSON, secret masking)
* **Containerization:** Docker & Docker Compose (Required)

## Project Structure

```text
.
├── src/                        # Spring Boot application source (modular monolith)
│   └── main/java/com/civicworks/
│       ├── platform/           # Security, RBAC, audit, crypto, idempotency, clock, Quartz config
│       ├── content/            # Content/resource CRUD, publish lifecycle, search indexing
│       ├── moderation/         # Comments, sensitive-word filter, moderation actions
│       ├── billing/            # Fee items, billing runs, due-date policy, late fees, discounts
│       ├── dispatch/           # Orders, driver assignment, zone capacity, forced dispatch
│       ├── settlement/         # Payments, allocations, reversals, shift handover, discrepancies
│       ├── notifications/      # Templates, in-app messages, reminders, outbox
│       ├── searchanalytics/    # FTS, typeahead, recommendations, history, KPIs, anomalies
│       └── ops/                # Backup, health
├── src/main/resources/db/migration/  # Flyway migrations
├── src/test/                   # Unit + SpringBootTest HTTP integration tests
├── docker-entrypoint.sh        # Bootstraps encryption key on first boot
├── init_db.sh                  # DB reset / restore / purge / status helper
├── docker-compose.yml          # Multi-container orchestration - MANDATORY
├── docker-compose.db-expose.yml # Optional overlay to expose Postgres for ad-hoc debugging
├── Dockerfile                  # App image
├── Dockerfile.test             # Containerized test runner (gradle:8.10.2-jdk21-alpine)
├── run_tests.sh                # Standardized test execution script - MANDATORY
└── README.md                   # Project documentation - MANDATORY
```

## Prerequisites

To ensure a consistent environment, this project is designed to run **entirely within containers**. The host needs only:
* [Docker](https://docs.docker.com/get-docker/)
* [Docker Compose](https://docs.docker.com/compose/install/) (v2)

No host Java, Gradle, Node, or other toolchain installs are required for either running the app or running the test suite — both paths execute inside the project's containers.

## Running the Application

1. **Build and Start Containers:**
   Use Docker Compose to build the images and spin up the entire stack in detached mode.
   ```bash
   docker-compose up
   ```

   Compose v2 equivalent used by this repository:
   ```bash
   docker compose up --build -d
   ```

   No `.env` file or `export ...` step is required. The bootstrap is done automatically by `docker-entrypoint.sh` on first boot:

   | Item | Where it lives | How it's produced | Override |
   |---|---|---|---|
   | App encryption key | `cwk-secrets` named volume, file `/run/cwk-secrets/encryption_key` (mode `0600`) | Generated on first boot from `/dev/urandom` (32 bytes, base64-url) and reused on every subsequent boot | Set `CIVICWORKS_ENCRYPTION_KEY` (env) or mount a key file and set `CIVICWORKS_ENCRYPTION_KEY_FILE=/path` |
   | DB credentials | none required | PostgreSQL runs with `POSTGRES_HOST_AUTH_METHOD=trust` on the **internal compose network only** (no host port exposure) | To require a password, set `POSTGRES_PASSWORD` on `db` and `SPRING_DATASOURCE_PASSWORD` on `app` |
   | Backup files | `pgbackups` named volume, mounted at `/backups` in both `db` and `app` | Written by the daily backup job (`pg_dump`) | — |

   This is **not** a production secret-management path; it's the dev-only bootstrap.

2. **Access the App:**
   * Backend API: `http://127.0.0.1:18080/api/v1`
   * Public endpoints: `http://127.0.0.1:18080/api/v1/public/`
   * Health check: `http://127.0.0.1:18080/actuator/health`

   The app binds **only to the loopback** of the host: `127.0.0.1:18080` → container `:8080`. The default port `18080` was chosen to avoid the very common `:8080` collision. Override with:
   ```bash
   APP_HOST_PORT=19000 docker compose up --build -d
   ```

   PostgreSQL is intentionally not exposed to the host. For ad-hoc DB debugging only:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.db-expose.yml up -d
   ```

3. **Verify Health (deterministic):**
   The container's own healthcheck is the authoritative signal:
   ```bash
   docker compose ps
   # repo-app-1 should show STATUS = "Up X (healthy)"
   ```

   Two repo-native, deterministic verification commands that always work:
   ```bash
   # 1) Container-local — runs inside the app container; works on any host.
   docker exec repo-app-1 wget -qO- http://127.0.0.1:8080/actuator/health

   # 2) Side-car on the compose network — works on any host with a Docker daemon.
   docker run --rm --network repo_default curlimages/curl:latest \
     -sS http://app:8080/actuator/health
   ```
   Both return `{"status":"UP","groups":["liveness","readiness"]}`.

   The host-loopback URL `curl http://127.0.0.1:18080/actuator/health` works on a normal review machine but is **not deterministic** across all hosts: VPN clients (Tailscale, WireGuard, corporate VPNs) and certain firewall/MTU configurations can intercept or drop traffic to the docker bridge. The compose network is built with a conservative MTU (1450) to mitigate the most common tunnel-MTU symptom; if your host VPN intercepts the bridge subnet entirely, use the container-local or side-car checks above.

4. **Stop the Application:**
   ```bash
   docker compose down
   ```

   To reset, restore, or purge the database (key-aware):
   ```bash
   ./init_db.sh              # safe reset: drops only the pgdata volume
                             # PRESERVES cwk-secrets (encryption key) and pgbackups
   ./init_db.sh restore FILE # restore from a pg_dump backup file (key preserved)
   ./init_db.sh purge        # full wipe: removes pgdata, pgbackups AND cwk-secrets
                             # (interactive confirmation required; encrypted columns
                             #  in any prior backup become UNRECOVERABLE)
   ./init_db.sh status       # show current container + volume state
   ```

## Testing

All unit and Spring Boot HTTP integration tests (`@SpringBootTest` + `TestRestTemplate`) are executed via a single, standardized shell script. It runs `gradle test` inside a containerized JDK 21 by building the runner image from `Dockerfile.test` (based on `gradle:8.10.2-jdk21-alpine`, with the project's dependency graph baked into image layers so reruns are fast). The host only needs **Docker** — no Java or Gradle installation is required.

Make sure the script is executable, then run it:

```bash
chmod +x run_tests.sh
./run_tests.sh
```

*Note: The `run_tests.sh` script outputs a standard exit code (`0` for success, non-zero for failure) and always tears down its own test containers, integrating smoothly with CI/CD validators.*

To run a focused subset, pass the gradle test filter through the same containerized runner — no host Java/Gradle is involved:

```bash
docker compose -f docker-compose.test.yml run --rm tests \
  gradle test --tests "*ServiceTest"       # Domain unit tests
docker compose -f docker-compose.test.yml run --rm tests \
  gradle test --tests "*PolicyTest"        # Policy/rule tests
docker compose -f docker-compose.test.yml run --rm tests \
  gradle test --tests "*AllocationTest"    # Settlement allocation tests
docker compose -f docker-compose.test.yml run --rm tests \
  gradle test --tests "*IT"                # SpringBootTest HTTP integration tests
```

## Seeded Credentials

Flyway pre-seeds one demo user per role on first boot (`V1__baseline.sql` for the admin row, `V6__demo_users_per_role.sql` for the rest). All demo users share the dev-only password `admin123` so the RBAC matrix can be exercised without manually creating accounts.

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **SYSTEM_ADMIN**   | `admin`      | `admin123` | Full access to all system modules — admin config, user management, audit log, discrepancy resolution. |
| **CONTENT_EDITOR** | `editor`     | `admin123` | Authors content (NEWS / POLICY / EVENT / CLASS), schedules publishing, manages drafts. |
| **MODERATOR**      | `moderator`  | `admin123` | Reviews comment moderation queue (approve / reject / escalate), manages sensitive-word list. |
| **BILLING_CLERK**  | `clerk`      | `admin123` | Posts payments, reverses payments, runs shift handover; can read masked resident IDs on accounts. |
| **DISPATCHER**     | `dispatcher` | `admin123` | Assigns drivers to orders, manages zone capacity, performs forced dispatch. |
| **DRIVER**         | `driver`     | `admin123` | Driver-side surface — receives assignments, marks online/offline. |
| **AUDITOR**        | `auditor`    | `admin123` | Read-only access to audit log, discrepancy cases, and full-plaintext resident IDs (no mutation). |

**Change every demo password immediately in any non-development environment.** The shared password is dev-only; the rows are seeded by a Flyway migration, so a `./init_db.sh purge` followed by `docker compose up` will recreate them as-is.

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

Quartz **cron triggers** are bound to the boot-time timezone. Rotating the cron timezone itself requires a restart — this is a limitation of the persistent `LocalDataSourceJobStore`, not a dead config write.

## Scheduled Jobs

All cron triggers fire in the configured municipality timezone (`civicworks.timezone`). Persisted in the `QRTZ_*` tables (Flyway V2) via `LocalDataSourceJobStore` + PostgreSQL JDBC delegate, `useProperties=true`. Restart-safe.

| Job name | Cron (local time) | Description |
|---|---|---|
| `contentPublicationJob` | `0 */5 * * * ?` (every 5 min) | Publishes content with `state=SCHEDULED` whose `scheduledAt <= now` |
| `billingCycleRunJob` | `0 5 0 * * ?` (12:05 AM daily) | If today is the configured monthly/quarterly cycle date, executes the billing run |
| `lateFeeBillingJob` | `0 5 0 * * ?` (12:05 AM daily) | Applies late fees per policy (5%, $50 cap) to overdue bills past the 10-day grace period |
| `reminderDeliveryJob` | `0 */10 * * * ?` (every 10 min) | Delivers due in-app reminders, increments retry counter |
| `searchHistoryRetentionJob` | `0 0 2 * * ?` (2:00 AM daily) | Purges per-user search history past `searchHistoryRetentionDays` |
| `dailyBackupJob` | `0 0 3 * * ?` (3:00 AM daily) | `pg_dump` to the `pgbackups` volume; checksum + size recorded in `backup_runs` |
| `kpiAggregationJob` | `0 30 0 * * ?` (12:30 AM daily) | Computes KPI snapshots and emits `arrearsGrowthHigh` anomaly when WoW arrears growth > 15% |

## Offline Constraints

- In-app notifications are active and fully local
- External notification channels (email, SMS, IM) are **outbox-only** and **disabled by default**
- When enabled via the admin API, external-channel messages write a row to `notification_outbox` — there is no network send path anywhere in the codebase
- The `notification_outbox` table stores export-ready payloads for manual offline export

## Security Model

- Opaque bearer tokens (`cwk_sess_<base64url>`) with SHA-256 hash storage
- Server-side session state with fixed TTL, no refresh tokens
- BCrypt password hashing
- Deny-by-default RBAC at controller + service level
- Field-level AES-256-GCM encryption for sensitive identifiers (e.g. `users.encrypted_resident_id`)
- Immutable audit log for all privileged actions
- Structured JSON logs with masking applied to token / password / encryption-key fields

Resident identifiers are never stored in plaintext. Each holder (`accounts`, `users`) carries `encrypted_resident_id` (AES-256-GCM ciphertext) and `resident_id_hash` (SHA-256 hex for deterministic lookup). DTOs never include the ciphertext or the hash; audit rows reference only the entity id and action.
