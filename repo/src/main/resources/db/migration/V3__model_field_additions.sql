-- V3: prompt-required core data model fields.

-- Account: minimum practical address fields.
ALTER TABLE accounts
    ADD COLUMN address_line1 VARCHAR(200),
    ADD COLUMN address_line2 VARCHAR(200),
    ADD COLUMN city          VARCHAR(120),
    ADD COLUMN state         VARCHAR(60),
    ADD COLUMN postal_code   VARCHAR(20);

-- Fee item: taxable flag (defaults false to preserve existing behavior).
ALTER TABLE fee_items
    ADD COLUMN taxable_flag BOOLEAN NOT NULL DEFAULT false;

-- Dispatch order: persisted rejection reason from the most recent rejection.
ALTER TABLE dispatch_orders
    ADD COLUMN rejection_reason VARCHAR(40);

-- Payment: shift_id FK so handover/shift reports can scope by shift directly
-- (in addition to the existing operator + window heuristic).
ALTER TABLE payments
    ADD COLUMN shift_id BIGINT REFERENCES cash_shifts(id);
CREATE INDEX idx_payments_shift ON payments(shift_id);

-- Per-account, per-fee usage source for PER_UNIT and METERED billing
-- calculation strategies. Latest record at-or-before the cycle date wins.
CREATE TABLE billing_usage (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT       NOT NULL REFERENCES accounts(id),
    fee_item_id  BIGINT       NOT NULL REFERENCES fee_items(id),
    cycle_date   DATE         NOT NULL,
    units        NUMERIC(12,3) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_billing_usage_lookup
    ON billing_usage(account_id, fee_item_id, cycle_date);
