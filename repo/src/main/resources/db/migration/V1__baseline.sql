-- CivicWorks Community Services - Baseline Schema
-- Extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- Platform / Security / Ops
-- ============================================================

CREATE TABLE system_config (
    id          SERIAL PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO system_config (config_key, config_value) VALUES
    ('timezone', 'America/New_York'),
    ('sessionTtlMinutes', '480'),
    ('searchHistoryRetentionDays', '90'),
    ('emailChannelEnabled', 'false'),
    ('smsChannelEnabled', 'false'),
    ('imChannelEnabled', 'false');

CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(100) NOT NULL UNIQUE,
    password_hash         VARCHAR(200) NOT NULL,
    display_name          VARCHAR(200),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    encrypted_resident_id TEXT,
    resident_id_hash      VARCHAR(64),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE user_roles (
    user_id BIGINT       NOT NULL REFERENCES users(id),
    role    VARCHAR(30)  NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE auth_sessions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id),
    token_hash     VARCHAR(64)  NOT NULL UNIQUE,
    issued_at      TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(100),
    last_seen_at   TIMESTAMPTZ
);

CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_token_hash ON auth_sessions(token_hash);

CREATE TABLE encryption_key_metadata (
    id          SERIAL PRIMARY KEY,
    key_version INTEGER NOT NULL UNIQUE,
    algorithm   VARCHAR(50) NOT NULL DEFAULT 'AES-256-GCM',
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO encryption_key_metadata (key_version, algorithm, active) VALUES (1, 'AES-256-GCM', true);

CREATE TABLE idempotency_keys (
    id               BIGSERIAL PRIMARY KEY,
    scope            VARCHAR(50)  NOT NULL,
    idem_key         VARCHAR(200) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_snapshot TEXT,
    response_status  INTEGER,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(scope, idem_key)
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,
    actor_role      VARCHAR(100),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(200),
    before_snapshot TEXT,
    after_snapshot  TEXT,
    request_id      VARCHAR(50),
    outcome         VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_id);

CREATE TABLE backup_runs (
    id          BIGSERIAL PRIMARY KEY,
    file_path   TEXT NOT NULL,
    file_size   BIGINT,
    checksum    VARCHAR(128),
    status      VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    started_at  TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_event (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    payload     TEXT,
    processed   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- Content + Moderation
-- ============================================================

CREATE TABLE content_items (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    body            TEXT,
    sanitized_body  TEXT,
    content_type    VARCHAR(30) NOT NULL,  -- NEWS, POLICY, EVENT, CLASS
    state           VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    origin          VARCHAR(200),
    price           NUMERIC(12,2),
    scheduled_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    unpublished_at  TIMESTAMPTZ,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_content_items_state ON content_items(state);
CREATE INDEX idx_content_items_type ON content_items(content_type);
CREATE INDEX idx_content_items_published ON content_items(state, published_at) WHERE state = 'PUBLISHED';

CREATE TABLE content_tag (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE content_item_tag (
    content_item_id BIGINT NOT NULL REFERENCES content_items(id),
    tag_id          BIGINT NOT NULL REFERENCES content_tag(id),
    PRIMARY KEY (content_item_id, tag_id)
);

CREATE TABLE content_publish_history (
    id              BIGSERIAL PRIMARY KEY,
    content_item_id BIGINT NOT NULL REFERENCES content_items(id),
    action          VARCHAR(30) NOT NULL, -- PUBLISH, UNPUBLISH, SCHEDULE
    actor_id        BIGINT REFERENCES users(id),
    previous_state  VARCHAR(30),
    new_state       VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_pub_history ON content_publish_history(content_item_id);

CREATE TABLE sensitive_words (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(200) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE comments (
    id              BIGSERIAL PRIMARY KEY,
    content_item_id BIGINT NOT NULL REFERENCES content_items(id),
    parent_id       BIGINT REFERENCES comments(id),
    author_id       BIGINT NOT NULL REFERENCES users(id),
    body            TEXT NOT NULL,
    filter_hit_count INTEGER NOT NULL DEFAULT 0,
    moderation_state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_content ON comments(content_item_id);
CREATE INDEX idx_comments_moderation ON comments(moderation_state);

CREATE TABLE moderation_actions (
    id          BIGSERIAL PRIMARY KEY,
    comment_id  BIGINT NOT NULL REFERENCES comments(id),
    action      VARCHAR(30) NOT NULL, -- APPROVE, REJECT, ESCALATE
    actor_id    BIGINT NOT NULL REFERENCES users(id),
    reason      TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- Billing
-- ============================================================

CREATE TABLE accounts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(200) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fee_items (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50) NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    calculation_type VARCHAR(20) NOT NULL, -- FLAT, PER_UNIT, METERED
    rate             NUMERIC(12,2) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing_cycles (
    id          BIGSERIAL PRIMARY KEY,
    cycle_type  VARCHAR(20) NOT NULL, -- MONTHLY, QUARTERLY
    cycle_date  DATE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing_due_date_policy (
    id              BIGSERIAL PRIMARY KEY,
    cycle_type      VARCHAR(20) NOT NULL,
    due_in_days     INTEGER NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(cycle_type, effective_from)
);

-- Default policies: 15 days for both monthly and quarterly
INSERT INTO billing_due_date_policy (cycle_type, due_in_days, effective_from) VALUES
    ('MONTHLY', 15, now()),
    ('QUARTERLY', 15, now());

CREATE TABLE billing_runs (
    id                  BIGSERIAL PRIMARY KEY,
    cycle_date          DATE NOT NULL,
    cycle_type          VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    policy_due_in_days  INTEGER NOT NULL,
    policy_effective_from TIMESTAMPTZ NOT NULL,
    bills_generated     INTEGER NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_billing_runs_unique ON billing_runs(cycle_date, cycle_type) WHERE status = 'COMPLETED';

CREATE TABLE bills (
    id              BIGSERIAL PRIMARY KEY,
    billing_run_id  BIGINT REFERENCES billing_runs(id),
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    cycle_date      DATE NOT NULL,
    cycle_type      VARCHAR(20) NOT NULL,
    due_date        DATE NOT NULL,
    policy_due_in_days INTEGER NOT NULL,
    original_amount NUMERIC(12,2) NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    late_fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    paid_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    balance         NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, PAID, OVERDUE, CLOSED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_bills_account ON bills(account_id);
CREATE INDEX idx_bills_due_date ON bills(due_date, status, balance);
CREATE INDEX idx_bills_status ON bills(status);

CREATE TABLE bill_line_items (
    id          BIGSERIAL PRIMARY KEY,
    bill_id     BIGINT NOT NULL REFERENCES bills(id),
    fee_item_id BIGINT NOT NULL REFERENCES fee_items(id),
    description VARCHAR(500),
    quantity    NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_rate   NUMERIC(12,2) NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    line_order  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE bill_discounts (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id),
    discount_type   VARCHAR(20) NOT NULL, -- PERCENTAGE, FIXED
    value           NUMERIC(12,2) NOT NULL,
    applied_amount  NUMERIC(12,2) NOT NULL,
    applied_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bill_late_fees (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id),
    fee_amount      NUMERIC(12,2) NOT NULL,
    eligible_date   DATE NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bill_late_fees ON bill_late_fees(bill_id);

-- ============================================================
-- Dispatch / Driver
-- ============================================================

CREATE TABLE zones (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    active  BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE zone_capacity_rules (
    id                      BIGSERIAL PRIMARY KEY,
    zone_id                 BIGINT NOT NULL REFERENCES zones(id) UNIQUE,
    max_concurrent_assignments INTEGER NOT NULL DEFAULT 10,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE drivers (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) UNIQUE,
    rating      NUMERIC(3,2) NOT NULL DEFAULT 5.00,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE driver_daily_presence (
    id              BIGSERIAL PRIMARY KEY,
    driver_id       BIGINT NOT NULL REFERENCES drivers(id),
    presence_date   DATE NOT NULL,
    minutes_online  INTEGER NOT NULL DEFAULT 0,
    UNIQUE(driver_id, presence_date)
);

CREATE TABLE dispatch_orders (
    id              BIGSERIAL PRIMARY KEY,
    zone_id         BIGINT REFERENCES zones(id),
    description     TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority        INTEGER NOT NULL DEFAULT 0,
    latitude        NUMERIC(9,6),
    longitude       NUMERIC(9,6),
    assigned_driver_id BIGINT REFERENCES drivers(id),
    forced          BOOLEAN NOT NULL DEFAULT false,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dispatch_orders_status ON dispatch_orders(status);
CREATE INDEX idx_dispatch_orders_zone ON dispatch_orders(zone_id, status);
CREATE INDEX idx_dispatch_orders_driver ON dispatch_orders(assigned_driver_id) WHERE assigned_driver_id IS NOT NULL;

CREATE TABLE dispatch_attempts (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES dispatch_orders(id),
    driver_id       BIGINT NOT NULL REFERENCES drivers(id),
    forced          BOOLEAN NOT NULL DEFAULT false,
    status          VARCHAR(30) NOT NULL, -- ASSIGNED, ACCEPTED, REJECTED, EXPIRED
    rejection_reason VARCHAR(100),
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispatch_attempts_order_driver ON dispatch_attempts(order_id, driver_id, attempted_at);

-- ============================================================
-- Settlement / Reconciliation
-- ============================================================

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id),
    amount          NUMERIC(12,2) NOT NULL,
    method          VARCHAR(20) NOT NULL, -- CASH, CHECK, VOUCHER, OTHER
    settlement_type VARCHAR(20) NOT NULL DEFAULT 'FULL', -- FULL, SPLIT, EVEN_SPLIT
    reference       VARCHAR(200),
    posted_by       BIGINT REFERENCES users(id),
    reversed        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_bill ON payments(bill_id);

CREATE TABLE payment_allocations (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      BIGINT NOT NULL REFERENCES payments(id),
    line_item_id    BIGINT REFERENCES bill_line_items(id),
    allocated_amount NUMERIC(12,2) NOT NULL,
    allocation_order INTEGER NOT NULL,
    residual_cents  INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_reversals (
    id                  BIGSERIAL PRIMARY KEY,
    original_payment_id BIGINT NOT NULL REFERENCES payments(id),
    reversal_amount     NUMERIC(12,2) NOT NULL,
    reason              TEXT,
    reversed_by         BIGINT REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cash_shifts (
    id          BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES users(id),
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE shift_handover_reports (
    id              BIGSERIAL PRIMARY KEY,
    shift_id        BIGINT NOT NULL REFERENCES cash_shifts(id),
    cash_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    check_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
    voucher_total   NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
    posted_ar_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    variance        NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE discrepancy_cases (
    id                  BIGSERIAL PRIMARY KEY,
    handover_report_id  BIGINT NOT NULL REFERENCES shift_handover_reports(id),
    variance_amount     NUMERIC(12,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, RESOLVED
    resolution_notes    TEXT,
    resolved_by         BIGINT REFERENCES users(id),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- Notifications
-- ============================================================

CREATE TABLE notification_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    subject     VARCHAR(500),
    body        TEXT NOT NULL,
    channel     VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE in_app_messages (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL REFERENCES users(id),
    template_id     BIGINT REFERENCES notification_templates(id),
    subject         VARCHAR(500),
    body            TEXT NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_in_app_messages_recipient ON in_app_messages(recipient_id, read_at);

CREATE TABLE task_reminders (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL REFERENCES users(id),
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(200),
    message         TEXT NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    sent            BOOLEAN NOT NULL DEFAULT false,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_reminders_scheduled ON task_reminders(scheduled_at, sent);

CREATE TABLE delivery_receipts (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES in_app_messages(id),
    status      VARCHAR(20) NOT NULL, -- DELIVERED, READ, ACKNOWLEDGED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_channel_config (
    id          BIGSERIAL PRIMARY KEY,
    channel     VARCHAR(20) NOT NULL UNIQUE, -- EMAIL, SMS, IM
    enabled     BOOLEAN NOT NULL DEFAULT false,
    config_json TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO notification_channel_config (channel, enabled) VALUES
    ('EMAIL', false),
    ('SMS', false),
    ('IM', false);

CREATE TABLE notification_outbox (
    id              BIGSERIAL PRIMARY KEY,
    channel         VARCHAR(20) NOT NULL,
    recipient_ref   VARCHAR(500) NOT NULL,
    subject         VARCHAR(500),
    body            TEXT NOT NULL,
    payload_json    TEXT,
    exported        BOOLEAN NOT NULL DEFAULT false,
    exported_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_outbox_pending ON notification_outbox(exported, channel);

-- ============================================================
-- Search / Analytics
-- ============================================================

CREATE TABLE search_documents (
    id          BIGSERIAL PRIMARY KEY,
    record_type VARCHAR(50) NOT NULL,
    record_id   BIGINT NOT NULL,
    title       VARCHAR(500) NOT NULL,
    body        TEXT,
    category    VARCHAR(200),
    origin      VARCHAR(200),
    price       NUMERIC(12,2),
    state       VARCHAR(30),
    published_at TIMESTAMPTZ,
    tsv         TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(body, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(category, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(origin, '')), 'D')
    ) STORED,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(record_type, record_id)
);

CREATE INDEX idx_search_documents_tsv ON search_documents USING GIN(tsv);
CREATE INDEX idx_search_documents_title_trgm ON search_documents USING GIN(title gin_trgm_ops);
CREATE INDEX idx_search_documents_state ON search_documents(state, published_at);

CREATE TABLE search_queries_recent (
    id          BIGSERIAL PRIMARY KEY,
    query_text  VARCHAR(500) NOT NULL,
    frequency   INTEGER NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_queries_text_trgm ON search_queries_recent USING GIN(query_text gin_trgm_ops);

CREATE TABLE search_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    query_text  VARCHAR(500) NOT NULL,
    result_count INTEGER,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_history_user ON search_history(user_id, created_at);

CREATE TABLE kpi_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    snapshot_date   DATE NOT NULL,
    metric_name     VARCHAR(200) NOT NULL,
    metric_value    NUMERIC(14,4) NOT NULL,
    metadata_json   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE anomaly_flags (
    id              BIGSERIAL PRIMARY KEY,
    flag_type       VARCHAR(100) NOT NULL,
    description     TEXT,
    metric_value    NUMERIC(14,4),
    threshold_value NUMERIC(14,4),
    acknowledged    BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- Seed data: default admin user (password: admin123)
-- BCrypt hash for 'admin123'
-- ============================================================
INSERT INTO users (username, password_hash, display_name, status) VALUES
    ('admin', '$2a$10$/oyvOgIJSYuXFmFJ.TubbOTTcg1ez1NOizVDtukYvYv44Dz5AbRCy', 'System Administrator', 'ACTIVE');

INSERT INTO user_roles (user_id, role) VALUES
    ((SELECT id FROM users WHERE username = 'admin'), 'SYSTEM_ADMIN');
