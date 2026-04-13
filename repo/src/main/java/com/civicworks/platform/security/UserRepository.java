package com.civicworks.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);

    /**
     * Deterministic lookup by the SHA-256 hash of a resident identifier. The
     * plaintext is never persisted — callers pass {@code CryptoService.hash(id)}
     * so this query remains hash-only.
     */
    Optional<UserEntity> findByResidentIdHash(String residentIdHash);
}
