package com.civicworks.platform.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(CAST(:actorId AS long) IS NULL OR a.actorId = :actorId) AND " +
            "(CAST(:action AS string) IS NULL OR a.action = :action) AND " +
            "(CAST(:entityType AS string) IS NULL OR a.entityType = :entityType) AND " +
            "(CAST(:fromTs AS timestamp) IS NULL OR a.createdAt >= :fromTs) AND " +
            "(CAST(:toTs AS timestamp) IS NULL OR a.createdAt <= :toTs) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findFiltered(
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            Pageable pageable);
}
