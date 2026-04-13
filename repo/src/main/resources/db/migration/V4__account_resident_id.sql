-- V4: prompt-required resident identifier on accounts.
-- Plaintext resident id is never persisted. Storage mirrors users:
--   - encrypted_resident_id : AES-256-GCM ciphertext (Base64)
--   - resident_id_hash      : SHA-256 hex of the normalized id, for
--                             deterministic lookup/dedupe without exposing the
--                             plaintext.

ALTER TABLE accounts
    ADD COLUMN encrypted_resident_id TEXT,
    ADD COLUMN resident_id_hash      VARCHAR(64);

CREATE INDEX idx_accounts_resident_id_hash ON accounts(resident_id_hash);

-- Payment: business-meaningful "received_at" timestamp distinct from
-- created_at (the row insert time). Backfilled to created_at for existing
-- rows so reporting/handover semantics remain correct.
ALTER TABLE payments
    ADD COLUMN received_at TIMESTAMPTZ;

UPDATE payments SET received_at = created_at WHERE received_at IS NULL;
CREATE INDEX idx_payments_received_at ON payments(received_at);
