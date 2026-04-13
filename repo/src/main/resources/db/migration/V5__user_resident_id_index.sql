-- V5: index to support deterministic lookup of users by the SHA-256 hash of
-- their resident identifier. The plaintext is never persisted; only the
-- AES-256-GCM ciphertext (encrypted_resident_id) and the SHA-256 hex
-- (resident_id_hash) live in the row. See UserResidentIdService.
CREATE INDEX IF NOT EXISTS idx_users_resident_id_hash ON users(resident_id_hash);
