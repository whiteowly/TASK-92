package com.civicworks.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Query("SELECT s FROM AuthSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    List<AuthSession> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("SELECT s FROM AuthSession s WHERE (:userId IS NULL OR s.userId = :userId) ORDER BY s.issuedAt DESC")
    List<AuthSession> findAllFiltered(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE AuthSession s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.userId = :userId AND s.revokedAt IS NULL")
    void revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now, @Param("reason") String reason);
}
